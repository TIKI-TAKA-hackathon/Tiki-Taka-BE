package xyz.stdiodh.gojjibom.notification

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import xyz.stdiodh.gojjibom.caregroup.UserEntity
import xyz.stdiodh.gojjibom.caregroup.UserRepository
import xyz.stdiodh.gojjibom.caregroup.UserType
import xyz.stdiodh.gojjibom.dose.DoseEventGenerator
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class NotificationIntegrationTest {
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

    @Autowired private lateinit var evaluator: MissedDoseEvaluator

    private val objectMapper = ObjectMapper()

    private val doseDate = "2026-07-02"
    private val scheduledAt = OffsetDateTime.of(2026, 7, 2, 8, 30, 0, 0, ZoneOffset.ofHours(9))

    @Test
    fun `senior notifications require active membership`() {
        val group = seededGroup()
        val outsiderId = registerUser("out")

        mockMvc
            .perform(
                get("/api/v1/seniors/${group.seniorUserId}/notifications")
                    .param("actorUserId", outsiderId.toString()),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_MEMBER_REQUIRED"))
    }

    @Test
    fun `senior notifications list is newest first with lowercase type and string ids`() {
        val group = seededGroup()

        mockMvc
            .perform(
                get("/api/v1/seniors/${group.seniorUserId}/notifications")
                    .param("actorUserId", group.ownerUserId.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.notifications.length()").value(3))
            .andExpect(jsonPath("$.data.notifications[0].id").isString)
            .andExpect(jsonPath("$.data.notifications[0].type").value("escalation"))
            .andExpect(jsonPath("$.data.notifications[0].level").value(2))
            .andExpect(jsonPath("$.data.notifications[2].type").value("missed"))
            .andExpect(jsonPath("$.data.notifications[0].doseEventId").isString)
    }

    @Test
    fun `care group notifications list is newest first`() {
        val group = seededGroup()

        mockMvc
            .perform(get("/api/v1/care-groups/${group.groupId}/notifications"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.notifications.length()").value(3))
            .andExpect(jsonPath("$.data.notifications[0].type").value("escalation"))
            .andExpect(jsonPath("$.data.notifications[0].level").value(2))
    }

    @Test
    fun `mark read sets read_at and requires membership`() {
        val group = seededGroup()
        val notificationId = latestNotificationId(group)

        // Outsider cannot mark read.
        val outsiderId = registerUser("mrout")
        mockMvc
            .perform(
                patch("/api/v1/notifications/$notificationId:read")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": $outsiderId}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_MEMBER_REQUIRED"))

        // Active member marks read.
        mockMvc
            .perform(
                patch("/api/v1/notifications/$notificationId:read")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.readAt").isNotEmpty)
    }

    @Test
    fun `caregiver board alert reflects the latest unresolved escalation`() {
        val group = seededGroup()

        mockMvc
            .perform(get("/api/v1/care-groups/${group.groupId}/board").param("date", doseDate))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.alert.doseLabel").value("아침약 · 1번 봉지"))
            .andExpect(jsonPath("$.data.alert.lastAlarm").value("오전 8:30"))
            .andExpect(jsonPath("$.data.alert.retries").value(2))
            .andExpect(jsonPath("$.data.alert.steps.length()").value(3))
    }

    /** Group with a prescription, a generated dose event, and 3 notifications (MISSED + 2 escalations). */
    private fun seededGroup(): CreatedGroup {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId)
        updateSettings(group, remindIntervalMin = 5, maxRetries = 3)
        generator.ensureEventsFor(group.seniorUserId, LocalDate.parse(doseDate))
        evaluator.evaluate(scheduledAt.plusMinutes(12))
        return group
    }

    private fun latestNotificationId(group: CreatedGroup): Long {
        val data =
            mockMvc
                .perform(get("/api/v1/care-groups/${group.groupId}/notifications"))
                .andExpect(status().isOk)
                .andReturn()
        return objectMapper
            .readTree(data.response.contentAsString)
            .path("data")
            .path("notifications")
            .path(0)
            .path("id")
            .asLong()
    }

    private fun updateSettings(
        group: CreatedGroup,
        remindIntervalMin: Int,
        maxRetries: Int,
    ) {
        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/notification-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "actorUserId": ${group.ownerUserId},
                          "enabled": true,
                          "remindIntervalMin": $remindIntervalMin,
                          "maxRetries": $maxRetries
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isOk)
    }

    private fun registerUser(tag: String): Long {
        val n = suffixSequence.incrementAndGet()
        return users
            .save(
                UserEntity(
                    userType = UserType.CAREGIVER,
                    name = "$tag $n",
                    phone = "010-notif-$tag$n",
                ),
            ).id ?: error("user id not assigned")
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
                                "phone": "010-notif-s$suffix",
                                "birthDate": "1940-01-01"
                              },
                              "owner": {
                                "name": "Owner $suffix",
                                "phone": "010-notif-o$suffix"
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
                        phone = "010-notif-ph${suffixSequence.incrementAndGet()}",
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
