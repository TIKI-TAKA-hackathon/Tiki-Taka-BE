package xyz.stdiodh.gojjibom.notification

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import xyz.stdiodh.gojjibom.caregroup.UserEntity
import xyz.stdiodh.gojjibom.caregroup.UserRepository
import xyz.stdiodh.gojjibom.caregroup.UserType
import xyz.stdiodh.gojjibom.dose.DoseEventGenerator
import xyz.stdiodh.gojjibom.dose.DoseEventRepository
import xyz.stdiodh.gojjibom.dose.DoseEventStatus
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

/**
 * WP3b: verifies MISSED/ESCALATION notifications are dispatched to the care group's
 * caregiver and the delivery outcome is persisted, idempotently. A recording
 * [NotificationSender] replaces the real stub so we can assert what was sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CaregiverDispatchIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        private val suffixSequence = AtomicLong()

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }

    /** A recording sender that captures every dispatch, replacing the production stub. */
    class RecordingNotificationSender : NotificationSender {
        data class Sent(
            val phone: String,
            val name: String,
            val notificationId: Long?,
            val type: NotificationType,
        )

        val sent = mutableListOf<Sent>()

        override fun dispatch(
            targetPhone: String,
            targetName: String,
            notification: NotificationEntity,
        ): DispatchResult {
            sent += Sent(targetPhone, targetName, notification.id, notification.type)
            return DispatchResult(
                success = true,
                channel = DispatchChannel.STUB,
                target = DispatchRenderer.renderTarget(targetPhone),
            )
        }
    }

    @TestConfiguration
    class RecordingSenderConfig {
        @Bean
        @Primary
        fun recordingSender(): RecordingNotificationSender = RecordingNotificationSender()
    }

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var generator: DoseEventGenerator

    @Autowired private lateinit var doseEvents: DoseEventRepository

    @Autowired private lateinit var notifications: NotificationRepository

    @Autowired private lateinit var evaluator: MissedDoseEvaluator

    @Autowired private lateinit var recordingSender: RecordingNotificationSender

    private val objectMapper = ObjectMapper()

    private val doseDate = "2026-07-02"
    private val scheduledAt = OffsetDateTime.of(2026, 7, 2, 8, 30, 0, 0, ZoneOffset.ofHours(9))

    @Test
    fun `escalation dispatches to the primary caregiver and stamps dispatch fields`() {
        val group = createGroupWithDoseEvent(remindIntervalMin = 5, maxRetries = 3)
        generateEventId(group.seniorUserId)

        // now = +12 min → MISSED(0) + escalation 1 + escalation 2 created and dispatched.
        // evaluate() is global, so we scope assertions to this group's owner/senior.
        evaluator.evaluate(scheduledAt.plusMinutes(12))

        val rows = notifications.findBySeniorIdOrderByCreatedAtDescIdDesc(group.seniorUserId)
        assertThat(rows).hasSize(3)
        assertThat(rows).allSatisfy { row ->
            assertThat(row.dispatchedAt).isNotNull()
            assertThat(row.dispatchTarget).isNotNull()
            assertThat(row.dispatchChannel).isEqualTo("STUB")
        }

        // Every MISSED/ESCALATION row was dispatched exactly once, to the owner (primary).
        val owner = users.findById(group.ownerUserId).orElseThrow()
        val sentToOwner = sentTo(owner.phone)
        assertThat(sentToOwner).hasSize(3)
        assertThat(sentToOwner).allSatisfy {
            assertThat(it.name).isEqualTo(owner.name)
        }
    }

    @Test
    fun `dispatch is idempotent — re-running the evaluator does not re-send`() {
        val group = createGroupWithDoseEvent(remindIntervalMin = 5, maxRetries = 3)
        generateEventId(group.seniorUserId)
        val ownerPhone = users.findById(group.ownerUserId).orElseThrow().phone

        evaluator.evaluate(scheduledAt.plusMinutes(12))
        val afterFirst = sentTo(ownerPhone).size
        assertThat(afterFirst).isEqualTo(3)

        val stampsAfterFirst =
            notifications
                .findBySeniorIdOrderByCreatedAtDescIdDesc(group.seniorUserId)
                .associate { it.requiredId() to it.dispatchedAt }

        // Re-run at the same clock → no new rows, no new dispatch, stamps unchanged.
        evaluator.evaluate(scheduledAt.plusMinutes(12))
        assertThat(sentTo(ownerPhone)).hasSize(afterFirst)
        notifications.findBySeniorIdOrderByCreatedAtDescIdDesc(group.seniorUserId).forEach {
            assertThat(it.dispatchedAt).isEqualTo(stampsAfterFirst[it.requiredId()])
        }

        // Advance past the 3rd rung → exactly one new dispatch (escalation level 3) for this group.
        evaluator.evaluate(scheduledAt.plusMinutes(20))
        assertThat(sentTo(ownerPhone)).hasSize(afterFirst + 1)
    }

    @Test
    fun `disabled settings dispatch nothing and leave the event SCHEDULED`() {
        val group = createGroupWithDoseEvent(enabled = false, remindIntervalMin = 5, maxRetries = 3)
        val eventId = generateEventId(group.seniorUserId)
        val ownerPhone = users.findById(group.ownerUserId).orElseThrow().phone

        evaluator.evaluate(scheduledAt.plusMinutes(30))

        assertThat(sentTo(ownerPhone)).isEmpty()
        assertThat(notifications.findBySeniorIdOrderByCreatedAtDescIdDesc(group.seniorUserId)).isEmpty()
        assertThat(doseEvents.findById(eventId).orElseThrow().status).isEqualTo(DoseEventStatus.SCHEDULED)
    }

    /** Dispatches recorded for a specific caregiver phone (evaluate() is global across groups). */
    private fun sentTo(phone: String) = recordingSender.sent.filter { it.phone == phone }

    private fun generateEventId(seniorUserId: Long): Long {
        val events = generator.ensureEventsFor(seniorUserId, LocalDate.parse(doseDate))
        return events.single().id ?: error("dose event id not assigned")
    }

    private fun createGroupWithDoseEvent(
        enabled: Boolean = true,
        remindIntervalMin: Int,
        maxRetries: Int,
    ): CreatedGroup {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId)
        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/notification-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "actorUserId": ${group.ownerUserId},
                          "enabled": $enabled,
                          "remindIntervalMin": $remindIntervalMin,
                          "maxRetries": $maxRetries
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isOk)
        return group
    }

    private fun createCareGroup(): CreatedGroup {
        val suffix = suffixSequence.incrementAndGet()
        val result =
            mockMvc
                .perform(
                    post("/api/v1/care-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "name": "Care group $suffix",
                              "senior": {
                                "name": "Senior $suffix",
                                "phone": "010-disp-s$suffix",
                                "birthDate": "1940-01-01"
                              },
                              "owner": {
                                "name": "Owner $suffix",
                                "phone": "010-disp-o$suffix"
                              }
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()

        val data = objectMapper.readTree(result.response.contentAsString).path("data")
        val ownerMember = data.path("members").path(0)
        return CreatedGroup(
            groupId = data.path("id").asLong(),
            seniorUserId = data.path("senior").path("id").asLong(),
            ownerUserId = ownerMember.path("user").path("id").asLong(),
        )
    }

    private fun registerPrescription(seniorUserId: Long) {
        val pharmacistUserId =
            users
                .save(
                    UserEntity(
                        userType = UserType.PHARMACIST,
                        name = "Pharmacist ${suffixSequence.incrementAndGet()}",
                        phone = "010-disp-ph${suffixSequence.incrementAndGet()}",
                    ),
                ).id ?: error("pharmacist id not assigned")

        mockMvc
            .perform(
                post("/api/v1/seniors/$seniorUserId/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "pharmacistUserId": $pharmacistUserId,
                          "pharmacy": { "name": "Gojjibom Pharmacy", "phone": "02-0000-0000" },
                          "prescribedDate": "$doseDate",
                          "startDate": "$doseDate",
                          "endDate": "2026-07-16",
                          "dispensingType": "POUCH",
                          "schedules": [
                            {
                              "slot": "MORNING",
                              "label": "아침약",
                              "packetNo": 1,
                              "scheduledTime": "08:30",
                              "mealRelation": "AFTER_MEAL",
                              "mealOffsetMin": 30,
                              "pillCount": 1,
                              "items": [
                                { "medicationName": "Blood pressure pill", "category": "Blood pressure", "count": 1 }
                              ]
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
    }

    private data class CreatedGroup(
        val groupId: Long,
        val seniorUserId: Long,
        val ownerUserId: Long,
    )
}
