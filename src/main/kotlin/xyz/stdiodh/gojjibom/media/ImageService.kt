package xyz.stdiodh.gojjibom.media

import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ImageService(
    private val authorizer: MembershipAuthorizer,
    private val objectStorage: ObjectStorage,
    private val images: ImageRepository,
    private val mediaProperties: MediaProperties,
) {
    fun createUploadUrl(request: ImageUploadUrlRequest): ImageUploadUrlResponse {
        authorizer.requireActiveMember(request.careGroupId, request.actorUserId)
        validateOwner(request.ownerType, request.ownerId, request.careGroupId)
        val contentType = normalizeContentType(request.contentType)
        validateImage(contentType, request.sizeBytes)

        val objectKey = newImageObjectKey(request.careGroupId, contentType)
        val presigned =
            objectStorage.presignUpload(
                objectKey = objectKey,
                contentType = contentType,
                sizeBytes = request.sizeBytes,
                ttlSeconds = mediaProperties.uploadUrlTtlSeconds,
            )

        return ImageUploadUrlResponse(
            objectKey = objectKey,
            uploadUrl = presigned.url,
            expiresAt = presigned.expiresAt,
            requiredHeaders = presigned.requiredHeaders,
        )
    }

    fun registerImage(request: RegisterImageRequest): ImageResponse {
        authorizer.requireActiveMember(request.careGroupId, request.actorUserId)
        validateOwner(request.ownerType, request.ownerId, request.careGroupId)
        val contentType = normalizeContentType(request.contentType)
        validateImage(contentType, request.sizeBytes)
        validateObjectKey(request.objectKey, request.careGroupId)

        if (images.existsByObjectKey(request.objectKey)) {
            throw MediaErrors.conflict("IMAGE_ALREADY_REGISTERED", "Image object key is already registered")
        }

        val image =
            images.save(
                ImageEntity(
                    ownerType = request.ownerType,
                    ownerId = request.ownerId,
                    objectKey = request.objectKey,
                    contentType = contentType,
                    sizeBytes = request.sizeBytes.toInt(),
                    uploadedBy = request.actorUserId,
                    createdAt = OffsetDateTime.now(),
                ),
            )

        return image.toResponse()
    }

    fun createViewUrl(
        imageId: Long,
        actorUserId: Long,
        careGroupId: Long,
    ): ImageViewUrlResponse {
        authorizer.requireActiveMember(careGroupId, actorUserId)
        val image =
            images.findById(imageId).orElseThrow {
                MediaErrors.notFound("IMAGE_NOT_FOUND", "Image not found")
            }
        validateObjectKey(image.objectKey, careGroupId)

        val presigned =
            objectStorage.presignView(
                objectKey = image.objectKey,
                ttlSeconds = mediaProperties.viewUrlTtlSeconds,
            )

        return ImageViewUrlResponse(
            imageId = image.requiredId(),
            viewUrl = presigned.url,
            expiresAt = presigned.expiresAt,
        )
    }

    /**
     * Demo-grade signed view URL for the unauthenticated BFF read endpoints
     * (/senior/today, /care-groups/{id}/board). No membership check by design
     * (SYNC-PLAN §4-7); returns null when the image is missing so the BFF payload
     * degrades gracefully. Harden alongside the BFF endpoints in S8.
     */
    fun viewUrlForImage(imageId: Long): String? {
        val image = images.findById(imageId).orElse(null) ?: return null
        return objectStorage
            .presignView(
                objectKey = image.objectKey,
                ttlSeconds = mediaProperties.viewUrlTtlSeconds,
            ).url
    }

    fun assertDoseEventImage(
        imageId: Long,
        doseEventId: Long,
        careGroupId: Long,
    ) {
        val image =
            images.findByIdAndOwnerTypeAndOwnerId(imageId, MediaOwnerType.DOSE_EVENT, doseEventId)
                ?: throw MediaErrors.badRequest(
                    "INVALID_IMAGE_OWNER",
                    "Image must be owned by this dose event",
                )
        if (!image.objectKey.startsWith(imageObjectKeyPrefix(careGroupId))) {
            throw MediaErrors.badRequest(
                "INVALID_IMAGE_OWNER",
                "Image must belong to the same care group as the dose event",
            )
        }
    }

    private fun normalizeContentType(contentType: String): String = contentType.trim().lowercase()

    private fun validateImage(
        contentType: String,
        sizeBytes: Long,
    ) {
        if (contentType !in ALLOWED_IMAGE_CONTENT_TYPES) {
            throw MediaErrors.badRequest("UNSUPPORTED_IMAGE_TYPE", "Unsupported image content type")
        }
        if (sizeBytes > mediaProperties.maxImageBytes) {
            throw MediaErrors.badRequest("IMAGE_TOO_LARGE", "Image exceeds the maximum allowed size")
        }
    }

    private fun validateOwner(
        ownerType: MediaOwnerType,
        ownerId: Long,
        careGroupId: Long,
    ) {
        if (ownerType == MediaOwnerType.CARE_GROUP && ownerId != careGroupId) {
            throw MediaErrors.badRequest("INVALID_IMAGE_OWNER", "Care-group image owner must match careGroupId")
        }
    }

    private fun validateObjectKey(
        objectKey: String,
        careGroupId: Long,
    ) {
        if (!objectKey.startsWith(imageObjectKeyPrefix(careGroupId))) {
            throw MediaErrors.forbidden("IMAGE_OBJECT_KEY_DENIED", "Image object key is outside the care group scope")
        }
    }

    private fun newImageObjectKey(
        careGroupId: Long,
        contentType: String,
    ): String = "${imageObjectKeyPrefix(careGroupId)}${UUID.randomUUID()}.${extensionFor(contentType)}"

    private fun imageObjectKeyPrefix(careGroupId: Long): String = "images/$careGroupId/"

    private fun extensionFor(contentType: String): String =
        when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw MediaErrors.badRequest("UNSUPPORTED_IMAGE_TYPE", "Unsupported image content type")
        }

    private fun ImageEntity.toResponse(): ImageResponse =
        ImageResponse(
            id = requiredId(),
            ownerType = ownerType,
            ownerId = ownerId,
            objectKey = objectKey,
            contentType = contentType,
            sizeBytes = sizeBytes,
            uploadedBy = uploadedBy,
            createdAt = createdAt,
        )

    private companion object {
        private val ALLOWED_IMAGE_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
