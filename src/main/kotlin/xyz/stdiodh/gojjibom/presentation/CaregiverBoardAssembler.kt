package xyz.stdiodh.gojjibom.presentation

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.caregroup.CareGroupEntity
import xyz.stdiodh.gojjibom.caregroup.CareGroupMapper
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberEntity
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberRepository
import xyz.stdiodh.gojjibom.caregroup.requiredId
import xyz.stdiodh.gojjibom.dose.DoseEventEntity
import xyz.stdiodh.gojjibom.dose.DoseEventRepository
import xyz.stdiodh.gojjibom.dose.DoseEventStatus
import xyz.stdiodh.gojjibom.notification.NotificationRepository
import xyz.stdiodh.gojjibom.notification.NotificationType
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Builds the FE-shaped CaregiverBoard for /care-groups/{id}/board.
 * pills is a demo placeholder (S5); alert reflects the latest unresolved
 * MISSED/ESCALATION notification (non-diagnostic, "약 미확인").
 */
@Component
class CaregiverBoardAssembler(
    private val members: CareGroupMemberRepository,
    private val doseEvents: DoseEventRepository,
    private val notifications: NotificationRepository,
    private val careGroupMapper: CareGroupMapper,
    private val seniorDayAssembler: SeniorDayAssembler,
    private val clock: Clock,
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
            alert = escalationAlert(seniorId),
        )
    }

    /**
     * Derives the board alert from the latest unresolved (read_at IS NULL) MISSED/ESCALATION
     * notification. Non-diagnostic ("약 미확인"); returns null when nothing is outstanding.
     */
    private fun escalationAlert(seniorId: Long): EscalationAlert? {
        val latest =
            notifications.findFirstBySeniorIdAndTypeInAndReadAtIsNullOrderByCreatedAtDescIdDesc(
                seniorId = seniorId,
                types = listOf(NotificationType.MISSED, NotificationType.ESCALATION),
            ) ?: return null
        val doseEvent = latest.doseEventId?.let { doseEvents.findByIdOrNull(it) }
        val retries =
            latest.doseEventId
                ?.let { notifications.countByDoseEventIdAndType(it, NotificationType.ESCALATION) }
                ?: 0
        return EscalationAlert(
            doseLabel = doseLabelFor(doseEvent),
            // scheduledTime is a LocalTime (Asia/Seoul wall-clock); avoids the JDBC
            // offset normalization that scheduledAt.toLocalTime() would suffer.
            lastAlarm = doseEvent?.let { PresentationFormat.clockLabel(it.doseSchedule.scheduledTime) }.orEmpty(),
            retries = retries,
            minutesElapsed = minutesElapsed(doseEvent),
            steps = escalationSteps(retries),
        )
    }

    private fun doseLabelFor(doseEvent: DoseEventEntity?): String =
        doseEvent?.let { PresentationFormat.slotLabel(it.doseSchedule.label, it.doseSchedule.packetNo) }.orEmpty()

    private fun minutesElapsed(doseEvent: DoseEventEntity?): Int {
        val scheduledAt = doseEvent?.scheduledAt ?: return 0
        val elapsed = ChronoUnit.MINUTES.between(scheduledAt, OffsetDateTime.now(clock))
        return elapsed.coerceAtLeast(0).toInt()
    }

    private fun escalationSteps(retries: Int): List<String> = (1..retries).map { "${it}차 재알림" } + "에스컬레이션"

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
