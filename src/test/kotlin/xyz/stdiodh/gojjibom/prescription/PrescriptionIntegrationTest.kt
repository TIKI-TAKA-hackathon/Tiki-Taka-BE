package xyz.stdiodh.gojjibom.prescription

import com.fasterxml.jackson.databind.JsonNode
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
class PrescriptionIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        private val phoneSequence = AtomicLong()

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

    @Test
    fun `pharmacist registers prescription and active owner reads dose schedules`() {
        val group = createCareGroup("rx-ok")
        val pharmacistUserId = createUser("rx-ok-pharmacist", UserType.PHARMACIST)

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(prescriptionBody(pharmacistUserId, pillCount = 3)),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.seniorId").value(group.seniorUserId))
            .andExpect(jsonPath("$.data.pharmacy.name").value("Gojjibom Pharmacy"))
            .andExpect(jsonPath("$.data.schedules[0].slot").value("MORNING"))
            .andExpect(jsonPath("$.data.schedules[0].pillCount").value(3))
            .andExpect(jsonPath("$.data.schedules[0].items.length()").value(2))

        mockMvc
            .perform(
                get("/api/v1/seniors/${group.seniorUserId}/dose-schedules")
                    .param("actorUserId", group.ownerUserId.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.schedules.length()").value(1))
            .andExpect(jsonPath("$.data.schedules[0].label").value("Morning packet"))
            .andExpect(jsonPath("$.data.schedules[0].items[0].medicationName").value("Blood pressure pill"))
    }

    @Test
    fun `pending member cannot read dose schedules`() {
        val group = createCareGroup("pending-read")
        val pendingMember = createPendingMember(group)

        mockMvc
            .perform(
                get("/api/v1/seniors/${group.seniorUserId}/dose-schedules")
                    .param(
                        "actorUserId",
                        pendingMember
                            .path("user")
                            .path("id")
                            .asLong()
                            .toString(),
                    ),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_MEMBER_REQUIRED"))
    }

    @Test
    fun `approved family member can read dose schedules`() {
        val group = createCareGroup("family-read")
        val pendingMember = createPendingMember(group)
        approveMember(group, pendingMember.path("id").asLong())

        mockMvc
            .perform(
                get("/api/v1/seniors/${group.seniorUserId}/dose-schedules")
                    .param(
                        "actorUserId",
                        pendingMember
                            .path("user")
                            .path("id")
                            .asLong()
                            .toString(),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.schedules.length()").value(0))
    }

    @Test
    fun `non pharmacist cannot register prescription`() {
        val group = createCareGroup("not-pharmacist")

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(prescriptionBody(group.ownerUserId, pillCount = 3)),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("PHARMACIST_REQUIRED"))
    }

    @Test
    fun `pill count must equal sum of medication item counts`() {
        val group = createCareGroup("bad-pill-count")
        val pharmacistUserId = createUser("bad-pill-count-pharmacist", UserType.PHARMACIST)

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(prescriptionBody(pharmacistUserId, pillCount = 2)),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("PILL_COUNT_MISMATCH"))
    }

    @Test
    fun `prescription requires at least one schedule`() {
        val group = createCareGroup("empty-schedules")
        val pharmacistUserId = createUser("empty-schedules-pharmacist", UserType.PHARMACIST)

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "pharmacistUserId": $pharmacistUserId,
                          "pharmacy": {
                            "name": "Gojjibom Pharmacy",
                            "phone": "02-0000-0000"
                          },
                          "prescribedDate": "2026-07-02",
                          "startDate": "2026-07-02",
                          "endDate": "2026-07-16",
                          "schedules": []
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
    }

    @Test
    fun `dose schedule requires at least one medication item`() {
        val group = createCareGroup("empty-items")
        val pharmacistUserId = createUser("empty-items-pharmacist", UserType.PHARMACIST)

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(emptyItemsPrescriptionBody(pharmacistUserId)),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
    }

    @Test
    fun `same medication cannot appear twice in one dose schedule`() {
        val group = createCareGroup("duplicate-item")
        val pharmacistUserId = createUser("duplicate-item-pharmacist", UserType.PHARMACIST)

        mockMvc
            .perform(
                post("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(duplicateItemPrescriptionBody(pharmacistUserId)),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_SCHEDULE_ITEM"))
    }

    @Test
    fun `confirm-meds lookup hides diagnosis and pharmacy contact fields`() {
        val group = createCareGroup("prv01")
        val pharmacistUserId = createUser("prv01-pharmacist", UserType.PHARMACIST)
        val code = "RX-PRV01-${phoneSequence.incrementAndGet()}"
        registerPrescriptionWithCode(group.seniorUserId, pharmacistUserId, code)

        val result =
            mockMvc
                .perform(get("/api/v1/prescriptions:lookup").param("code", code))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.pharmacyName").value("Gojjibom Pharmacy"))
                .andExpect(jsonPath("$.data.registrationCode").value(code))
                .andExpect(jsonPath("$.data.dispensingType").value("pouch"))
                .andExpect(jsonPath("$.data.schedules[0].doseBasis").value("after_meal"))
                .andReturn()

        val json = result.response.contentAsString
        // PRV-01: response must not carry 병명(category), pharmacy phone/address.
        assert(!json.contains("\"category\"")) { "lookup response leaked category: $json" }
        assert(!json.contains("\"phone\"")) { "lookup response leaked phone: $json" }
        assert(!json.contains("\"address\"")) { "lookup response leaked address: $json" }
        assert(!json.contains("Blood pressure")) { "lookup response leaked diagnosis text: $json" }
    }

    @Test
    fun `confirm-meds lookup returns 404 for unknown code`() {
        mockMvc
            .perform(get("/api/v1/prescriptions:lookup").param("code", "RX-DOES-NOT-EXIST"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("PRESCRIPTION_NOT_FOUND"))
    }

    @Test
    fun `prescription history derives active and ended status by end date`() {
        val group = createCareGroup("history")
        val pharmacistUserId = createUser("history-pharmacist", UserType.PHARMACIST)
        // Past prescription (ended: end_date is well before any realistic today).
        registerPrescriptionWithDates(group.seniorUserId, pharmacistUserId, "2020-01-01", "2020-01-15")
        // Open-ended prescription (active).
        registerPrescriptionWithDates(group.seniorUserId, pharmacistUserId, "2026-06-01", null)

        mockMvc
            .perform(
                get("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .param("actorUserId", group.ownerUserId.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.seniorId").value(group.seniorUserId.toString()))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            // Ordered by start_date desc: the 2026 (active) entry comes first.
            .andExpect(jsonPath("$.data.items[0].status").value("active"))
            .andExpect(jsonPath("$.data.items[1].status").value("ended"))
    }

    @Test
    fun `prescription history rejects non-member`() {
        val group = createCareGroup("history-forbidden")
        val stranger = createUser("history-stranger", UserType.CAREGIVER)

        mockMvc
            .perform(
                get("/api/v1/seniors/${group.seniorUserId}/prescriptions")
                    .param("actorUserId", stranger.toString()),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_MEMBER_REQUIRED"))
    }

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

    private fun createPendingMember(group: CreatedGroup): JsonNode {
        val tokenResult =
            mockMvc
                .perform(
                    post("/api/v1/care-groups/${group.groupId}/invite-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"ownerUserId": ${group.ownerUserId}, "maxUses": 1}"""),
                ).andExpect(status().isCreated)
                .andReturn()
        val token = tokenResult.dataNode().path("token").asText()

        return mockMvc
            .perform(
                post("/api/v1/invites/$token:accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Family ${group.groupId}",
                          "phone": "010-family-${group.groupId}",
                          "role": "FAMILY"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
            .andReturn()
            .dataNode()
    }

    private fun approveMember(
        group: CreatedGroup,
        memberId: Long,
    ) {
        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/members/$memberId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "actorUserId": ${group.ownerUserId},
                          "status": "ACTIVE",
                          "role": "FAMILY"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isOk)
    }

    private fun registerPrescriptionWithCode(
        seniorUserId: Long,
        pharmacistUserId: Long,
        code: String,
    ) {
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
                            "phone": "02-0000-0000",
                            "address": "Seoul"
                          },
                          "prescribedDate": "2026-07-02",
                          "startDate": "2026-07-02",
                          "endDate": "2026-07-16",
                          "dispensingType": "POUCH",
                          "registrationCode": "$code",
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

    private fun registerPrescriptionWithDates(
        seniorUserId: Long,
        pharmacistUserId: Long,
        startDate: String,
        endDate: String?,
    ) {
        val endDateJson = endDate?.let { "\"$it\"" } ?: "null"
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
                          "endDate": $endDateJson,
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

    private fun emptyItemsPrescriptionBody(pharmacistUserId: Long): String =
        """
        {
          "pharmacistUserId": $pharmacistUserId,
          "pharmacy": {
            "name": "Gojjibom Pharmacy",
            "phone": "02-0000-0000"
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
              "pillCount": 1,
              "items": []
            }
          ]
        }
        """.trimIndent()

    private fun duplicateItemPrescriptionBody(pharmacistUserId: Long): String =
        """
        {
          "pharmacistUserId": $pharmacistUserId,
          "pharmacy": {
            "name": "Gojjibom Pharmacy",
            "phone": "02-0000-0000"
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
              "pillCount": 2,
              "items": [
                {
                  "medicationName": "Blood pressure pill",
                  "category": "Blood pressure",
                  "count": 1
                },
                {
                  "medicationName": "Blood pressure pill",
                  "category": "Blood pressure",
                  "count": 1
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
