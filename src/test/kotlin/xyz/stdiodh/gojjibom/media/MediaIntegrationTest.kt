package xyz.stdiodh.gojjibom.media

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.OffsetDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class MediaIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

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
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectStorage: FakeObjectStorage

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun resetFakes() {
        objectStorage.reset()
    }

    @Test
    fun `image upload url requires active care group membership`() {
        mockMvc
            .perform(
                post("/api/v1/media/images/upload-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(imageUploadUrlBody(actorUserId = 999, careGroupId = 999)),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_MEMBER_REQUIRED"))
    }

    @Test
    fun `image upload url rejects unsupported content type`() {
        val member = createActiveMember("bad-type")

        mockMvc
            .perform(
                post("/api/v1/media/images/upload-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        imageUploadUrlBody(
                            actorUserId = member.actorUserId,
                            careGroupId = member.careGroupId,
                            contentType = "application/pdf",
                        ),
                    ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_IMAGE_TYPE"))
    }

    @Test
    fun `image upload url rejects oversized image`() {
        val member = createActiveMember("too-large")

        mockMvc
            .perform(
                post("/api/v1/media/images/upload-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        imageUploadUrlBody(
                            actorUserId = member.actorUserId,
                            careGroupId = member.careGroupId,
                            sizeBytes = 2048,
                        ),
                    ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("IMAGE_TOO_LARGE"))
    }

    @Test
    fun `image registration stores metadata and returns view url`() {
        val member = createActiveMember("register")
        val upload = createImageUploadUrl(member)
        val image =
            mockMvc
                .perform(
                    post("/api/v1/media/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            registerImageBody(
                                member = member,
                                objectKey = upload.path("objectKey").asText(),
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.objectKey").value(upload.path("objectKey").asText()))
                .andReturn()
                .dataNode()

        mockMvc
            .perform(
                get("/api/v1/media/images/${image.path("id").asLong()}/view-url")
                    .param("actorUserId", member.actorUserId.toString())
                    .param("careGroupId", member.careGroupId.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.viewUrl").value(viewUrlFor(upload.path("objectKey").asText())))
    }

    @Test
    fun `image view url rejects object key outside requested care group`() {
        val member = createActiveMember("view-owner")
        val otherGroup = createActiveMember("view-other", actorUserId = member.actorUserId)
        val upload = createImageUploadUrl(member)
        val image =
            mockMvc
                .perform(
                    post("/api/v1/media/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerImageBody(member = member, objectKey = upload.path("objectKey").asText())),
                ).andExpect(status().isCreated)
                .andReturn()
                .dataNode()

        mockMvc
            .perform(
                get("/api/v1/media/images/${image.path("id").asLong()}/view-url")
                    .param("actorUserId", otherGroup.actorUserId.toString())
                    .param("careGroupId", otherGroup.careGroupId.toString()),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("IMAGE_OBJECT_KEY_DENIED"))
    }

    private fun createImageUploadUrl(member: TestMember): JsonNode =
        mockMvc
            .perform(
                post("/api/v1/media/images/upload-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(imageUploadUrlBody(actorUserId = member.actorUserId, careGroupId = member.careGroupId)),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.uploadUrl").exists())
            .andReturn()
            .dataNode()

    private fun imageUploadUrlBody(
        actorUserId: Long,
        careGroupId: Long,
        contentType: String = "image/jpeg",
        sizeBytes: Long = 512,
    ): String =
        """
        {
          "actorUserId": $actorUserId,
          "careGroupId": $careGroupId,
          "ownerType": "MEDICATION",
          "ownerId": 10,
          "contentType": "$contentType",
          "sizeBytes": $sizeBytes
        }
        """.trimIndent()

    private fun registerImageBody(
        member: TestMember,
        objectKey: String,
    ): String =
        """
        {
          "actorUserId": ${member.actorUserId},
          "careGroupId": ${member.careGroupId},
          "ownerType": "MEDICATION",
          "ownerId": 10,
          "objectKey": "$objectKey",
          "contentType": "image/jpeg",
          "sizeBytes": 512
        }
        """.trimIndent()

    private fun createActiveMember(
        suffix: String,
        actorUserId: Long? = null,
    ): TestMember {
        val seniorId = insertUser(name = "Senior $suffix", phone = "010-$suffix-0001", userType = "SENIOR")
        val actorId =
            actorUserId
                ?: insertUser(name = "Actor $suffix", phone = "010-$suffix-0002", userType = "CAREGIVER")
        val groupId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO care_groups (senior_id, name)
                VALUES (?, ?)
                RETURNING id
                """.trimIndent(),
                Long::class.java,
                seniorId,
                "Care group $suffix",
            ) ?: error("care group id not returned")

        jdbcTemplate.update(
            """
            INSERT INTO care_group_members (care_group_id, user_id, role, status, joined_at)
            VALUES (?, ?, 'FAMILY', 'ACTIVE', CURRENT_TIMESTAMP)
            """.trimIndent(),
            groupId,
            actorId,
        )

        return TestMember(actorUserId = actorId, careGroupId = groupId)
    }

    private fun insertUser(
        name: String,
        phone: String,
        userType: String,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO users (user_type, name, phone)
            VALUES (?, ?, ?)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            userType,
            name,
            phone,
        ) ?: error("user id not returned")

    private fun MvcResult.dataNode(): JsonNode = objectMapper.readTree(response.contentAsString).path("data")

    private fun viewUrlFor(objectKey: String): String = "https://storage.test/view/$objectKey"

    data class TestMember(
        val actorUserId: Long,
        val careGroupId: Long,
    )

    @TestConfiguration
    class Fakes {
        @Bean
        @Primary
        fun objectStorage(): FakeObjectStorage = FakeObjectStorage()
    }
}

class FakeObjectStorage : ObjectStorage {
    val putObjects: MutableMap<String, ByteArray> = linkedMapOf()

    fun reset() {
        putObjects.clear()
    }

    override fun presignUpload(
        objectKey: String,
        contentType: String,
        sizeBytes: Long,
        ttlSeconds: Long,
    ): PresignedObjectUrl =
        PresignedObjectUrl(
            url = "https://storage.test/upload/$objectKey",
            expiresAt = OffsetDateTime.parse("2030-01-01T00:00:00Z"),
            requiredHeaders = mapOf("content-type" to contentType),
        )

    override fun presignView(
        objectKey: String,
        ttlSeconds: Long,
    ): PresignedObjectUrl =
        PresignedObjectUrl(
            url = "https://storage.test/view/$objectKey",
            expiresAt = OffsetDateTime.parse("2030-01-01T00:00:00Z"),
            requiredHeaders = emptyMap(),
        )

    override fun putObject(
        objectKey: String,
        contentType: String,
        bytes: ByteArray,
    ) {
        putObjects[objectKey] = bytes
    }
}
