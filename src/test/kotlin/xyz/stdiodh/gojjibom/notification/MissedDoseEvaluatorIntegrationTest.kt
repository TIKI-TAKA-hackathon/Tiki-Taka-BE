package xyz.stdiodh.gojjibom.notification

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class MissedDoseEvaluatorIntegrationTest {
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

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var generator: DoseEventGenerator

    @Autowired private lateinit var doseEvents: DoseEventRepository

    @Autowired private lateinit var notifications: NotificationRepository

    @Autowired private lateinit var evaluator: MissedDoseEvaluator

    private val objectMapper = ObjectMapper()

    private val doseDate = "2026-07-02"

    // scheduledTime 08:30 Asia/Seoul → 2026-07-02T08:30+09:00.
    private val scheduledAt = OffsetDateTime.of(2026, 7, 2, 8, 30, 0, 0, ZoneOffset.ofHours(9))

    @Test
    fun `evaluate transitions overdue event to MISSED and inserts escalation rungs per cadence`() {
        val group = createGroupWithDoseEvent(remindIntervalMin = 5, maxRetries = 3)
        val eventId = generateEventId(group.seniorUserId)

        // now = scheduledAt + 12 min → grace(0) passed; rungs at +5 and +10 due, +15 not yet.
        // evaluate() is global, so we assert on this senior's rows rather than the return count.
        evaluator.evaluate(scheduledAt.plusMinutes(12))

        val event = doseEvents.findById(eventId).orElseThrow()
        assertThat(event.status).isEqualTo(DoseEventStatus.MISSED)

        val rows = notifications.findBySeniorIdOrderByCreatedAtDescIdDesc(group.seniorUserId)
        assertThat(rows.map { it.type to it.level })
            .containsExactlyInAnyOrder(
                NotificationType.MISSED to 0,
                NotificationType.ESCALATION to 1,
                NotificationType.ESCALATION to 2,
            )
        assertThat(rows.all { it.title.contains("약 미확인") || it.body.contains("확인") }).isTrue()
    }

    @Test
    fun `evaluate is idempotent on re-run and only adds newly-due rungs`() {
        val group = createGroupWithDoseEvent(remindIntervalMin = 5, maxRetries = 3)
        generateEventId(group.seniorUserId)

        evaluator.evaluate(scheduledAt.plusMinutes(12))
        val afterFirst = notifications.findBySeniorIdOrderByCreatedAtDescIdDesc(group.seniorUserId)
        // MISSED(0) + escalation 1 + escalation 2 (rung 3 at +15 not yet due).
        assertThat(afterFirst.map { it.type to it.level })
            .containsExactlyInAnyOrder(
                NotificationType.MISSED to 0,
                NotificationType.ESCALATION to 1,
                NotificationType.ESCALATION to 2,
            )

        // Re-run at the same clock → the UNIQUE guard + pre-check add nothing for this senior.
        evaluator.evaluate(scheduledAt.plusMinutes(12))
        assertThat(notifications.findBySeniorIdOrderByCreatedAtDescIdDesc(group.seniorUserId))
            .hasSameSizeAs(afterFirst)

        // Advance past the 3rd rung → exactly one new escalation (level 3) for this senior.
        evaluator.evaluate(scheduledAt.plusMinutes(20))
        val afterThird = notifications.findBySeniorIdOrderByCreatedAtDescIdDesc(group.seniorUserId)
        assertThat(afterThird).hasSize(4)
        assertThat(afterThird.count { it.type == NotificationType.ESCALATION }).isEqualTo(3)
    }

    @Test
    fun `disabled notification settings produce no notifications and no MISSED transition`() {
        val group = createGroupWithDoseEvent(enabled = false, remindIntervalMin = 5, maxRetries = 3)
        val eventId = generateEventId(group.seniorUserId)

        val created = evaluator.evaluate(scheduledAt.plusMinutes(30))

        assertThat(created).isEqualTo(0)
        assertThat(notifications.findBySeniorIdOrderByCreatedAtDescIdDesc(group.seniorUserId)).isEmpty()
        assertThat(doseEvents.findById(eventId).orElseThrow().status).isEqualTo(DoseEventStatus.SCHEDULED)
    }

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
                                "phone": "010-eval-s$suffix",
                                "birthDate": "1940-01-01"
                              },
                              "owner": {
                                "name": "Owner $suffix",
                                "phone": "010-eval-o$suffix"
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
                        phone = "010-eval-ph${suffixSequence.incrementAndGet()}",
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
