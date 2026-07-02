package xyz.stdiodh.gojjibom.notification

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime

/**
 * Thin @Scheduled wrapper around [MissedDoseEvaluator.evaluate]. All business logic
 * lives in the evaluator (directly testable). Gated by `app.notifications.scheduler.enabled`
 * (default true) so it stays DISABLED in the 'test' profile — tests call evaluate(now).
 */
@Component
@ConditionalOnProperty(
    prefix = "app.notifications.scheduler",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class MissedDoseScheduler(
    private val evaluator: MissedDoseEvaluator,
    private val clock: Clock,
) {
    @Scheduled(fixedRateString = "\${app.notifications.scheduler.fixed-rate-ms:60000}")
    fun run() {
        evaluator.evaluate(OffsetDateTime.now(clock))
    }
}
