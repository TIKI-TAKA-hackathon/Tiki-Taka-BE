package xyz.stdiodh.gojjibom.dose

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import xyz.stdiodh.gojjibom.caregroup.UserEntity
import xyz.stdiodh.gojjibom.prescription.DoseScheduleEntity
import java.time.LocalDate
import java.time.OffsetDateTime

enum class DoseEventStatus {
    SCHEDULED,
    TAKEN,
    MISSED,
    SKIPPED,
}

enum class ConfirmMethod {
    VOICE,
    BUTTON,
    CAREGIVER,
    AUTO,
}

@Entity
@Table(name = "dose_events")
class DoseEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dose_schedule_id", nullable = false)
    var doseSchedule: DoseScheduleEntity = DoseScheduleEntity(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_id", nullable = false)
    var senior: UserEntity = UserEntity(),
    @Column(name = "scheduled_date", nullable = false)
    var scheduledDate: LocalDate = LocalDate.now(),
    @Column(name = "scheduled_at", nullable = false)
    var scheduledAt: OffsetDateTime = OffsetDateTime.now(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DoseEventStatus = DoseEventStatus.SCHEDULED,
    @Column(name = "confirmed_at")
    var confirmedAt: OffsetDateTime? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "confirm_method")
    var confirmMethod: ConfirmMethod? = null,
    @Column(name = "confirmed_by")
    var confirmedById: Long? = null,
    @Column(name = "photo_image_id")
    var photoImageId: Long? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
