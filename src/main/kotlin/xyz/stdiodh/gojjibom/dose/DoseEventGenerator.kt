package xyz.stdiodh.gojjibom.dose

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.prescription.DoseScheduleEntity
import xyz.stdiodh.gojjibom.prescription.DoseScheduleRepository
import xyz.stdiodh.gojjibom.prescription.requiredId
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class DoseEventGenerator(
    private val doseSchedules: DoseScheduleRepository,
    private val doseEvents: DoseEventRepository,
    private val clock: Clock,
) {
    fun ensureEventsFor(
        seniorId: Long,
        date: LocalDate,
    ): List<DoseEventEntity> {
        val schedules = doseSchedules.findActiveForDate(seniorId, date)
        return schedules.mapNotNull { ensureEvent(date, it) }
    }

    private fun ensureEvent(
        date: LocalDate,
        schedule: DoseScheduleEntity,
    ): DoseEventEntity? {
        val scheduleId = schedule.requiredId()
        val existing = doseEvents.findByDoseScheduleIdAndScheduledDate(scheduleId, date)
        if (existing != null) {
            return existing
        }

        val scheduledAt: OffsetDateTime =
            date
                .atTime(schedule.scheduledTime)
                .atZone(clock.zone)
                .toOffsetDateTime()

        return try {
            doseEvents.save(
                DoseEventEntity(
                    doseSchedule = schedule,
                    senior = schedule.prescription.senior,
                    scheduledDate = date,
                    scheduledAt = scheduledAt,
                    status = DoseEventStatus.SCHEDULED,
                    createdAt = OffsetDateTime.now(clock),
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            // A concurrent first-reader already inserted this event; the unique
            // (dose_schedule_id, scheduled_date) index deduplicates. Re-read it.
            doseEvents.findByDoseScheduleIdAndScheduledDate(scheduleId, date)
        }
    }
}
