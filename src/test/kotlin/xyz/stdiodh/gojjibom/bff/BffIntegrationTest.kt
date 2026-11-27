package xyz.stdiodh.gojjibom.bff

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import xyz.stdiodh.gojjibom.caregroup.domain.UserEntity
import xyz.stdiodh.gojjibom.caregroup.domain.UserRepository
import xyz.stdiodh.gojjibom.caregroup.domain.UserType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(BffIntegrationTest.FixedClockConfig::class)
class BffIntegrationTest {
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

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()

    private val doseDate = "2026-07-02"

    @Test
    fun `senior today returns FE-shaped payload with lowercase status and string ids`() {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId)

        mockMvc
            .perform(
                get("/api/v1/senior/today")
                    .param("seniorId", group.seniorUserId.toString())
                    .param("date", doseDate),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.dateLabel").value("2026년 7월 2일 목요일"))
            .andExpect(jsonPath("$.data.doses.length()").value(1))
            .andExpect(jsonPath("$.data.doses[0].id").isString)
            .andExpect(jsonPath("$.data.doses[0].status").value("upcoming"))
            .andExpect(jsonPath("$.data.doses[0].time").value("08:30"))
            .andExpect(jsonPath("$.data.nextDose.doseId").isString)
            .andExpect(jsonPath("$.data.nextDose.alarmLabel").value("오전 8:30"))
            .andExpect(jsonPath("$.data.nextDose.dispensingType").value("pouch"))
            .andExpect(jsonPath("$.data.nextDose.pills[0].shape").value("round"))
    }

    @Test
    fun `senior today has no next dose when there is no active schedule`() {
        val group = createCareGroup()

        mockMvc
            .perform(
                get("/api/v1/senior/today")
                    .param("seniorId", group.seniorUserId.toString())
                    .param("date", doseDate),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nextDose").doesNotExist())
            .andExpect(jsonPath("$.data.doses.length()").value(0))
    }

    @Test
    fun `dose status copy distinguishes taken scheduled missed and skipped`() {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId)
        val eventId = generateDoseEventIds(group.seniorUserId, doseDate).single()

        assertDoseCopy(group.seniorUserId, "upcoming", "잠시 후 알림이 와요")

        updateDoseStatus(eventId, "MISSED")
        assertDoseCopy(group.seniorUserId, "missed", "아직 확인되지 않았어요")

        updateDoseStatus(eventId, "SKIPPED")
        assertDoseCopy(group.seniorUserId, "missed", "아직 확인되지 않았어요")

        jdbcTemplate.update(
            """
            UPDATE dose_events
            SET status = 'TAKEN',
                confirmed_at = TIMESTAMPTZ '2026-07-02 10:15:00+09',
                confirm_method = 'BUTTON',
                confirmed_by = ?
            WHERE id = ?
            """.trimIndent(),
            group.ownerUserId,
            eventId,
        )
        assertDoseCopy(group.seniorUserId, "done", "1개 모두 드셨어요")
    }

    @Test
    fun `next dose omits meal baseline and category note when source values are absent`() {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId)
        jdbcTemplate.update("UPDATE medications SET category = NULL WHERE name = 'Blood pressure pill'")

        mockMvc
            .perform(
                get("/api/v1/senior/today")
                    .param("seniorId", group.seniorUserId.toString())
                    .param("date", doseDate),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nextDose.baselineNote").value(""))
            .andExpect(jsonPath("$.data.nextDose.includesNote").value(""))
    }

    @Test
    fun `caregiver board returns FE-shaped payload with patient name and circle`() {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId)

        mockMvc
            .perform(
                get("/api/v1/care-groups/${group.groupId}/board")
                    .param("date", doseDate),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.patientName").isString)
            .andExpect(jsonPath("$.data.circle.family").value(1))
            .andExpect(jsonPath("$.data.circle.social").value(0))
            .andExpect(jsonPath("$.data.doses.length()").value(1))
            .andExpect(jsonPath("$.data.doses[0].status").value("upcoming"))
            .andExpect(jsonPath("$.data.confirmations.length()").value(0))
            .andExpect(jsonPath("$.data.week.length()").value(7))
            .andExpect(jsonPath("$.data.alert").doesNotExist())
    }

    @Test
    fun `caregiver board folds empty warned all-taken and mixed week days`() {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId, startDate = "2026-06-01")
        registerPrescription(group.seniorUserId, startDate = "2026-06-01")
        val target = LocalDate.parse(doseDate)
        val warned = target.minusDays(5)
        val allTaken = target.minusDays(4)
        val mixed = target.minusDays(3)

        generateDoseEventIds(group.seniorUserId, warned.toString())
        generateDoseEventIds(group.seniorUserId, allTaken.toString())
        val mixedIds = generateDoseEventIds(group.seniorUserId, mixed.toString())
        jdbcTemplate.update(
            "UPDATE dose_events SET status = 'MISSED' WHERE senior_id = ? AND scheduled_date = ?",
            group.seniorUserId,
            warned,
        )
        markDateTaken(group, allTaken)
        markEventTaken(group, mixedIds.first())

        mockMvc
            .perform(get("/api/v1/care-groups/${group.groupId}/board").param("date", doseDate))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.week[0].status").value("none"))
            .andExpect(jsonPath("$.data.week[1].status").value("warn"))
            .andExpect(jsonPath("$.data.week[2].status").value("done"))
            .andExpect(jsonPath("$.data.week[3].status").value("none"))
    }

    @Test
    fun `caregiver board adds fixed confirmation time and method after confirmation`() {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId)
        val eventId = generateDoseEventIds(group.seniorUserId, doseDate).single()

        mockMvc
            .perform(get("/api/v1/care-groups/${group.groupId}/board").param("date", doseDate))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.confirmations.length()").value(0))

        mockMvc
            .perform(
                post("/api/v1/dose-events/$eventId:confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "method": "CAREGIVER"}"""),
            ).andExpect(status().isOk)

        mockMvc
            .perform(get("/api/v1/care-groups/${group.groupId}/board").param("date", doseDate))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.doses[0].time").value("08:30"))
            .andExpect(jsonPath("$.data.confirmations.length()").value(1))
            .andExpect(jsonPath("$.data.confirmations[0].detail").value("오전 10:15 · 보호자 확인"))
    }

    @Test
    fun `caregiver board returns 404 for unknown group`() {
        mockMvc
            .perform(get("/api/v1/care-groups/999999/board").param("date", doseDate))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_NOT_FOUND"))
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
                                "phone": "010-bff-s$suffix",
                                "birthDate": "1940-01-01"
                              },
                              "owner": {
                                "name": "Owner $suffix",
                                "phone": "010-bff-o$suffix"
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

    private fun registerPrescription(
        seniorUserId: Long,
        startDate: String = doseDate,
    ) {
        val pharmacistUserId =
            users
                .save(
                    UserEntity(
                        userType = UserType.PHARMACIST,
                        name = "Pharmacist ${suffixSequence.incrementAndGet()}",
                        phone = "010-bff-ph${suffixSequence.incrementAndGet()}",
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
                          "pharmacy": {
                            "name": "Gojjibom Pharmacy",
                            "phone": "02-0000-0000"
                          },
                          "prescribedDate": "$startDate",
                          "startDate": "$startDate",
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
                                {
                                  "medicationName": "Blood pressure pill",
                                  "category": "Blood pressure",
                                  "count": 1
                                }
                              ]
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
    }

    private fun generateDoseEventIds(
        seniorUserId: Long,
        date: String,
    ): List<Long> {
        val result =
            mockMvc
                .perform(
                    get("/api/v1/senior/today")
                        .param("seniorId", seniorUserId.toString())
                        .param("date", date),
                ).andExpect(status().isOk)
                .andReturn()
        return objectMapper
            .readTree(result.response.contentAsString)
            .path("data")
            .path("doses")
            .map { it.path("id").asLong() }
    }

    private fun assertDoseCopy(
        seniorUserId: Long,
        expectedStatus: String,
        expectedNote: String,
    ) {
        mockMvc
            .perform(
                get("/api/v1/senior/today")
                    .param("seniorId", seniorUserId.toString())
                    .param("date", doseDate),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.doses[0].status").value(expectedStatus))
            .andExpect(jsonPath("$.data.doses[0].note").value(expectedNote))
    }

    private fun updateDoseStatus(
        eventId: Long,
        status: String,
    ) {
        jdbcTemplate.update(
            """
            UPDATE dose_events
            SET status = ?,
                confirmed_at = NULL,
                confirm_method = NULL,
                confirmed_by = NULL
            WHERE id = ?
            """.trimIndent(),
            status,
            eventId,
        )
    }

    private fun markDateTaken(
        group: CreatedGroup,
        date: LocalDate,
    ) {
        jdbcTemplate.update(
            """
            UPDATE dose_events
            SET status = 'TAKEN',
                confirmed_at = TIMESTAMPTZ '2026-07-02 10:15:00+09',
                confirm_method = 'BUTTON',
                confirmed_by = ?
            WHERE senior_id = ? AND scheduled_date = ?
            """.trimIndent(),
            group.ownerUserId,
            group.seniorUserId,
            date,
        )
    }

    private fun markEventTaken(
        group: CreatedGroup,
        eventId: Long,
    ) {
        jdbcTemplate.update(
            """
            UPDATE dose_events
            SET status = 'TAKEN',
                confirmed_at = TIMESTAMPTZ '2026-07-02 10:15:00+09',
                confirm_method = 'BUTTON',
                confirmed_by = ?
            WHERE id = ?
            """.trimIndent(),
            group.ownerUserId,
            eventId,
        )
    }

    private data class CreatedGroup(
        val groupId: Long,
        val seniorUserId: Long,
        val ownerUserId: Long,
    )

    @TestConfiguration(proxyBeanMethods = false)
    class FixedClockConfig {
        @Bean
        @Primary
        fun fixedClock(): Clock =
            Clock.fixed(
                Instant.parse("2026-07-02T01:15:00Z"),
                ZoneId.of("Asia/Seoul"),
            )
    }
}
