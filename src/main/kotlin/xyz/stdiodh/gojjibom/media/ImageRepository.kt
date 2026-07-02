package xyz.stdiodh.gojjibom.media

import org.springframework.data.jpa.repository.JpaRepository

interface ImageRepository : JpaRepository<ImageEntity, Long> {
    fun existsByObjectKey(objectKey: String): Boolean

    fun findByIdAndOwnerTypeAndOwnerId(
        id: Long,
        ownerType: MediaOwnerType,
        ownerId: Long,
    ): ImageEntity?
}
