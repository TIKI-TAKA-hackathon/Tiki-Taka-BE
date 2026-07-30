package xyz.stdiodh.gojjibom.caregroup

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class CareGroupRulesTest {
    @Test
    fun `invite remains valid immediately before expiry and expires at the exact boundary`() {
        val clock =
            Clock.fixed(
                Instant.parse("2026-07-30T03:00:00Z"),
                ZoneId.of("Asia/Seoul"),
            )
        val expiresAt = OffsetDateTime.now(clock)
        val invite = InviteLinkEntity(expiresAt = expiresAt)

        val immediatelyBefore =
            CareGroupRules.inviteAcceptanceFailure(
                invite = invite,
                role = CareGroupRole.FAMILY,
                currentTime = expiresAt.minusNanos(1),
            )
        val atBoundary =
            CareGroupRules.inviteAcceptanceFailure(
                invite = invite,
                role = CareGroupRole.FAMILY,
                currentTime = expiresAt,
            )

        assertThat(immediatelyBefore).isNull()
        assertThat(atBoundary?.status).isEqualTo(HttpStatus.GONE)
        assertThat(atBoundary?.code).isEqualTo("INVITE_EXPIRED")
    }
}
