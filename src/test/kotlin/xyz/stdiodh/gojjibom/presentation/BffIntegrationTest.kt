package xyz.stdiodh.gojjibom.presentation

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
            .andExpect(jsonPath("$.data.week.length()").value(7))
            .andExpect(jsonPath("$.data.alert").doesNotExist())
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

    private fun registerPrescription(seniorUserId: Long) {
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

    private data class CreatedGroup(
        val groupId: Long,
        val seniorUserId: Long,
        val ownerUserId: Long,
    )
}
