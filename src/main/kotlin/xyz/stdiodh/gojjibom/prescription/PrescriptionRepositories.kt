package xyz.stdiodh.gojjibom.prescription

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface PharmacyRepository : JpaRepository<PharmacyEntity, Long> {
    fun findByNameAndPhone(
        name: String,
        phone: String,
    ): PharmacyEntity?
}

interface PrescriptionRepository : JpaRepository<PrescriptionEntity, Long> {
    // Fetches only the to-one relations plus the single `schedules` collection.
    // Schedule items / medications are read lazily inside the read-only transaction;
    // fetching two bags (schedules + items) in one query trips MultipleBagFetchException.
    @Query(
        """
        select distinct prescription
        from PrescriptionEntity prescription
        join fetch prescription.senior senior
        join fetch prescription.pharmacy pharmacy
        left join fetch prescription.schedules schedule
        where prescription.registrationCode = :code
        """,
    )
    fun findForConfirmByCode(
        @Param("code") code: String,
    ): PrescriptionEntity?

    @Query(
        """
        select distinct prescription
        from PrescriptionEntity prescription
        join fetch prescription.pharmacy pharmacy
        left join fetch prescription.schedules schedule
        where prescription.senior.id = :seniorId
        order by prescription.startDate desc, prescription.id desc
        """,
    )
    fun findAllBySeniorIdOrderByStartDateDesc(
        @Param("seniorId") seniorId: Long,
    ): List<PrescriptionEntity>
}

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

    @Query(
        """
        select distinct schedule
        from DoseScheduleEntity schedule
        join fetch schedule.prescription prescription
        where prescription.senior.id = :seniorId
          and prescription.status = :status
          and schedule.active = true
          and prescription.startDate <= :date
          and (prescription.endDate is null or prescription.endDate >= :date)
        order by schedule.scheduledTime asc, schedule.id asc
        """,
    )
    fun findActiveForDate(
        @Param("seniorId") seniorId: Long,
        @Param("date") date: LocalDate,
        @Param("status") status: PrescriptionStatus = PrescriptionStatus.ACTIVE,
    ): List<DoseScheduleEntity>
}

interface DoseScheduleItemRepository : JpaRepository<DoseScheduleItemEntity, Long>
