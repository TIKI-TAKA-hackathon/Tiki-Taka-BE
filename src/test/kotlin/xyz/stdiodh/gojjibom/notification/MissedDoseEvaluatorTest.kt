package xyz.stdiodh.gojjibom.notification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import xyz.stdiodh.gojjibom.caregroup.CareGroupEntity
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberEntity
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberRepository
import xyz.stdiodh.gojjibom.caregroup.CareGroupRepository
import xyz.stdiodh.gojjibom.caregroup.CareGroupRole
import xyz.stdiodh.gojjibom.caregroup.MemberStatus
import xyz.stdiodh.gojjibom.caregroup.NotificationSettingsEntity
import xyz.stdiodh.gojjibom.caregroup.NotificationSettingsRepository
import xyz.stdiodh.gojjibom.caregroup.UserEntity
import xyz.stdiodh.gojjibom.dose.DoseEventEntity
import xyz.stdiodh.gojjibom.dose.DoseEventRepository
import xyz.stdiodh.gojjibom.dose.DoseEventStatus
import xyz.stdiodh.gojjibom.prescription.DoseScheduleEntity
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class MissedDoseEvaluatorTest {
    private val doseEvents = mock(DoseEventRepository::class.java)
    private val notificationSettings = mock(NotificationSettingsRepository::class.java)
    private val careGroups = mock(CareGroupRepository::class.java)
    private val notifications = mock(NotificationRepository::class.java)
    private val members = mock(CareGroupMemberRepository::class.java)
    private val sender = RecordingSender()
    private val clock = Clock.fixed(Instant.parse("2026-07-02T01:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val saved = mutableListOf<NotificationEntity>()

    private val evaluator =
        MissedDoseEvaluator(
            doseEvents = doseEvents,
            notificationSettings = notificationSettings,
            careGroups = careGroups,
            notifications = notifications,
            properties = NotificationProperties(missedGraceMin = 0),
            recipientResolver = CaregiverRecipientResolver(members),
            sender = sender,
            clock = clock,
        )

    @Test
    fun `senior without a settings row is evaluated with the default cadence`() {
        val event = overdueEvent()
        givenOverdue(event, NOW)
        givenCareGroup()
        givenPrimaryCaregiver()
        givenSaveSucceeds()

        // Defaults: remind every 5 minutes, up to 3 retries; +12 min -> rungs 1 and 2 are due.
        val created = evaluator.evaluate(NOW)

        assertThat(created).isEqualTo(3)
        assertThat(event.status).isEqualTo(DoseEventStatus.MISSED)
        assertThat(saved.map { it.type to it.level })
            .containsExactly(
                NotificationType.MISSED to 0,
                NotificationType.ESCALATION to 1,
                NotificationType.ESCALATION to 2,
            )
        assertThat(saved.map { it.title })
            .containsExactly("약 미확인", "약 미확인 1차 알림", "약 미확인 2차 알림")
        assertThat(saved.first().body).isEqualTo("아침약 · 1번 봉지 복용이 아직 확인되지 않았어요.")
        assertThat(saved.last().body).isEqualTo("아침약 · 1번 봉지 복용이 여전히 확인되지 않았어요. (2차 재알림)")
        assertThat(saved.all { it.careGroupId == GROUP_ID && it.seniorId == SENIOR_ID }).isTrue()
        assertThat(saved.all { it.createdAt == OffsetDateTime.now(clock) }).isTrue()
        assertThat(sender.phones).containsExactly(CAREGIVER_PHONE, CAREGIVER_PHONE, CAREGIVER_PHONE)
        assertThat(saved.all { it.dispatchedAt == OffsetDateTime.now(clock) }).isTrue()
        assertThat(saved.map { it.dispatchChannel }.distinct()).containsExactly("STUB")
    }

    @Test
    fun `zero max retries records only the MISSED notification`() {
        val event = overdueEvent()
        val now = SCHEDULED_AT.plusHours(2)
        givenOverdue(event, now)
        givenCareGroup()
        givenPrimaryCaregiver()
        givenSaveSucceeds()
        `when`(notificationSettings.findBySeniorId(SENIOR_ID))
            .thenReturn(NotificationSettingsEntity(enabled = true, remindIntervalMin = 5, maxRetries = 0))

        val created = evaluator.evaluate(now)

        assertThat(created).isEqualTo(1)
        assertThat(saved.map { it.type to it.level }).containsExactly(NotificationType.MISSED to 0)
    }

    @Test
    fun `rung inserted concurrently is neither counted nor dispatched`() {
        val event = overdueEvent()
        givenOverdue(event, NOW)
        givenCareGroup()
        givenPrimaryCaregiver()
        `when`(notifications.save(any(NotificationEntity::class.java)))
            .thenThrow(DataIntegrityViolationException("duplicate (dose_event_id, type, level)"))

        val created = evaluator.evaluate(NOW)

        assertThat(created).isZero()
        assertThat(event.status).isEqualTo(DoseEventStatus.MISSED)
        assertThat(sender.phones).isEmpty()
    }

    @Test
    fun `notification is recorded without dispatch when no caregiver is eligible`() {
        val event = overdueEvent()
        givenOverdue(event, NOW)
        givenCareGroup()
        givenSaveSucceeds()
        val removedOwner =
            CareGroupMemberEntity(
                id = 1,
                user = UserEntity(id = 20, name = "Removed owner", phone = CAREGIVER_PHONE),
                role = CareGroupRole.OWNER,
                status = MemberStatus.REMOVED,
            )
        `when`(members.findByCareGroupIdAndIsPrimaryTrue(GROUP_ID)).thenReturn(null)
        `when`(members.findByCareGroupIdOrderByIdAsc(GROUP_ID)).thenReturn(listOf(removedOwner))

        val created = evaluator.evaluate(NOW)

        assertThat(created).isEqualTo(3)
        assertThat(sender.phones).isEmpty()
        assertThat(saved.map { it.dispatchedAt }).containsOnlyNulls()
        assertThat(saved.map { it.dispatchTarget }).containsOnlyNulls()
    }

    private fun overdueEvent(): DoseEventEntity =
        DoseEventEntity(
            id = 100,
            doseSchedule = DoseScheduleEntity(label = "아침약", packetNo = 1),
            senior = UserEntity(id = SENIOR_ID),
            scheduledAt = SCHEDULED_AT,
            status = DoseEventStatus.SCHEDULED,
        )

    private fun givenOverdue(
        event: DoseEventEntity,
        now: OffsetDateTime,
    ) {
        `when`(doseEvents.findOverdueOpen(now)).thenReturn(listOf(event))
    }

    private fun givenCareGroup() {
        `when`(careGroups.findBySeniorId(SENIOR_ID)).thenReturn(CareGroupEntity(id = GROUP_ID))
    }

    private fun givenPrimaryCaregiver() {
        val primary =
            CareGroupMemberEntity(
                id = 1,
                user = UserEntity(id = 20, name = "Primary", phone = CAREGIVER_PHONE),
                role = CareGroupRole.OWNER,
                status = MemberStatus.ACTIVE,
                isPrimary = true,
            )
        `when`(members.findByCareGroupIdAndIsPrimaryTrue(GROUP_ID)).thenReturn(primary)
    }

    private fun givenSaveSucceeds() {
        `when`(notifications.save(any(NotificationEntity::class.java))).thenAnswer { invocation ->
            invocation.getArgument<NotificationEntity>(0).also { saved += it }
        }
    }

    private class RecordingSender : NotificationSender {
        val phones = mutableListOf<String>()

        override fun dispatch(
            targetPhone: String,
            targetName: String,
            notification: NotificationEntity,
        ): DispatchResult {
            phones += targetPhone
            return DispatchResult(
                success = true,
                channel = DispatchChannel.STUB,
                target = DispatchRenderer.renderTarget(targetPhone),
            )
        }
    }

    private companion object {
        const val SENIOR_ID = 10L
        const val GROUP_ID = 30L
        const val CAREGIVER_PHONE = "010-1234-5678"
        val SCHEDULED_AT: OffsetDateTime = OffsetDateTime.parse("2026-07-02T08:30:00+09:00")
        val NOW: OffsetDateTime = SCHEDULED_AT.plusMinutes(12)
    }
}
