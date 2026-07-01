package xyz.stdiodh.gojjibom.media

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalTime
import java.time.OffsetDateTime

data class ImageUploadUrlRequest(
    @field:Min(1)
    val actorUserId: Long,
    @field:Min(1)
    val careGroupId: Long,
    @field:NotNull
    val ownerType: MediaOwnerType,
    @field:Min(1)
    val ownerId: Long,
    @field:NotBlank
    val contentType: String,
    @field:Min(1)
    val sizeBytes: Long,
)

data class RegisterImageRequest(
    @field:Min(1)
    val actorUserId: Long,
    @field:Min(1)
    val careGroupId: Long,
    @field:NotNull
    val ownerType: MediaOwnerType,
    @field:Min(1)
    val ownerId: Long,
    @field:NotBlank
    val objectKey: String,
    @field:NotBlank
    val contentType: String,
    @field:Min(1)
    val sizeBytes: Long,
)

data class ImageUploadUrlResponse(
    val objectKey: String,
    val uploadUrl: String,
    val expiresAt: OffsetDateTime,
    val requiredHeaders: Map<String, String>,
)

data class ImageResponse(
    val id: Long,
    val ownerType: MediaOwnerType,
    val ownerId: Long,
    val objectKey: String,
    val contentType: String,
    val sizeBytes: Int,
    val uploadedBy: Long,
    val createdAt: OffsetDateTime,
)

data class ImageViewUrlResponse(
    val imageId: Long,
    val viewUrl: String,
    val expiresAt: OffsetDateTime,
)

data class TtsClipRequest(
    @field:Min(1)
    val actorUserId: Long,
    @field:Min(1)
    val careGroupId: Long,
    @field:NotBlank
    val voice: String = "ko_default",
    @field:NotBlank
    val doseLabel: String,
    val scheduledTime: LocalTime,
)

data class TtsClipResponse(
    val id: Long,
    val voice: String,
    val text: String,
    val objectKey: String,
    val playUrl: String,
    val expiresAt: OffsetDateTime,
    val durationMs: Int,
)
