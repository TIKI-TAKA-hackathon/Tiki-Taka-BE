package xyz.stdiodh.gojjibom.presentation

import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.dose.DoseEventEntity
import xyz.stdiodh.gojjibom.dose.requiredId
import xyz.stdiodh.gojjibom.media.ImageService

/**
 * Builds the FE-shaped PhotoGallery for /care-groups/{id}/photos.
 * Each event carries a photo_image_id (repository filters on not-null); the image
 * is resolved to a signed view URL via ImageService. Events whose image row is
 * missing are dropped so photoUrl stays a non-null String in the contract.
 * thumbnailUrl mirrors photoUrl until dedicated thumbnails land.
 */
@Component
class PhotoGalleryAssembler(
    private val imageService: ImageService,
) {
    fun assemble(
        careGroupId: Long,
        events: List<DoseEventEntity>,
    ): PhotoGallery {
        val photos =
            events.mapNotNull { event ->
                val imageId = event.photoImageId ?: return@mapNotNull null
                val photoUrl = imageService.viewUrlForImage(imageId) ?: return@mapNotNull null
                toGalleryPhoto(event, photoUrl)
            }
        return PhotoGallery(
            careGroupId = careGroupId.toString(),
            photos = photos,
        )
    }

    private fun toGalleryPhoto(
        event: DoseEventEntity,
        photoUrl: String,
    ): GalleryPhoto {
        val schedule = event.doseSchedule
        return GalleryPhoto(
            doseEventId = event.requiredId().toString(),
            doseLabel = PresentationFormat.slotLabel(schedule.label, schedule.packetNo),
            takenAtLabel = PresentationFormat.photoTakenAtLabel(event.confirmedAt ?: event.scheduledAt),
            status = PresentationFormat.statusToLower(event.status),
            method = event.confirmMethod?.let { PresentationFormat.methodToLower(it) },
            reviewStatus = PresentationFormat.reviewStatusToLower(event.photoReviewStatus),
            photoUrl = photoUrl,
            thumbnailUrl = photoUrl,
        )
    }
}
