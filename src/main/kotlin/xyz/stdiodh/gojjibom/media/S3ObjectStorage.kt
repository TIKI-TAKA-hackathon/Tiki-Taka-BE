package xyz.stdiodh.gojjibom.media

import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.ServerSideEncryption
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class S3ObjectStorage(
    private val properties: AwsStorageProperties,
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
) : ObjectStorage {
    override fun presignUpload(
        objectKey: String,
        contentType: String,
        sizeBytes: Long,
        ttlSeconds: Long,
    ): PresignedObjectUrl {
        val putObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build()
        val presignRequest =
            PutObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofSeconds(ttlSeconds))
                .putObjectRequest(putObjectRequest)
                .build()
        val presigned = s3Presigner.presignPutObject(presignRequest)

        return PresignedObjectUrl(
            url = presigned.url().toString(),
            expiresAt = expiresAt(ttlSeconds),
            requiredHeaders = presigned.signedHeaders().mapValues { (_, values) -> values.joinToString(",") },
        )
    }

    override fun presignView(
        objectKey: String,
        ttlSeconds: Long,
    ): PresignedObjectUrl {
        val getObjectRequest =
            GetObjectRequest
                .builder()
                .bucket(bucket())
                .key(objectKey)
                .build()
        val presignRequest =
            GetObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofSeconds(ttlSeconds))
                .getObjectRequest(getObjectRequest)
                .build()
        val presigned = s3Presigner.presignGetObject(presignRequest)

        return PresignedObjectUrl(
            url = presigned.url().toString(),
            expiresAt = expiresAt(ttlSeconds),
            requiredHeaders = emptyMap(),
        )
    }

    override fun putObject(
        objectKey: String,
        contentType: String,
        bytes: ByteArray,
    ) {
        val request =
            PutObjectRequest
                .builder()
                .bucket(bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(bytes.size.toLong())
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build()

        s3Client.putObject(request, RequestBody.fromBytes(bytes))
    }

    private fun bucket(): String =
        properties.s3.bucket.takeIf { it.isNotBlank() }
            ?: throw MediaErrors.serviceUnavailable(
                "MEDIA_BUCKET_NOT_CONFIGURED",
                "Object storage bucket is not configured",
            )

    private fun expiresAt(ttlSeconds: Long): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(ttlSeconds)
}
