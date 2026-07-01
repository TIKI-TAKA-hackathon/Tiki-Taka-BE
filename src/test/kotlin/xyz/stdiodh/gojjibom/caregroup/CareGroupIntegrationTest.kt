package xyz.stdiodh.gojjibom.caregroup

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
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
class CareGroupIntegrationTest {
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

    @Autowired
    private lateinit var inviteLinks: InviteLinkRepository

    @Test
    fun `create care group creates senior and active owner membership`() {
        val group = createCareGroup("create")

        mockMvc
            .perform(get("/api/v1/care-groups/${group.groupId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.senior.userType").value("SENIOR"))
            .andExpect(jsonPath("$.data.members[0].role").value("OWNER"))
            .andExpect(jsonPath("$.data.members[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `invite acceptance creates pending member and owner approves it`() {
        val group = createCareGroup("approve")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        val pendingMember = acceptInvite(token, "approve")

        assertThat(pendingMember.path("status").asText()).isEqualTo("PENDING")

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/members/${pendingMember.path("id").asLong()}")
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
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.joinedAt").isNotEmpty)
    }

    @Test
    fun `non owner cannot issue invite link`() {
        val group = createCareGroup("not-owner")

        mockMvc
            .perform(
                post("/api/v1/care-groups/${group.groupId}/invite-links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ownerUserId": 999999, "maxUses": 1}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("OWNER_REQUIRED"))
    }

    @Test
    fun `reissued invite revokes previous invite link`() {
        val group = createCareGroup("revoked")
        val revokedToken = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)

        mockMvc
            .perform(
                post("/api/v1/invites/$revokedToken:accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(inviteAcceptBody("revoked", role = "FAMILY")),
            ).andExpect(status().isGone)
            .andExpect(jsonPath("$.error.code").value("INVITE_REVOKED"))
    }

    @Test
    fun `expired invite link is rejected`() {
        val group = createCareGroup("expired")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        val invite = inviteLinks.findByToken(token) ?: error("invite not found")
        invite.expiresAt = OffsetDateTime.now().minusMinutes(1)
        inviteLinks.save(invite)

        mockMvc
            .perform(
                post("/api/v1/invites/$token:accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(inviteAcceptBody("expired", role = "FAMILY")),
            ).andExpect(status().isGone)
            .andExpect(jsonPath("$.error.code").value("INVITE_EXPIRED"))
    }

    @Test
    fun `invite link cannot be reused after max uses`() {
        val group = createCareGroup("exhausted")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        acceptInvite(token, "exhausted-one")

        mockMvc
            .perform(
                post("/api/v1/invites/$token:accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(inviteAcceptBody("exhausted-two", role = "SOCIAL_WORKER")),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("INVITE_EXHAUSTED"))
    }

    @Test
    fun `owner member cannot be removed`() {
        val group = createCareGroup("owner-remove")

        mockMvc
            .perform(
                delete("/api/v1/care-groups/${group.groupId}/members/${group.ownerMemberId}")
                    .param("actorUserId", group.ownerUserId.toString()),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("OWNER_MEMBER_IMMUTABLE"))
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
            ownerUserId = ownerMember.path("user").path("id").asLong(),
            ownerMemberId = ownerMember.path("id").asLong(),
        )
    }

    private fun createInviteToken(
        ownerUserId: Long,
        groupId: Long,
        maxUses: Int,
    ): String {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/care-groups/$groupId/invite-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"ownerUserId": $ownerUserId, "maxUses": $maxUses}"""),
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
                        .content(inviteAcceptBody(suffix, role = "FAMILY")),
                ).andExpect(status().isCreated)
                .andReturn()

        return result.dataNode()
    }

    private fun inviteAcceptBody(
        suffix: String,
        role: String,
    ): String =
        """
        {
          "name": "Invitee $suffix",
          "phone": "010-$suffix-0003",
          "role": "$role"
        }
        """.trimIndent()

    private fun org.springframework.test.web.servlet.MvcResult.dataNode(): JsonNode =
        objectMapper.readTree(response.contentAsString).path("data")

    private data class CreatedGroup(
        val groupId: Long,
        val ownerUserId: Long,
        val ownerMemberId: Long,
    )
}
