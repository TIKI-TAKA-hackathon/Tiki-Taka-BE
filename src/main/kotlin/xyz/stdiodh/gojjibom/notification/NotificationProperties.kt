package xyz.stdiodh.gojjibom.notification

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Config-driven knobs for the missed-dose engine.
 *
 * `scheduler.enabled` gates the @Scheduled wrapper and is FALSE in the test
 * profile so tests drive `evaluate(now)` directly without wall-clock timing.
 */
@ConfigurationProperties(prefix = "app.notifications")
data class NotificationProperties(
    /** Grace window (minutes) after scheduled_at before an event flips to MISSED. */
    val missedGraceMin: Int = 0,
    val scheduler: Scheduler = Scheduler(),
) {
    data class Scheduler(
        val enabled: Boolean = true,
        /** @Scheduled fixedRate in milliseconds. */
        val fixedRateMs: Long = 60_000,
    )
}
