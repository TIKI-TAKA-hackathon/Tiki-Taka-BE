package xyz.stdiodh.gojjibom.caregroup

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class NotificationSettingsIntegrationTest {
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

    private val objectMapper = ObjectMapper()

    @Test
    fun `notification settings returns defaults when no row exists`() {
        val group = createCareGroup("notif-default")

        mockMvc
            .perform(get("/api/v1/seniors/${group.seniorUserId}/notification-settings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.seniorId").value(group.seniorUserId))
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.remindIntervalMin").value(5))
            .andExpect(jsonPath("$.data.maxRetries").value(3))
            .andExpect(jsonPath("$.data.updatedAt").doesNotExist())
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `non primary caregiver cannot update notification settings`() {
        val group = createCareGroup("notif-primary")
        val familyMemberId = approveActiveFamily(group, "notif-primary")
        val familyUserId = memberUserId(group, familyMemberId)

        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/notification-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(settingsBody(familyUserId)),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("PRIMARY_REQUIRED"))
    }

    @Test
    fun `updating notification settings upserts and returns the new values`() {
        val group = createCareGroup("notif-upsert")

        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/notification-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(settingsBody(group.ownerUserId, enabled = false, remindIntervalMin = 10, maxRetries = 1)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.enabled").value(false))
            .andExpect(jsonPath("$.data.remindIntervalMin").value(10))
            .andExpect(jsonPath("$.data.maxRetries").value(1))
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty)

        // Second PUT updates the same row (upsert) and GET reflects the latest values.
        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/notification-settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(settingsBody(group.ownerUserId, enabled = true, remindIntervalMin = 15, maxRetries = 4)),
            ).andExpect(status().isOk)

        mockMvc
            .perform(get("/api/v1/seniors/${group.seniorUserId}/notification-settings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.remindIntervalMin").value(15))
            .andExpect(jsonPath("$.data.maxRetries").value(4))
    }

    private fun settingsBody(
        actorUserId: Long,
        enabled: Boolean = true,
        remindIntervalMin: Int = 5,
        maxRetries: Int = 3,
    ): String =
        """
        {
          "actorUserId": $actorUserId,
          "enabled": $enabled,
          "remindIntervalMin": $remindIntervalMin,
          "maxRetries": $maxRetries
        }
        """.trimIndent()

    private fun approveActiveFamily(
        group: CreatedGroup,
        suffix: String,
    ): Long {
        val token = createInviteToken(group.ownerUserId, group.groupId)
        val member = acceptInvite(token, suffix)
        val memberId = member.path("id").asLong()
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/care-groups/${group.groupId}/members/$memberId")
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
        return memberId
    }

    private fun memberUserId(
        group: CreatedGroup,
        memberId: Long,
    ): Long {
        val data =
            mockMvc
                .perform(get("/api/v1/care-groups/${group.groupId}"))
                .andExpect(status().isOk)
                .andReturn()
                .dataNode()
        return data
            .path("members")
            .first { it.path("id").asLong() == memberId }
            .path("user")
            .path("id")
            .asLong()
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

    private fun createInviteToken(
        ownerUserId: Long,
        groupId: Long,
    ): String {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/care-groups/$groupId/invite-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"ownerUserId": $ownerUserId, "maxUses": 1}"""),
                ).andExpect(status().isCreated)
                .andReturn()

        return result.dataNode().path("token").asText()
    }

    private fun acceptInvite(
        token: String,
        suffix: String,
    ): JsonNode {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/invites/$token:accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "name": "Invitee $suffix",
                              "phone": "010-$suffix-0003",
                              "role": "FAMILY"
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()

        return result.dataNode()
    }

    private fun org.springframework.test.web.servlet.MvcResult.dataNode(): JsonNode =
        objectMapper.readTree(response.contentAsString).path("data")

    private data class CreatedGroup(
        val groupId: Long,
        val seniorUserId: Long,
        val ownerUserId: Long,
    )
}
