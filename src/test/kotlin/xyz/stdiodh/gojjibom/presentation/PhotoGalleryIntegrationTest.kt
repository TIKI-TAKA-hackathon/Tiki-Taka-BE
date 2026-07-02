package xyz.stdiodh.gojjibom.presentation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
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
import xyz.stdiodh.gojjibom.media.FakeObjectStorage
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PhotoGalleryIntegrationTest {
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
    fun `photos aggregate returns attached-photo events with signed url and review status`() {
        val fixture = confirmedPhotoDose()

        mockMvc
            .perform(
                get("/api/v1/care-groups/${fixture.groupId}/photos")
                    .param("from", "2026-06-30")
                    .param("to", doseDate),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.careGroupId").value(fixture.groupId.toString()))
            .andExpect(jsonPath("$.data.photos.length()").value(1))
            .andExpect(jsonPath("$.data.photos[0].doseEventId").value(fixture.doseEventId.toString()))
            .andExpect(jsonPath("$.data.photos[0].doseLabel").value("아침약 · 1번 봉지"))
            .andExpect(jsonPath("$.data.photos[0].status").value("done"))
            .andExpect(jsonPath("$.data.photos[0].method").value("caregiver"))
            .andExpect(jsonPath("$.data.photos[0].reviewStatus").value("pending"))
            .andExpect(jsonPath("$.data.photos[0].photoUrl").value(fixture.viewUrl))
            .andExpect(jsonPath("$.data.photos[0].thumbnailUrl").value(fixture.viewUrl))
    }

    @Test
    fun `photos aggregate excludes dose events outside the requested window`() {
        val fixture = confirmedPhotoDose()

        mockMvc
            .perform(
                get("/api/v1/care-groups/${fixture.groupId}/photos")
                    .param("from", "2026-07-03")
                    .param("to", "2026-07-09"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.photos.length()").value(0))
    }

    @Test
    fun `review photo sets flagged status`() {
        val fixture = confirmedPhotoDose()

        mockMvc
            .perform(
                patch("/api/v1/dose-events/${fixture.doseEventId}/photo:review")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reviewBody(fixture.ownerUserId, "FLAGGED")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(fixture.doseEventId))
            .andExpect(jsonPath("$.data.photoReviewStatus").value("FLAGGED"))

        mockMvc
            .perform(
                get("/api/v1/care-groups/${fixture.groupId}/photos").param("to", doseDate),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.photos[0].reviewStatus").value("flagged"))
    }

    @Test
    fun `review photo requires active care group membership`() {
        val fixture = confirmedPhotoDose()
        val stranger =
            users
                .save(
                    UserEntity(
                        userType = UserType.CAREGIVER,
                        name = "Stranger ${suffixSequence.incrementAndGet()}",
                        phone = "010-photo-x${suffixSequence.incrementAndGet()}",
                    ),
                ).id ?: error("stranger id not assigned")

        mockMvc
            .perform(
                patch("/api/v1/dose-events/${fixture.doseEventId}/photo:review")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reviewBody(stranger, "REVIEWED")),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_MEMBER_REQUIRED"))
    }

    @Test
    fun `review photo returns 400 when dose event has no photo`() {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId)
        val doseEventId = generateDoseEvent(group.seniorUserId)

        mockMvc
            .perform(
                patch("/api/v1/dose-events/$doseEventId/photo:review")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reviewBody(group.ownerUserId, "REVIEWED")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("DOSE_EVENT_PHOTO_REQUIRED"))
    }

    private fun confirmedPhotoDose(): PhotoFixture {
        val group = createCareGroup()
        registerPrescription(group.seniorUserId)
        val doseEventId = generateDoseEvent(group.seniorUserId)
        val objectKey = registerDoseEventImage(group, doseEventId)
        val imageId =
            jdbcTemplate.queryForObject(
                "SELECT id FROM images WHERE object_key = ?",
                Long::class.java,
                objectKey,
            ) ?: error("image id not found")

        mockMvc
            .perform(
                post("/api/v1/dose-events/$doseEventId:confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "actorUserId": ${group.ownerUserId},
                          "method": "CAREGIVER",
                          "imageId": $imageId
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.photoReviewStatus").value("PENDING"))

        return PhotoFixture(
            groupId = group.groupId,
            ownerUserId = group.ownerUserId,
            doseEventId = doseEventId,
            viewUrl = "https://storage.test/view/$objectKey",
        )
    }

    private fun registerDoseEventImage(
        group: CreatedGroup,
        doseEventId: Long,
    ): String {
        val objectKey =
            createImageUploadUrl(group)
                .path("objectKey")
                .asText()

        mockMvc
            .perform(
                post("/api/v1/media/images")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "actorUserId": ${group.ownerUserId},
                          "careGroupId": ${group.groupId},
                          "ownerType": "DOSE_EVENT",
                          "ownerId": $doseEventId,
                          "objectKey": "$objectKey",
                          "contentType": "image/jpeg",
                          "sizeBytes": 512
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)

        return objectKey
    }

    private fun createImageUploadUrl(group: CreatedGroup): JsonNode =
        mockMvc
            .perform(
                post("/api/v1/media/images/upload-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "actorUserId": ${group.ownerUserId},
                          "careGroupId": ${group.groupId},
                          "ownerType": "CARE_GROUP",
                          "ownerId": ${group.groupId},
                          "contentType": "image/jpeg",
                          "sizeBytes": 512
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
            .andReturn()
            .dataNode()

    private fun generateDoseEvent(seniorUserId: Long): Long {
        val result =
            mockMvc
                .perform(
                    get("/api/v1/senior/today")
                        .param("seniorId", seniorUserId.toString())
                        .param("date", doseDate),
                ).andExpect(status().isOk)
                .andReturn()
        val doses = objectMapper.readTree(result.response.contentAsString).path("data").path("doses")
        return doses.path(0).path("id").asLong()
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
                                "phone": "010-photo-s$suffix",
                                "birthDate": "1940-01-01"
                              },
                              "owner": {
                                "name": "Owner $suffix",
                                "phone": "010-photo-o$suffix"
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
                        phone = "010-photo-ph${suffixSequence.incrementAndGet()}",
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

    private fun reviewBody(
        actorUserId: Long,
        reviewStatus: String,
    ): String =
        """
        {
          "actorUserId": $actorUserId,
          "reviewStatus": "$reviewStatus"
        }
        """.trimIndent()

    private fun MvcResult.dataNode(): JsonNode = objectMapper.readTree(response.contentAsString).path("data")

    private data class CreatedGroup(
        val groupId: Long,
        val seniorUserId: Long,
        val ownerUserId: Long,
    )

    private data class PhotoFixture(
        val groupId: Long,
        val ownerUserId: Long,
        val doseEventId: Long,
        val viewUrl: String,
    )

    @TestConfiguration
    class Fakes {
        @Bean
        @Primary
        fun objectStorage(): FakeObjectStorage = FakeObjectStorage()
    }
}
