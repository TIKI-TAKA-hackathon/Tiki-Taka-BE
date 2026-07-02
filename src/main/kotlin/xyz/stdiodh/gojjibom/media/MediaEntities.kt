package xyz.stdiodh.gojjibom.media

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

enum class MediaOwnerType {
    CARE_GROUP,
    MEDICATION,
    DOSE_EVENT,
}

@Entity
@Table(name = "images")
class ImageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    var ownerType: MediaOwnerType = MediaOwnerType.CARE_GROUP,
    @Column(name = "owner_id", nullable = false)
    var ownerId: Long = 0,
    @Column(name = "object_key", nullable = false, unique = true)
    var objectKey: String = "",
    @Column(name = "content_type", nullable = false)
    var contentType: String = "",
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Int = 0,
    @Column(name = "uploaded_by", nullable = false)
    var uploadedBy: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "tts_clips")
class TtsClipEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "text_hash", nullable = false)
    var textHash: String = "",
    @Column(nullable = false)
    var voice: String = "",
    @Column(nullable = false)
    var text: String = "",
    @Column(name = "object_key", nullable = false)
    var objectKey: String = "",
    @Column(name = "duration_ms", nullable = false)
    var durationMs: Int = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
