package xyz.stdiodh.gojjibom.presentation

import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.caregroup.MealTimeEntity
import xyz.stdiodh.gojjibom.caregroup.MealTimeRepository
import xyz.stdiodh.gojjibom.dose.DoseEventEntity
import xyz.stdiodh.gojjibom.dose.DoseEventStatus
import xyz.stdiodh.gojjibom.dose.requiredId
import xyz.stdiodh.gojjibom.media.ImageService
import xyz.stdiodh.gojjibom.prescription.DoseScheduleEntity
import xyz.stdiodh.gojjibom.prescription.DoseScheduleItemEntity
import xyz.stdiodh.gojjibom.prescription.DoseSlot
import xyz.stdiodh.gojjibom.prescription.requiredId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Builds the FE-shaped SeniorDay for /senior/today. All Korean/lowercase/string
 * formatting is delegated to PresentationFormat; meal-derived labels are computed
 * lazily from meal_times + dose_basis + offset (dose_events.scheduled_at is not rewritten).
 */
@Component
class SeniorDayAssembler(
    private val mealTimes: MealTimeRepository,
    private val imageService: ImageService,
) {
    fun assemble(
        seniorId: Long,
        date: LocalDate,
        events: List<DoseEventEntity>,
    ): SeniorDay {
        val sorted = events.sortedWith(compareBy({ it.scheduledAt }, { it.id }))
        val mealTime = mealTimes.findBySeniorId(seniorId)
        val doses = sorted.map { toDose(it) }
        val next = sorted.firstOrNull { it.status == DoseEventStatus.SCHEDULED }
        return SeniorDay(
            dateLabel = PresentationFormat.dateLabel(date),
            nextDose = next?.let { toNextDose(it, mealTime) },
            doses = doses,
        )
    }

    fun toDose(event: DoseEventEntity): Dose {
        val schedule = event.doseSchedule
        return Dose(
            id = event.requiredId().toString(),
            label = schedule.label,
            time = event.scheduledAt.toLocalTime().format(TIME_FORMAT),
            mealTag = PresentationFormat.mealTag(schedule.mealRelation, schedule.mealOffsetMin, schedule.slot),
            pillCount = schedule.pillCount ?: 0,
            packetNo = schedule.packetNo ?: 0,
            status = PresentationFormat.statusToLower(event.status),
            note = doseNote(event),
        )
    }

    private fun toNextDose(
        event: DoseEventEntity,
        mealTime: MealTimeEntity?,
    ): NextDose {
        val schedule = event.doseSchedule
        val pills = schedule.items.sortedBy { it.requiredId() }.map { toPill(it) }
        val includesNote = includesNote(schedule)
        return NextDose(
            doseId = event.requiredId().toString(),
            label = PresentationFormat.slotLabel(schedule.label, schedule.packetNo),
            alarmLabel = PresentationFormat.clockLabel(schedule.scheduledTime),
            pillCount = schedule.pillCount ?: 0,
            packetNo = schedule.packetNo ?: 0,
            mealTag = PresentationFormat.mealTag(schedule.mealRelation, schedule.mealOffsetMin, schedule.slot),
            includesNote = includesNote,
            baselineNote = baselineNote(schedule, mealTime),
            spokenText = spokenText(schedule),
            doneTimeLabel = event.confirmedAt?.let { PresentationFormat.clockLabel(it.toLocalTime()) }.orEmpty(),
            dispensingType =
                schedule.prescription.dispensingType.name
                    .lowercase(),
            photoThumbUrl = event.photoImageId?.let { imageService.viewUrlForImage(it) },
            pills = pills,
        )
    }

    private fun toPill(item: DoseScheduleItemEntity): Pill =
        Pill(
            id = item.medication.requiredId().toString(),
            name = item.medication.name,
            shape =
                item.medication.shape.name
                    .lowercase(),
            note = item.medication.description.orEmpty(),
        )

    private fun doseNote(event: DoseEventEntity): String =
        when (event.status) {
            DoseEventStatus.TAKEN -> "${event.doseSchedule.pillCount ?: 0}개 모두 드셨어요"
            DoseEventStatus.SCHEDULED -> "잠시 후 알림이 와요"
            DoseEventStatus.MISSED, DoseEventStatus.SKIPPED -> "아직 확인되지 않았어요"
        }

    private fun includesNote(schedule: DoseScheduleEntity): String {
        val category =
            schedule.items
                .mapNotNull { it.medication.category?.takeIf { c -> c.isNotBlank() } }
                .firstOrNull() ?: return ""
        return "${category}약이 포함돼 있어요"
    }

    private fun baselineNote(
        schedule: DoseScheduleEntity,
        mealTime: MealTimeEntity?,
    ): String {
        if (mealTime == null) {
            return ""
        }
        val mealTimeForSlot =
            when (schedule.slot) {
                DoseSlot.MORNING -> "아침 식사" to mealTime.breakfastTime
                DoseSlot.LUNCH -> "점심 식사" to mealTime.lunchTime
                DoseSlot.DINNER -> "저녁 식사" to mealTime.dinnerTime
                else -> null
            }
        return mealTimeForSlot
            ?.let { (mealLabel, time) ->
                "$mealLabel ${PresentationFormat.clockLabel(time)} 기준"
            }.orEmpty()
    }

    private fun spokenText(schedule: DoseScheduleEntity): String {
        val count = schedule.pillCount ?: 0
        return "${schedule.label} ${count}개를 드실 시간이에요."
    }

    private companion object {
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
