package xyz.stdiodh.gojjibom.media

import java.time.OffsetDateTime

interface ObjectStorage {
    fun presignUpload(
        objectKey: String,
        contentType: String,
        sizeBytes: Long,
        ttlSeconds: Long,
    ): PresignedObjectUrl

    fun presignView(
        objectKey: String,
        ttlSeconds: Long,
    ): PresignedObjectUrl

    fun putObject(
        objectKey: String,
        contentType: String,
        bytes: ByteArray,
    )
}

data class PresignedObjectUrl(
    val url: String,
    val expiresAt: OffsetDateTime,
    val requiredHeaders: Map<String, String>,
)
