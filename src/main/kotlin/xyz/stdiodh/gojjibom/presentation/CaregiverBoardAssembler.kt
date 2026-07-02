package xyz.stdiodh.gojjibom.presentation

import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.caregroup.CareGroupEntity
import xyz.stdiodh.gojjibom.caregroup.CareGroupMapper
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberEntity
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberRepository
import xyz.stdiodh.gojjibom.caregroup.requiredId
import xyz.stdiodh.gojjibom.dose.DoseEventEntity
import xyz.stdiodh.gojjibom.dose.DoseEventRepository
import xyz.stdiodh.gojjibom.dose.DoseEventStatus
import java.time.LocalDate

/**
 * Builds the FE-shaped CaregiverBoard for /care-groups/{id}/board.
 * pills is a demo placeholder (S5) and alert is null (S6) until those stages land.
 */
@Component
class CaregiverBoardAssembler(
    private val members: CareGroupMemberRepository,
    private val doseEvents: DoseEventRepository,
    private val careGroupMapper: CareGroupMapper,
    private val seniorDayAssembler: SeniorDayAssembler,
) {
    fun assemble(
        careGroup: CareGroupEntity,
        date: LocalDate,
        events: List<DoseEventEntity>,
    ): CaregiverBoard {
        val careGroupId = careGroup.requiredId()
        val seniorId = careGroup.senior.requiredId()
        val activeMembers = members.findByCareGroupIdOrderByIdAsc(careGroupId)
        val sorted = events.sortedWith(compareBy({ it.scheduledAt }, { it.id }))

        return CaregiverBoard(
            patientName = careGroup.senior.name,
            circle = toCareCircle(activeMembers),
            doses = sorted.map { seniorDayAssembler.toDose(it) },
            confirmations = sorted.filter { it.status == DoseEventStatus.TAKEN }.map { toConfirmLog(it) },
            pills = placeholderPillTracking(),
            week = week(seniorId, date),
            alert = null,
        )
    }

    private fun toCareCircle(members: List<CareGroupMemberEntity>): CareCircle {
        val circle = careGroupMapper.circleOf(members)
        return CareCircle(family = circle.family, social = circle.social)
    }

    private fun toConfirmLog(event: DoseEventEntity): ConfirmLog {
        val detail =
            event.confirmedAt?.let { confirmedAt ->
                val time = PresentationFormat.clockLabel(confirmedAt.toLocalTime())
                val method = PresentationFormat.methodKorean(event.confirmMethod)
                if (method.isNotBlank()) "$time · $method" else time
            }
        return ConfirmLog(
            doseLabel = PresentationFormat.slotLabel(event.doseSchedule.label, event.doseSchedule.packetNo),
            status = PresentationFormat.statusToLower(event.status),
            detail = detail,
            photoThumbUrl = null,
        )
    }

    private fun week(
        seniorId: Long,
        date: LocalDate,
    ): List<WeekDay> {
        val from = date.minusDays(WEEK_SPAN - 1)
        val events = doseEvents.findBySeniorIdAndScheduledDateBetween(seniorId, from, date)
        val byDay = events.groupBy { it.scheduledDate }
        return (0 until WEEK_SPAN).map { offset ->
            val day = from.plusDays(offset)
            WeekDay(
                label = "${day.dayOfMonth}",
                status = foldDay(byDay[day].orEmpty()),
            )
        }
    }

    private fun foldDay(events: List<DoseEventEntity>): String {
        val hasMissed =
            events.any {
                it.status == DoseEventStatus.MISSED || it.status == DoseEventStatus.SKIPPED
            }
        return when {
            events.isEmpty() -> "none"
            hasMissed -> "warn"
            events.all { it.status == DoseEventStatus.TAKEN } -> "done"
            else -> "none"
        }
    }

    private fun placeholderPillTracking(): PillTracking = PillTracking(remaining = 0, runOutDate = "", refillDDay = "")

    private companion object {
        private const val WEEK_SPAN = 7L
    }
}
