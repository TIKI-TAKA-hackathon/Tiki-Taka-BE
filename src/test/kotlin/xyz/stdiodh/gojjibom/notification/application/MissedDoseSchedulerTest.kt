package xyz.stdiodh.gojjibom.notification.application

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class MissedDoseSchedulerTest {
    @Test
    fun `scheduled run evaluates overdue doses as of the injected clock`() {
        val evaluator = mock(MissedDoseEvaluator::class.java)
        val clock = Clock.fixed(Instant.parse("2026-07-02T00:30:00Z"), ZoneId.of("Asia/Seoul"))

        MissedDoseScheduler(evaluator, clock).run()

        verify(evaluator).evaluate(OffsetDateTime.parse("2026-07-02T09:30:00+09:00"))
        verifyNoMoreInteractions(evaluator)
    }
}
