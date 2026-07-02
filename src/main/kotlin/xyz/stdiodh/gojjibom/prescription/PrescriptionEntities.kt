package xyz.stdiodh.gojjibom.prescription

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import xyz.stdiodh.gojjibom.caregroup.UserEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

enum class PrescriptionStatus {
    ACTIVE,
    ENDED,
}

enum class DoseSlot {
    MORNING,
    LUNCH,
    DINNER,
    BEDTIME,
    CUSTOM,
}

enum class MealRelation {
    BEFORE_MEAL,
    AFTER_MEAL,
    WITH_MEAL,
    NONE,
}

enum class DoseBasis {
    BEFORE_MEAL,
    AFTER_MEAL,
    BEDTIME,
    EMPTY_STOMACH,
    FIXED,
}

enum class DispensingType {
    POUCH,
    ORGANIZER,
}

enum class PillShape {
    ROUND,
    OVAL,
    CAPSULE,
}

@Entity
@Table(name = "pharmacies")
class PharmacyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false)
    var name: String = "",
    @Column(nullable = false)
    var phone: String = "",
    @Column
    var address: String? = null,
)

@Entity
@Table(name = "prescriptions")
class PrescriptionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_id", nullable = false)
    var senior: UserEntity = UserEntity(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    var pharmacy: PharmacyEntity = PharmacyEntity(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by")
    var registeredBy: UserEntity? = null,
    @Column(name = "prescribed_date", nullable = false)
    var prescribedDate: LocalDate = LocalDate.now(),
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate = LocalDate.now(),
    @Column(name = "end_date")
    var endDate: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PrescriptionStatus = PrescriptionStatus.ACTIVE,
    @Convert(converter = DispensingTypeConverter::class)
    @Column(name = "dispensing_type", nullable = false)
    var dispensingType: DispensingType = DispensingType.POUCH,
    @Column(name = "registration_code")
    var registrationCode: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @OneToMany(mappedBy = "prescription")
    var schedules: MutableList<DoseScheduleEntity> = mutableListOf(),
)

@Entity
@Table(name = "medications")
class MedicationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false)
    var name: String = "",
    @Column
    var category: String? = null,
    @Column(name = "photo_url")
    var photoUrl: String? = null,
    @Column
    var description: String? = null,
    @Convert(converter = PillShapeConverter::class)
    @Column(name = "shape", nullable = false)
    var shape: PillShape = PillShape.ROUND,
)

@Entity
@Table(name = "dose_schedules")
class DoseScheduleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    var prescription: PrescriptionEntity = PrescriptionEntity(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var slot: DoseSlot = DoseSlot.CUSTOM,
    @Column(nullable = false)
    var label: String = "",
    @Column(name = "packet_no")
    var packetNo: Int? = null,
    @Column(name = "scheduled_time", nullable = false)
    var scheduledTime: LocalTime = LocalTime.MIDNIGHT,
    @Enumerated(EnumType.STRING)
    @Column(name = "meal_relation", nullable = false)
    var mealRelation: MealRelation = MealRelation.NONE,
    @Column(name = "meal_offset_min")
    var mealOffsetMin: Int? = null,
    @Column(name = "pill_count")
    var pillCount: Int? = null,
    @Convert(converter = DoseBasisConverter::class)
    @Column(name = "dose_basis", nullable = false)
    var doseBasis: DoseBasis = DoseBasis.FIXED,
    @Column(nullable = false)
    var active: Boolean = true,
    @OneToMany(mappedBy = "doseSchedule")
    var items: MutableList<DoseScheduleItemEntity> = mutableListOf(),
)

@Entity
@Table(name = "dose_schedule_items")
class DoseScheduleItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dose_schedule_id", nullable = false)
    var doseSchedule: DoseScheduleEntity = DoseScheduleEntity(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    var medication: MedicationEntity = MedicationEntity(),
    @Column(nullable = false)
    var count: Int = 1,
)
