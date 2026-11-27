package xyz.stdiodh.gojjibom.media.infra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.ServerSideEncryption
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import xyz.stdiodh.gojjibom.global.web.ApiException
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class S3ObjectStorageTest {
    private val s3Client = Mockito.mock(S3Client::class.java)
    private val s3Presigner = Mockito.mock(S3Presigner::class.java)
    private val clock =
        Clock.fixed(
            Instant.parse("2026-07-30T01:02:03Z"),
            ZoneId.of("Asia/Seoul"),
        )
    private val properties = AwsStorageProperties(s3 = AwsS3Properties(bucket = "test-bucket"))
    private val objectStorage = S3ObjectStorage(properties, s3Client, s3Presigner, clock)

    @Test
    fun `presignUpload sends the complete S3 request and returns an exact UTC expiry`() {
        val presignedRequest = Mockito.mock(PresignedPutObjectRequest::class.java)
        Mockito
            .`when`(presignedRequest.url())
            .thenReturn(URI("https://test-bucket.s3.example/upload-key").toURL())
        Mockito
            .`when`(presignedRequest.signedHeaders())
            .thenReturn(
                mapOf(
                    "content-type" to listOf("image/jpeg"),
                    "x-amz-server-side-encryption" to listOf("AES256"),
                ),
            )
        Mockito
            .`when`(s3Presigner.presignPutObject(Mockito.any(PutObjectPresignRequest::class.java)))
            .thenReturn(presignedRequest)

        val result =
            objectStorage.presignUpload(
                objectKey = "care-groups/10/upload-key",
                contentType = "image/jpeg",
                sizeBytes = 1_024,
                ttlSeconds = 300,
            )

        val captor = ArgumentCaptor.forClass(PutObjectPresignRequest::class.java)
        Mockito.verify(s3Presigner).presignPutObject(captor.capture())
        val request = captor.value
        val putObjectRequest = request.putObjectRequest()
        assertThat(request.signatureDuration()).isEqualTo(Duration.ofSeconds(300))
        assertThat(putObjectRequest.bucket()).isEqualTo("test-bucket")
        assertThat(putObjectRequest.key()).isEqualTo("care-groups/10/upload-key")
        assertThat(putObjectRequest.contentType()).isEqualTo("image/jpeg")
        assertThat(putObjectRequest.contentLength()).isEqualTo(1_024)
        assertThat(putObjectRequest.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256)
        assertThat(result.url).isEqualTo("https://test-bucket.s3.example/upload-key")
        assertThat(result.expiresAt).isEqualTo(OffsetDateTime.parse("2026-07-30T01:07:03Z"))
        assertThat(result.requiredHeaders)
            .containsEntry("content-type", "image/jpeg")
            .containsEntry("x-amz-server-side-encryption", "AES256")
    }

    @Test
    fun `presignView sends the bucket key and TTL and returns an exact UTC expiry`() {
        val presignedRequest = Mockito.mock(PresignedGetObjectRequest::class.java)
        Mockito
            .`when`(presignedRequest.url())
            .thenReturn(URI("https://test-bucket.s3.example/view-key").toURL())
        Mockito
            .`when`(s3Presigner.presignGetObject(Mockito.any(GetObjectPresignRequest::class.java)))
            .thenReturn(presignedRequest)

        val result =
            objectStorage.presignView(
                objectKey = "care-groups/10/view-key",
                ttlSeconds = 120,
            )

        val captor = ArgumentCaptor.forClass(GetObjectPresignRequest::class.java)
        Mockito.verify(s3Presigner).presignGetObject(captor.capture())
        val request = captor.value
        val getObjectRequest = request.getObjectRequest()
        assertThat(request.signatureDuration()).isEqualTo(Duration.ofSeconds(120))
        assertThat(getObjectRequest.bucket()).isEqualTo("test-bucket")
        assertThat(getObjectRequest.key()).isEqualTo("care-groups/10/view-key")
        assertThat(result.url).isEqualTo("https://test-bucket.s3.example/view-key")
        assertThat(result.expiresAt).isEqualTo(OffsetDateTime.parse("2026-07-30T01:04:03Z"))
        assertThat(result.requiredHeaders).isEmpty()
    }

    @Test
    fun `putObject sends content metadata encryption and bytes`() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        objectStorage.putObject(
            objectKey = "care-groups/10/image-key",
            contentType = "image/webp",
            bytes = bytes,
        )

        val requestCaptor = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        val bodyCaptor = ArgumentCaptor.forClass(RequestBody::class.java)
        Mockito.verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture())
        val request = requestCaptor.value
        assertThat(request.bucket()).isEqualTo("test-bucket")
        assertThat(request.key()).isEqualTo("care-groups/10/image-key")
        assertThat(request.contentType()).isEqualTo("image/webp")
        assertThat(request.contentLength()).isEqualTo(bytes.size.toLong())
        assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256)
        assertThat(bodyCaptor.value.optionalContentLength()).contains(bytes.size.toLong())
        val uploadedBytes =
            bodyCaptor.value
                .contentStreamProvider()
                .newStream()
                .use { it.readAllBytes() }
        assertThat(uploadedBytes).isEqualTo(bytes)
    }

    @Test
    fun `empty bucket fails before calling AWS`() {
        val storage =
            S3ObjectStorage(
                AwsStorageProperties(s3 = AwsS3Properties(bucket = "")),
                s3Client,
                s3Presigner,
                clock,
            )

        val exception =
            assertThrows<ApiException> {
                storage.presignView("care-groups/10/view-key", ttlSeconds = 120)
            }

        assertThat(exception.status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(exception.code).isEqualTo("MEDIA_BUCKET_NOT_CONFIGURED")
        assertThat(exception.message).isEqualTo("Object storage bucket is not configured")
        Mockito.verifyNoInteractions(s3Client, s3Presigner)
    }
}
