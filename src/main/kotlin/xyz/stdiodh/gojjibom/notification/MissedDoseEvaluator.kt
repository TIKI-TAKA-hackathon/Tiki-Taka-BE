package xyz.stdiodh.gojjibom.notification

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import xyz.stdiodh.gojjibom.caregroup.CareGroupRepository
import xyz.stdiodh.gojjibom.caregroup.NotificationSettingsEntity
import xyz.stdiodh.gojjibom.caregroup.NotificationSettingsRepository
import xyz.stdiodh.gojjibom.caregroup.requiredId
import xyz.stdiodh.gojjibom.dose.DoseEventEntity
import xyz.stdiodh.gojjibom.dose.DoseEventRepository
import xyz.stdiodh.gojjibom.dose.DoseEventStatus
import xyz.stdiodh.gojjibom.dose.requiredId
import xyz.stdiodh.gojjibom.presentation.PresentationFormat
import java.time.OffsetDateTime

/**
 * Channel-independent missed-dose / escalation engine (WP3a).
 *
 * For every dose_event still SCHEDULED whose scheduled_at is past due it:
 *   1. skips seniors whose notification_settings are disabled;
 *   2. transitions the event to MISSED once the grace window has elapsed and
 *      records a level-0 MISSED notification;
 *   3. records escalation rungs following the cadence
 *      `scheduled_at + n × remind_interval_min` for n in 1..max_retries.
 *
 * Idempotent: the UNIQUE (dose_event_id, type, level) guard plus a pre-check
 * mean re-running over the same clock never inserts a duplicate rung.
 *
 * Wording stays at "약 미확인" — no fall/emergency-detection overclaim (spec 005 §G).
 * Actual delivery (web push / SMS) is the separate WP3b task; this only writes
 * in-app `notifications` rows.
 */
@Component
class MissedDoseEvaluator(
    private val doseEvents: DoseEventRepository,
    private val notificationSettings: NotificationSettingsRepository,
    private val careGroups: CareGroupRepository,
    private val notifications: NotificationRepository,
    private val properties: NotificationProperties,
) {
    /**
     * Evaluates all overdue scheduled events as of [now] and returns the number
     * of notifications created on this run.
     */
    @Transactional
    fun evaluate(now: OffsetDateTime): Int {
        var created = 0
        for (event in doseEvents.findOverdueOpen(now)) {
            created += evaluateEvent(event, now)
        }
        return created
    }

    private fun evaluateEvent(
        event: DoseEventEntity,
        now: OffsetDateTime,
    ): Int {
        val seniorId = event.senior.requiredId()
        val settings = notificationSettings.findBySeniorId(seniorId) ?: DEFAULT_SETTINGS
        val careGroupId = careGroups.findBySeniorId(seniorId)?.requiredId()
        if (!settings.enabled || careGroupId == null) {
            return 0
        }

        var created = 0
        if (transitionToMissed(event, careGroupId, now)) {
            created++
        }
        created += insertEscalations(event, careGroupId, settings, now)
        return created
    }

    /** Marks the event MISSED (after grace) and records the level-0 MISSED notification. */
    private fun transitionToMissed(
        event: DoseEventEntity,
        careGroupId: Long,
        now: OffsetDateTime,
    ): Boolean {
        val graceDeadline = event.scheduledAt.plusMinutes(properties.missedGraceMin.toLong())
        if (now.isBefore(graceDeadline)) {
            return false
        }
        if (event.status == DoseEventStatus.SCHEDULED) {
            event.status = DoseEventStatus.MISSED
        }
        return insertNotification(event, careGroupId, NotificationType.MISSED, MISSED_LEVEL)
    }

    /** Records escalation rungs whose cadence deadline has passed, up to max_retries. */
    private fun insertEscalations(
        event: DoseEventEntity,
        careGroupId: Long,
        settings: NotificationSettingsEntity,
        now: OffsetDateTime,
    ): Int {
        var created = 0
        for (level in 1..settings.maxRetries) {
            val deadline = event.scheduledAt.plusMinutes(settings.remindIntervalMin.toLong() * level)
            if (now.isBefore(deadline)) {
                break
            }
            if (insertNotification(event, careGroupId, NotificationType.ESCALATION, level)) {
                created++
            }
        }
        return created
    }

    private fun insertNotification(
        event: DoseEventEntity,
        careGroupId: Long,
        type: NotificationType,
        level: Int,
    ): Boolean {
        val doseEventId = event.requiredId()
        if (notifications.existsByDoseEventIdAndTypeAndLevel(doseEventId, type, level)) {
            return false
        }
        val label = PresentationFormat.slotLabel(event.doseSchedule.label, event.doseSchedule.packetNo)
        return try {
            notifications.save(
                NotificationEntity(
                    careGroupId = careGroupId,
                    seniorId = event.senior.requiredId(),
                    doseEventId = doseEventId,
                    type = type,
                    level = level,
                    title = titleFor(type, level),
                    body = bodyFor(type, level, label),
                    createdAt = OffsetDateTime.now(),
                ),
            )
            true
        } catch (_: DataIntegrityViolationException) {
            // A concurrent run already inserted this rung; the UNIQUE guard deduplicates.
            false
        }
    }

    private fun titleFor(
        type: NotificationType,
        level: Int,
    ): String =
        when (type) {
            NotificationType.MISSED -> "약 미확인"
            NotificationType.ESCALATION -> "약 미확인 ${level}차 알림"
            NotificationType.REMINDER -> "복약 알림"
        }

    private fun bodyFor(
        type: NotificationType,
        level: Int,
        label: String,
    ): String =
        when (type) {
            NotificationType.MISSED -> "$label 복용이 아직 확인되지 않았어요."
            NotificationType.ESCALATION -> "$label 복용이 여전히 확인되지 않았어요. (${level}차 재알림)"
            NotificationType.REMINDER -> "$label 드실 시간이에요."
        }

    private companion object {
        private const val MISSED_LEVEL = 0

        // Used only when a senior has no notification_settings row (GET semantics: defaults).
        private val DEFAULT_SETTINGS =
            NotificationSettingsEntity(
                enabled = true,
                remindIntervalMin = NotificationSettingsEntity.DEFAULT_REMIND_INTERVAL_MIN,
                maxRetries = NotificationSettingsEntity.DEFAULT_MAX_RETRIES,
            )
    }
}
