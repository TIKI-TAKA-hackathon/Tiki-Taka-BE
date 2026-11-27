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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import xyz.stdiodh.gojjibom.caregroup.domain.CareGroupMemberRepository
import xyz.stdiodh.gojjibom.caregroup.domain.CareGroupRole
import xyz.stdiodh.gojjibom.caregroup.domain.MemberStatus

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CareGroupEdgeCaseIntegrationTest {
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

        private const val UNKNOWN_ID = 999_999_999L
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper()

    @Autowired
    private lateinit var members: CareGroupMemberRepository

    @Test
    fun `unknown care group returns 404 for read and change log`() {
        mockMvc
            .perform(get("/api/v1/care-groups/$UNKNOWN_ID"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_NOT_FOUND"))

        mockMvc
            .perform(get("/api/v1/care-groups/$UNKNOWN_ID/change-log").param("limit", "10"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_NOT_FOUND"))
    }

    @Test
    fun `senior who already has a care group cannot get a second one`() {
        createCareGroup("senior-dup")

        mockMvc
            .perform(
                post("/api/v1/care-groups")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(careGroupBody("Second group", "010-senior-dup-0001", "010-senior-dup-0009")),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_EXISTS"))
    }

    @Test
    fun `phone registered as a senior cannot be reused as an owner`() {
        val first = createCareGroup("type-conflict")

        mockMvc
            .perform(
                post("/api/v1/care-groups")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        careGroupBody(
                            "Conflicting group",
                            seniorPhone = "010-type-conflict-0099",
                            ownerPhone = "010-type-conflict-0001",
                        ),
                    ),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("USER_TYPE_CONFLICT"))

        mockMvc
            .perform(get("/api/v1/care-groups:lookup").param("seniorPhone", "010-type-conflict-0099"))
            .andExpect(status().isNotFound)
        assertThat(members.findByCareGroupIdOrderByIdAsc(first.groupId)).hasSize(1)
    }

    @Test
    fun `existing caregiver is reused as owner of another senior's care group`() {
        val first = createCareGroup("reuse-owner")

        val second =
            mockMvc
                .perform(
                    post("/api/v1/care-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            careGroupBody(
                                "Second senior group",
                                seniorPhone = "010-reuse-owner-0011",
                                ownerPhone = " 010-reuse-owner-0002 ",
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()
                .dataNode()

        val secondOwner = second.path("members").path(0)
        assertThat(secondOwner.path("user").path("id").asLong()).isEqualTo(first.ownerUserId)
        assertThat(secondOwner.path("role").asText()).isEqualTo("OWNER")
        assertThat(second.path("id").asLong()).isNotEqualTo(first.groupId)
    }

    @Test
    fun `accepting an unknown invite token returns 404`() {
        mockMvc
            .perform(
                post("/api/v1/invites/not-a-real-token:accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(inviteAcceptBody("unknown-token", role = "FAMILY")),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("INVITE_NOT_FOUND"))
    }

    @Test
    fun `member update rejects unknown members empty payloads and PENDING status`() {
        val group = createCareGroup("member-update")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        val memberId = acceptInvite(token, "member-update").path("id").asLong()
        activateMember(group, memberId)

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/members/$UNKNOWN_ID")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "status": "ACTIVE"}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/members/$memberId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_MEMBER_UPDATE"))

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/members/$memberId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "status": "PENDING"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_MEMBER_STATUS"))

        val persisted = members.findByIdAndCareGroupId(memberId, group.groupId)
        assertThat(persisted?.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(persisted?.role).isEqualTo(CareGroupRole.FAMILY)
    }

    @Test
    fun `role only update keeps status and the original join time`() {
        val group = createCareGroup("role-only")
        val token = createInviteToken(group.ownerUserId, group.groupId, maxUses = 1)
        val memberId = acceptInvite(token, "role-only").path("id").asLong()
        activateMember(group, memberId)
        val joinedAt = members.findByIdAndCareGroupId(memberId, group.groupId)?.joinedAt

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/members/$memberId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "role": "SOCIAL_WORKER"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.role").value("SOCIAL_WORKER"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.viewerOnly").value(true))

        val persisted = members.findByIdAndCareGroupId(memberId, group.groupId)
        assertThat(joinedAt).isNotNull()
        assertThat(persisted?.joinedAt?.toInstant()).isEqualTo(joinedAt?.toInstant())
    }

    @Test
    fun `meal times read returns 404 until set and then the saved values`() {
        val group = createCareGroup("meal-read")

        mockMvc
            .perform(get("/api/v1/seniors/${group.seniorUserId}/meal-times"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("MEAL_TIMES_NOT_FOUND"))

        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/meal-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mealTimesBody(group.ownerUserId, "07:45", "12:15", "18:45")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(get("/api/v1/seniors/${group.seniorUserId}/meal-times"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.seniorId").value(group.seniorUserId))
            .andExpect(jsonPath("$.data.breakfast").value("07:45:00"))
            .andExpect(jsonPath("$.data.lunch").value("12:15:00"))
            .andExpect(jsonPath("$.data.dinner").value("18:45:00"))
    }

    @Test
    fun `meal times update for a user without a care group returns 404`() {
        val group = createCareGroup("meal-no-group")

        mockMvc
            .perform(
                put("/api/v1/seniors/${group.ownerUserId}/meal-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mealTimesBody(group.ownerUserId)),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_NOT_FOUND"))
    }

    @Test
    fun `resubmitting identical meal times writes no change log rows`() {
        val group = createCareGroup("meal-same")
        repeat(2) {
            mockMvc
                .perform(
                    put("/api/v1/seniors/${group.seniorUserId}/meal-times")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mealTimesBody(group.ownerUserId, "08:00", "12:30", "18:30")),
                ).andExpect(status().isOk)
        }

        // Only the first (insert) PUT logs its three fields; the identical second PUT adds none.
        mockMvc
            .perform(
                get("/api/v1/care-groups/${group.groupId}/change-log")
                    .param("limit", "10"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(3))
    }

    @Test
    fun `primary transfer to an unknown member returns 404 and keeps the current primary`() {
        val group = createCareGroup("primary-unknown")

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/primary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "memberId": $UNKNOWN_ID}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))

        assertThat(members.findByCareGroupIdAndIsPrimaryTrue(group.groupId)?.id).isEqualTo(group.ownerMemberId)
    }

    @Test
    fun `primary transfer to the current primary keeps a single primary and logs a no-op change`() {
        val group = createCareGroup("primary-self")

        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/primary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "memberId": ${group.ownerMemberId}}"""),
            ).andExpect(status().isOk)

        val primaryIds =
            members
                .findByCareGroupIdOrderByIdAsc(group.groupId)
                .filter { it.isPrimary }
                .map { it.id }
        assertThat(primaryIds).containsExactly(group.ownerMemberId)

        mockMvc
            .perform(get("/api/v1/care-groups/${group.groupId}/change-log").param("limit", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].targetType").value("MEMBER"))
            .andExpect(jsonPath("$.data[0].field").value("is_primary"))
            .andExpect(jsonPath("$.data[0].oldValue").value(group.ownerMemberId.toString()))
            .andExpect(jsonPath("$.data[0].newValue").value(group.ownerMemberId.toString()))
    }

    private fun careGroupBody(
        name: String,
        seniorPhone: String,
        ownerPhone: String,
    ): String =
        """
        {
          "name": "$name",
          "senior": {
            "name": "Senior $name",
            "phone": "$seniorPhone",
            "birthDate": "1940-01-01"
          },
          "owner": {
            "name": "Owner $name",
            "phone": "$ownerPhone"
          }
        }
        """.trimIndent()

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

    private data class CreatedGroup(
        val groupId: Long,
        val seniorUserId: Long,
        val ownerUserId: Long,
        val ownerMemberId: Long,
    )
}
