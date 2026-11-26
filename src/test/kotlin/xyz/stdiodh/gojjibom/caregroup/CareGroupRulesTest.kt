package xyz.stdiodh.gojjibom.caregroup

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
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

    @Test
    fun `invite without a max use limit stays acceptable regardless of use count`() {
        val now = OffsetDateTime.parse("2026-07-30T12:00:00+09:00")
        val invite = InviteLinkEntity(expiresAt = now.plusHours(1), maxUses = null, useCount = 1_000)

        assertThat(CareGroupRules.inviteAcceptanceFailure(invite, CareGroupRole.FAMILY, now)).isNull()
    }

    @Test
    fun `member update requires a status or role and rejects PENDING status`() {
        val member = CareGroupMemberEntity(role = CareGroupRole.FAMILY, status = MemberStatus.ACTIVE)

        val empty =
            CareGroupRules.updateFailure(member, UpdateMemberRequest(actorUserId = 1, status = null, role = null))
        val toPending =
            CareGroupRules.updateFailure(
                member,
                UpdateMemberRequest(actorUserId = 1, status = MemberStatus.PENDING, role = null),
            )
        val toRemoved =
            CareGroupRules.updateFailure(
                member,
                UpdateMemberRequest(actorUserId = 1, status = MemberStatus.REMOVED, role = null),
            )

        assertThat(empty?.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(empty?.code).isEqualTo("INVALID_MEMBER_UPDATE")
        assertThat(toPending?.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(toPending?.code).isEqualTo("INVALID_MEMBER_STATUS")
        assertThat(toRemoved).isNull()
    }

    @Test
    fun `role only update leaves status and join time untouched`() {
        val joinedAt = OffsetDateTime.parse("2026-07-01T09:00:00+09:00")
        val member =
            CareGroupMemberEntity(role = CareGroupRole.FAMILY, status = MemberStatus.ACTIVE, joinedAt = joinedAt)

        CareGroupRules.applyUpdate(
            member,
            UpdateMemberRequest(actorUserId = 1, status = null, role = CareGroupRole.SOCIAL_WORKER),
            joinedAt.plusDays(3),
        )

        assertThat(member.role).isEqualTo(CareGroupRole.SOCIAL_WORKER)
        assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(member.joinedAt).isEqualTo(joinedAt)
    }

    @Test
    fun `activation stamps join time only for members who never joined`() {
        val firstJoin = OffsetDateTime.parse("2026-07-01T09:00:00+09:00")
        val later = firstJoin.plusDays(10)
        val rejoining =
            CareGroupMemberEntity(status = MemberStatus.REMOVED, joinedAt = firstJoin)
        val fresh = CareGroupMemberEntity(status = MemberStatus.PENDING, joinedAt = null)
        val activate = UpdateMemberRequest(actorUserId = 1, status = MemberStatus.ACTIVE, role = null)

        CareGroupRules.applyUpdate(rejoining, activate, later)
        CareGroupRules.applyUpdate(fresh, activate, later)

        assertThat(rejoining.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(rejoining.joinedAt).isEqualTo(firstJoin)
        assertThat(fresh.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(fresh.joinedAt).isEqualTo(later)
    }

    @Test
    fun `meal time change log records every field on insert and only changed fields on update`() {
        val now = OffsetDateTime.parse("2026-07-30T12:00:00+09:00")
        val existing =
            MealTimeEntity(
                breakfastTime = LocalTime.of(8, 0),
                lunchTime = LocalTime.of(12, 30),
                dinnerTime = LocalTime.of(18, 30),
            )

        val inserted =
            CareGroupRules.mealTimeChangeLog(
                careGroupId = 10,
                actorUserId = 20,
                seniorId = 30,
                existing = null,
                breakfast = LocalTime.of(8, 0),
                lunch = LocalTime.of(12, 30),
                dinner = LocalTime.of(18, 30),
                currentTime = now,
            )
        val unchanged =
            CareGroupRules.mealTimeChangeLog(
                careGroupId = 10,
                actorUserId = 20,
                seniorId = 30,
                existing = existing,
                breakfast = LocalTime.of(8, 0),
                lunch = LocalTime.of(12, 30),
                dinner = LocalTime.of(18, 30),
                currentTime = now,
            )
        val dinnerOnly =
            CareGroupRules.mealTimeChangeLog(
                careGroupId = 10,
                actorUserId = 20,
                seniorId = 30,
                existing = existing,
                breakfast = LocalTime.of(8, 0),
                lunch = LocalTime.of(12, 30),
                dinner = LocalTime.of(19, 5),
                currentTime = now,
            )

        assertThat(inserted.map { Triple(it.field, it.oldValue, it.newValue) })
            .containsExactly(
                Triple("breakfast_time", null, "08:00"),
                Triple("lunch_time", null, "12:30"),
                Triple("dinner_time", null, "18:30"),
            )
        assertThat(unchanged).isEmpty()
        assertThat(dinnerOnly).hasSize(1)
        with(dinnerOnly.single()) {
            assertThat(careGroupId).isEqualTo(10L)
            assertThat(actorUserId).isEqualTo(20L)
            assertThat(targetType).isEqualTo(ChangeTargetType.MEAL_TIME)
            assertThat(targetId).isEqualTo(30L)
            assertThat(field).isEqualTo("dinner_time")
            assertThat(oldValue).isEqualTo("18:30")
            assertThat(newValue).isEqualTo("19:05")
            assertThat(createdAt).isEqualTo(now)
        }
    }
}
