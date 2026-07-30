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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
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

    @Autowired
    private lateinit var members: CareGroupMemberRepository

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
    fun `invite acceptance rejects the OWNER role`() {
        val group = createCareGroup("invite-owner")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)

        mockMvc
            .perform(
                post("/api/v1/invites/$token:accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(inviteAcceptBody("invite-owner", role = "OWNER")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_MEMBER_ROLE"))

        assertThat(inviteLinks.findByToken(token)?.useCount).isZero()
        assertThat(members.findByCareGroupIdOrderByIdAsc(group.groupId)).hasSize(1)
    }

    @Test
    fun `pending member cannot accept another invite to the same care group`() {
        val group = createCareGroup("pending-duplicate")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 2)
        acceptInvite(token, "pending-duplicate")

        mockMvc
            .perform(
                post("/api/v1/invites/$token:accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(inviteAcceptBody("pending-duplicate", role = "FAMILY")),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MEMBER_ALREADY_EXISTS"))

        assertThat(inviteLinks.findByToken(token)?.useCount).isEqualTo(1)
        assertThat(members.findByCareGroupIdOrderByIdAsc(group.groupId)).hasSize(2)
    }

    @Test
    fun `active member cannot accept another invite to the same care group`() {
        val group = createCareGroup("active-duplicate")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 2)
        val member = acceptInvite(token, "active-duplicate")
        activateMember(group, member.path("id").asLong())

        mockMvc
            .perform(
                post("/api/v1/invites/$token:accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(inviteAcceptBody("active-duplicate", role = "FAMILY")),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("MEMBER_ALREADY_EXISTS"))

        assertThat(inviteLinks.findByToken(token)?.useCount).isEqualTo(1)
        assertThat(members.findByCareGroupIdOrderByIdAsc(group.groupId)).hasSize(2)
    }

    @Test
    fun `removed member rejoins as the same non primary pending row`() {
        val group = createCareGroup("removed-rejoin")
        val firstToken = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        val original = acceptInvite(firstToken, "removed-rejoin")
        val memberId = original.path("id").asLong()
        activateMember(group, memberId)

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/primary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "memberId": $memberId}"""),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                delete("/api/v1/care-groups/${group.groupId}/members/$memberId")
                    .param("actorUserId", group.ownerUserId.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("REMOVED"))

        val secondToken = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        val rejoinedResult =
            mockMvc
                .perform(
                    post("/api/v1/invites/$secondToken:accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteAcceptBody("removed-rejoin", role = "SOCIAL_WORKER")),
                ).andExpect(status().isCreated)
                .andReturn()
        val rejoined = rejoinedResult.dataNode()

        assertThat(rejoined.path("id").asLong()).isEqualTo(memberId)
        assertThat(rejoined.path("status").asText()).isEqualTo("PENDING")
        assertThat(rejoined.path("role").asText()).isEqualTo("SOCIAL_WORKER")
        assertThat(rejoined.path("joinedAt").isNull).isTrue()
        assertThat(rejoined.path("isPrimary").asBoolean()).isFalse()

        val persisted = members.findByIdAndCareGroupId(memberId, group.groupId)
        assertThat(persisted?.status).isEqualTo(MemberStatus.PENDING)
        assertThat(persisted?.role).isEqualTo(CareGroupRole.SOCIAL_WORKER)
        assertThat(persisted?.joinedAt).isNull()
        assertThat(persisted?.isPrimary).isFalse()
        assertThat(members.findByCareGroupIdOrderByIdAsc(group.groupId)).hasSize(2)
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

    @Test
    fun `owner membership cannot be patched`() {
        val group = createCareGroup("owner-patch")

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/members/${group.ownerMemberId}")
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
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("OWNER_MEMBER_IMMUTABLE"))
    }

    @Test
    fun `non owner member cannot be promoted to OWNER`() {
        val group = createCareGroup("promote-owner")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        val member = acceptInvite(token, "promote-owner")

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/members/${member.path("id").asLong()}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "role": "OWNER"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_MEMBER_ROLE"))
    }

    @Test
    fun `owner is created as the primary caregiver`() {
        val group = createCareGroup("primary-owner")

        mockMvc
            .perform(get("/api/v1/care-groups/${group.groupId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.members[0].isPrimary").value(true))
            .andExpect(jsonPath("$.data.members[0].viewerOnly").value(false))
    }

    @Test
    fun `only the primary caregiver can edit meal times`() {
        val group = createCareGroup("meal-primary")
        val familyMemberId = approveActiveFamily(group, "meal-primary")
        val familyUserId = memberUserId(group, familyMemberId)

        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/meal-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mealTimesBody(familyUserId)),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("PRIMARY_REQUIRED"))
    }

    @Test
    fun `updating meal times upserts and writes one change log row per changed field`() {
        val group = createCareGroup("meal-log")

        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/meal-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mealTimesBody(group.ownerUserId, "08:00", "12:30", "18:30")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.breakfast").value("08:00:00"))

        // Only lunch and dinner change on the second update -> two change_log rows total.
        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/meal-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mealTimesBody(group.ownerUserId, "08:00", "13:00", "19:00")),
            ).andExpect(status().isOk)

        // First upsert had no existing row -> 3 rows; second changed 2 fields -> 2 rows; total 5.
        mockMvc
            .perform(
                get("/api/v1/care-groups/${group.groupId}/change-log")
                    .param("limit", "10"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(5))
            .andExpect(jsonPath("$.data[0].targetType").value("MEAL_TIME"))
    }

    @Test
    fun `primary transfer moves the flag to the target member`() {
        val group = createCareGroup("primary-transfer")
        val familyMemberId = approveActiveFamily(group, "primary-transfer")

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/primary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "memberId": $familyMemberId}"""),
            ).andExpect(status().isOk)

        val refreshed =
            mockMvc
                .perform(get("/api/v1/care-groups/${group.groupId}"))
                .andExpect(status().isOk)
                .andReturn()
                .dataNode()

        val primaryMemberIds =
            refreshed
                .path("members")
                .filter { it.path("isPrimary").asBoolean() }
                .map { it.path("id").asLong() }
        assertThat(primaryMemberIds).containsExactly(familyMemberId)
    }

    @Test
    fun `pending member cannot become the primary caregiver`() {
        val group = createCareGroup("pending-primary")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        val pending = acceptInvite(token, "pending-primary")

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/primary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "actorUserId": ${group.ownerUserId},
                          "memberId": ${pending.path("id").asLong()}
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_PRIMARY_TARGET"))

        val persistedMembers = members.findByCareGroupIdOrderByIdAsc(group.groupId)
        assertThat(persistedMembers.single { it.id == group.ownerMemberId }.isPrimary).isTrue()
        assertThat(persistedMembers.single { it.id == pending.path("id").asLong() }.isPrimary).isFalse()
    }

    @Test
    fun `senior and owner with the same phone are rejected as an invalid owner`() {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/care-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "name": "Same phone group",
                              "senior": {
                                "name": "Same phone senior",
                                "phone": "010-same-phone-0001",
                                "birthDate": "1940-01-01"
                              },
                              "owner": {
                                "name": "Same phone owner",
                                "phone": "010-same-phone-0001"
                              }
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isBadRequest)
                .andReturn()

        result.errorNode("INVALID_OWNER")
    }

    @Test
    fun `malformed JSON returns the common error envelope`() {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/care-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"),
                ).andExpect(status().isBadRequest)
                .andReturn()

        val error = result.errorNode("INVALID_REQUEST")
        assertThat(error.path("message").asText()).isEqualTo("Invalid request body")
    }

    @Test
    fun `missing request parameter returns the common error envelope`() {
        val result =
            mockMvc
                .perform(get("/api/v1/care-groups:lookup"))
                .andExpect(status().isBadRequest)
                .andReturn()

        val error = result.errorNode("INVALID_REQUEST")
        assertThat(error.path("message").asText()).isEqualTo("Missing request parameter: seniorPhone")
    }

    @Test
    fun `bean validation failure returns the common error envelope`() {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/care-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "name": " ",
                              "senior": {
                                "name": "Validation senior",
                                "phone": "010-validation-0001",
                                "birthDate": "1940-01-01"
                              },
                              "owner": {
                                "name": "Validation owner",
                                "phone": "010-validation-0002"
                              }
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isBadRequest)
                .andReturn()

        val error = result.errorNode("INVALID_REQUEST")
        assertThat(error.path("message").asText()).contains("name")
    }

    private fun activateMember(
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

    private fun approveActiveFamily(
        group: CreatedGroup,
        suffix: String,
    ): Long {
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        val member = acceptInvite(token, suffix)
        val memberId = member.path("id").asLong()
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

    private fun mealTimesBody(
        actorUserId: Long,
        breakfast: String = "08:00",
        lunch: String = "12:30",
        dinner: String = "18:30",
    ): String =
        """
        {
          "actorUserId": $actorUserId,
          "breakfast": "$breakfast",
          "lunch": "$lunch",
          "dinner": "$dinner"
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

    private fun org.springframework.test.web.servlet.MvcResult.errorNode(expectedCode: String): JsonNode {
        val root = objectMapper.readTree(response.contentAsString)
        assertThat(root.has("data")).isTrue()
        assertThat(root.path("data").isNull).isTrue()
        assertThat(root.has("error")).isTrue()
        assertThat(root.path("error").isObject).isTrue()
        assertThat(root.path("error").path("code").asText()).isEqualTo(expectedCode)
        assertThat(root.path("error").path("message").asText()).isNotBlank()
        return root.path("error")
    }

    private data class CreatedGroup(
        val groupId: Long,
        val seniorUserId: Long,
        val ownerUserId: Long,
        val ownerMemberId: Long,
    )
}
