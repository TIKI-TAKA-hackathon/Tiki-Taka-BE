package xyz.stdiodh.gojjibom.prescription

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PharmacyRepository : JpaRepository<PharmacyEntity, Long> {
    fun findByNameAndPhone(
        name: String,
        phone: String,
    ): PharmacyEntity?
}

interface PrescriptionRepository : JpaRepository<PrescriptionEntity, Long>

interface MedicationRepository : JpaRepository<MedicationEntity, Long> {
    fun findFirstByNameAndCategory(
        name: String,
        category: String?,
    ): MedicationEntity?
}

interface DoseScheduleRepository : JpaRepository<DoseScheduleEntity, Long> {
    @Query(
        """
        select distinct schedule
        from DoseScheduleEntity schedule
        join fetch schedule.prescription prescription
        join fetch prescription.pharmacy pharmacy
        left join fetch schedule.items item
        left join fetch item.medication medication
        where prescription.senior.id = :seniorId
          and prescription.status = :status
          and schedule.active = true
        order by schedule.scheduledTime asc, schedule.id asc
        """,
    )
    fun findActiveBySeniorId(
        seniorId: Long,
        status: PrescriptionStatus = PrescriptionStatus.ACTIVE,
    ): List<DoseScheduleEntity>
}

interface DoseScheduleItemRepository : JpaRepository<DoseScheduleItemEntity, Long>
