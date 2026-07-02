package xyz.stdiodh.gojjibom.dose

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface DoseEventRepository : JpaRepository<DoseEventEntity, Long> {
    @Query(
        """
        select distinct event
        from DoseEventEntity event
        join fetch event.doseSchedule schedule
        join fetch schedule.prescription prescription
        join fetch prescription.pharmacy pharmacy
        left join fetch schedule.items item
        left join fetch item.medication medication
        where event.senior.id = :seniorId
          and event.scheduledDate = :date
        order by event.scheduledAt asc, event.id asc
        """,
    )
    fun findBySeniorIdAndScheduledDate(
        seniorId: Long,
        date: LocalDate,
    ): List<DoseEventEntity>

    fun findByDoseScheduleIdAndScheduledDate(
        doseScheduleId: Long,
        scheduledDate: LocalDate,
    ): DoseEventEntity?

    @Query(
        """
        select event
        from DoseEventEntity event
        join fetch event.doseSchedule schedule
        join fetch schedule.prescription prescription
        join fetch prescription.pharmacy pharmacy
        join fetch event.senior senior
        left join fetch schedule.items item
        left join fetch item.medication medication
        where event.id = :id
        """,
    )
    fun findDetailById(id: Long): DoseEventEntity?

    @Query(
        """
        select distinct event
        from DoseEventEntity event
        join fetch event.doseSchedule schedule
        where event.senior.id = :seniorId
          and event.scheduledDate between :from and :to
        order by event.scheduledAt asc, event.id asc
        """,
    )
    fun findBySeniorIdAndScheduledDateBetween(
        seniorId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<DoseEventEntity>

    @Query(
        """
        select event
        from DoseEventEntity event
        join fetch event.doseSchedule schedule
        where event.senior.id = :seniorId
          and event.scheduledDate between :from and :to
          and event.photoImageId is not null
        order by event.scheduledAt desc, event.id desc
        """,
    )
    fun findPhotosBySeniorIdAndScheduledDateBetween(
        seniorId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<DoseEventEntity>
}
