package xyz.stdiodh.gojjibom.prescription

import com.fasterxml.jackson.databind.JsonNode
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import xyz.stdiodh.gojjibom.caregroup.UserEntity
import xyz.stdiodh.gojjibom.caregroup.UserRepository
import xyz.stdiodh.gojjibom.caregroup.UserType
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PrescriptionRegistrationIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        private val phoneSequence = AtomicLong()

        private const val UNKNOWN_ID = 999_999_999L

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
    private lateinit var doseSchedules: DoseScheduleRepository

    private val objectMapper = ObjectMapper()

    @Test
    fun `prescription target must be an existing senior with a care group`() {
        val group = createCareGroup("rx-target")
        val pharmacistUserId = createUser("rx-target-pharmacist", UserType.PHARMACIST)
        val seniorWithoutGroup = createUser("rx-target-lonely-senior", UserType.SENIOR)
        val body = prescriptionBody(pharmacistUserId, pillCount = 3)

        mockMvc
            .perform(
                post("/api/v1/seniors/$UNKNOWN_ID/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("SENIOR_NOT_FOUND"))

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.ownerUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("SENIOR_REQUIRED"))

        mockMvc
            .perform(
                post("/api/v1/seniors/$seniorWithoutGroup/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_REQUIRED"))
    }

    @Test
    fun `unknown pharmacist cannot register prescription`() {
        val group = createCareGroup("rx-no-pharmacist")

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(prescriptionBody(UNKNOWN_ID, pillCount = 3)),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("PHARMACIST_NOT_FOUND"))
    }

    @Test
    fun `end date before start date is rejected and a same-day prescription is accepted`() {
        val group = createCareGroup("rx-dates")
        val pharmacistUserId = createUser("rx-dates-pharmacist", UserType.PHARMACIST)

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(singleScheduleBody(pharmacistUserId, startDate = "2026-07-02", endDate = "2026-07-01")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"))

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(singleScheduleBody(pharmacistUserId, startDate = "2026-07-02", endDate = "2026-07-02")),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.endDate").value("2026-07-02"))
    }

    @Test
    fun `registration normalizes blank optional fields and derives pill count and dose basis`() {
        val group = createCareGroup("rx-defaults")
        val pharmacistUserId = createUser("rx-defaults-pharmacist", UserType.PHARMACIST)
        val explicitSchedule =
            scheduleJson("EXPLICIT", "DINNER", "18:30", "AFTER_MEAL", "Defaults explicit", doseBasis = "EMPTY_STOMACH")

        val data =
            mockMvc
                .perform(
                    post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "pharmacistUserId": $pharmacistUserId,
                              "pharmacy": { "name": "Defaults Pharmacy", "phone": "02-1111-0000", "address": "  " },
                              "prescribedDate": "2026-07-02",
                              "startDate": "2026-07-02",
                              "endDate": null,
                              "dispensingType": "ORGANIZER",
                              "registrationCode": "   ",
                              "schedules": [
                                ${scheduleJson("BEFORE", "MORNING", "07:30", "BEFORE_MEAL", "Defaults before")},
                                ${scheduleJson("WITH", "LUNCH", "12:30", "WITH_MEAL", "Defaults with")},
                                ${scheduleJson("BEDTIME", "BEDTIME", "22:00", "NONE", "Defaults bedtime")},
                                ${scheduleJson("CUSTOM", "CUSTOM", "15:00", "NONE", "Defaults custom")},
                                $explicitSchedule
                              ]
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()
                .dataNode()

        assertThat(data.path("dispensingType").asText()).isEqualTo("ORGANIZER")
        assertThat(data.path("registrationCode").isNull).isTrue()
        assertThat(data.path("endDate").isNull).isTrue()
        assertThat(data.path("pharmacy").path("address").isNull).isTrue()

        val schedules = data.path("schedules").associateBy { it.path("label").asText() }
        assertThat(schedules.mapValues { it.value.path("doseBasis").asText() })
            .containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    "BEFORE" to "BEFORE_MEAL",
                    "WITH" to "AFTER_MEAL",
                    "BEDTIME" to "BEDTIME",
                    "CUSTOM" to "FIXED",
                    "EXPLICIT" to "EMPTY_STOMACH",
                ),
            )
        val before = schedules.getValue("BEFORE")
        // pillCount omitted -> sum of item counts (2 + 1).
        assertThat(before.path("pillCount").asInt()).isEqualTo(3)
        val firstItem = before.path("items").path(0)
        assertThat(firstItem.path("category").isNull).isTrue()
        assertThat(firstItem.path("description").isNull).isTrue()
        assertThat(firstItem.path("shape").asText()).isEqualTo("CAPSULE")
        val secondItem = before.path("items").path(1)
        assertThat(secondItem.path("shape").asText()).isEqualTo("ROUND")
    }

    @Test
    fun `blank and missing categories count as the same medication within a schedule`() {
        val group = createCareGroup("rx-blank-category")
        val pharmacistUserId = createUser("rx-blank-category-pharmacist", UserType.PHARMACIST)

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "pharmacistUserId": $pharmacistUserId,
                          "pharmacy": { "name": "Gojjibom Pharmacy", "phone": "02-0000-0000" },
                          "prescribedDate": "2026-07-02",
                          "startDate": "2026-07-02",
                          "endDate": "2026-07-16",
                          "schedules": [
                            {
                              "slot": "MORNING",
                              "label": "Morning packet",
                              "scheduledTime": "08:30",
                              "mealRelation": "AFTER_MEAL",
                              "items": [
                                { "medicationName": "Vitamin", "category": "  ", "count": 1 },
                                { "medicationName": " Vitamin ", "category": null, "count": 1 }
                              ]
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_SCHEDULE_ITEM"))
    }

    @Test
    fun `pharmacy and medication rows are reused across registrations`() {
        val group = createCareGroup("rx-reuse")
        val pharmacistUserId = createUser("rx-reuse-pharmacist", UserType.PHARMACIST)

        val first = registerPrescription(group.seniorUserId, prescriptionBody(pharmacistUserId, pillCount = 3))
        val second = registerPrescription(group.seniorUserId, prescriptionBody(pharmacistUserId, pillCount = 3))

        assertThat(second.path("id").asLong()).isNotEqualTo(first.path("id").asLong())
        assertThat(second.path("pharmacy").path("id").asLong()).isEqualTo(first.path("pharmacy").path("id").asLong())
        assertThat(medicationIds(second)).isEqualTo(medicationIds(first))
    }

    @Test
    fun `confirm-meds lookup lists only active schedules in time order for an open-ended prescription`() {
        val group = createCareGroup("rx-lookup-order")
        val pharmacistUserId = createUser("rx-lookup-order-pharmacist", UserType.PHARMACIST)
        val code = "RX-ORDER-${phoneSequence.incrementAndGet()}"
        val created =
            registerPrescription(
                group.seniorUserId,
                """
                {
                  "pharmacistUserId": $pharmacistUserId,
                  "pharmacy": { "name": "Gojjibom Pharmacy", "phone": "02-0000-0000" },
                  "prescribedDate": "2026-07-02",
                  "startDate": "2026-07-02",
                  "endDate": null,
                  "registrationCode": " $code ",
                  "schedules": [
                    ${scheduleJson("Evening", "DINNER", "19:00", "AFTER_MEAL", "Lookup evening")},
                    ${scheduleJson("Retired", "LUNCH", "12:00", "AFTER_MEAL", "Lookup retired")},
                    ${scheduleJson("Morning", "MORNING", "08:00", "BEFORE_MEAL", "Lookup morning")}
                  ]
                }
                """.trimIndent(),
            )
        assertThat(created.path("registrationCode").asText()).isEqualTo(code)
        val retiredId =
            created
                .path("schedules")
                .first { it.path("label").asText() == "Retired" }
                .path("id")
                .asLong()
        val retired = doseSchedules.findById(retiredId).orElseThrow()
        retired.active = false
        doseSchedules.save(retired)

        mockMvc
            .perform(get("/api/v1/prescriptions:lookup").param("code", code))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.schedules.length()").value(2))
            .andExpect(jsonPath("$.data.schedules[0].displayName").value("Morning"))
            .andExpect(jsonPath("$.data.schedules[0].doseBasis").value("before_meal"))
            .andExpect(jsonPath("$.data.schedules[0].timesPerDay").value(2))
            .andExpect(jsonPath("$.data.schedules[0].dispensedDays").doesNotExist())
            .andExpect(jsonPath("$.data.schedules[1].displayName").value("Evening"))
    }

    @Test
    fun `dose schedules and history reject targets that are not seniors with a care group`() {
        val group = createCareGroup("rx-read-target")
        val seniorWithoutGroup = createUser("rx-read-target-lonely", UserType.SENIOR)
        val actor = group.ownerUserId.toString()

        listOf("dose-schedules", "prescriptions").forEach { resource ->
            mockMvc
                .perform(get("/api/v1/seniors/$UNKNOWN_ID/$resource").param("actorUserId", actor))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("SENIOR_NOT_FOUND"))

            mockMvc
                .perform(get("/api/v1/seniors/${group.ownerUserId}/$resource").param("actorUserId", actor))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("SENIOR_REQUIRED"))

            mockMvc
                .perform(get("/api/v1/seniors/$seniorWithoutGroup/$resource").param("actorUserId", actor))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("CARE_GROUP_REQUIRED"))
        }
    }

    private fun registerPrescription(
        seniorUserId: Long,
        body: String,
    ): JsonNode =
        mockMvc
            .perform(
                post("/api/v1/seniors/$seniorUserId/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)
            .andReturn()
            .dataNode()

    private fun medicationIds(prescription: JsonNode): List<Long> =
        prescription
            .path("schedules")
            .flatMap { schedule -> schedule.path("items").map { it.path("medicationId").asLong() } }

    private fun scheduleJson(
        label: String,
        slot: String,
        scheduledTime: String,
        mealRelation: String,
        medicationPrefix: String,
        doseBasis: String? = null,
    ): String {
        val doseBasisJson = doseBasis?.let { "\"$it\"" } ?: "null"
        return """
            {
              "slot": "$slot",
              "label": "$label",
              "scheduledTime": "$scheduledTime",
              "mealRelation": "$mealRelation",
              "doseBasis": $doseBasisJson,
              "items": [
                {
                  "medicationName": "$medicationPrefix A",
                  "category": " ",
                  "description": "",
                  "count": 2,
                  "shape": "CAPSULE"
                },
                { "medicationName": "$medicationPrefix B", "count": 1 }
              ]
            }
            """.trimIndent()
    }

    private fun singleScheduleBody(
        pharmacistUserId: Long,
        startDate: String,
        endDate: String,
    ): String =
        """
        {
          "pharmacistUserId": $pharmacistUserId,
          "pharmacy": { "name": "Gojjibom Pharmacy", "phone": "02-0000-0000" },
          "prescribedDate": "$startDate",
          "startDate": "$startDate",
          "endDate": "$endDate",
          "schedules": [
            ${scheduleJson("Date packet", "MORNING", "08:30", "AFTER_MEAL", "Date range")}
          ]
        }
        """.trimIndent()

    private fun createCareGroup(suffix: String): CreatedGroup {
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
                                "phone": "010-$suffix-0001",
                                "birthDate": "1940-01-01"
                              },
                              "owner": {
                                "name": "Owner $suffix",
                                "phone": "010-$suffix-0002"
                              }
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()

        val data = result.dataNode()
        val ownerMember = data.path("members").path(0)
        return CreatedGroup(
            groupId = data.path("id").asLong(),
            seniorUserId = data.path("senior").path("id").asLong(),
            ownerUserId = ownerMember.path("user").path("id").asLong(),
        )
    }

    private fun createUser(
        suffix: String,
        userType: UserType,
    ): Long =
        users
            .save(
                UserEntity(
                    userType = userType,
                    name = "User $suffix",
                    phone = "010-test-${phoneSequence.incrementAndGet()}",
                ),
            ).id
            ?: error("user id not assigned")

    private fun prescriptionBody(
        pharmacistUserId: Long,
        pillCount: Int,
    ): String =
        """
        {
          "pharmacistUserId": $pharmacistUserId,
          "pharmacy": {
            "name": "Gojjibom Pharmacy",
            "phone": "02-0000-0000",
            "address": "Seoul"
          },
          "prescribedDate": "2026-07-02",
          "startDate": "2026-07-02",
          "endDate": "2026-07-16",
          "schedules": [
            {
              "slot": "MORNING",
              "label": "Morning packet",
              "packetNo": 1,
              "scheduledTime": "08:30",
              "mealRelation": "AFTER_MEAL",
              "mealOffsetMin": 30,
              "pillCount": $pillCount,
              "items": [
                {
                  "medicationName": "Blood pressure pill",
                  "category": "Blood pressure",
                  "description": "Prescription medication",
                  "count": 1
                },
                {
                  "medicationName": "Diabetes pill",
                  "category": "Diabetes",
                  "count": 2
                }
              ]
            }
          ]
        }
        """.trimIndent()

    private fun org.springframework.test.web.servlet.MvcResult.dataNode(): JsonNode =
        objectMapper.readTree(response.contentAsString).path("data")

    private data class CreatedGroup(
        val groupId: Long,
        val seniorUserId: Long,
        val ownerUserId: Long,
    )
}
