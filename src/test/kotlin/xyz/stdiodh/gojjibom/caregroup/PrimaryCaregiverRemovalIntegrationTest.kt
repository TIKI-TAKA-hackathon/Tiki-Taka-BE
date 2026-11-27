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
import xyz.stdiodh.gojjibom.caregroup.domain.CareGroupMemberRepository

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PrimaryCaregiverRemovalIntegrationTest {
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
    private lateinit var members: CareGroupMemberRepository

    private val objectMapper = ObjectMapper()

    @Test
    fun `removing the primary caregiver hands primary back to the active owner`() {
        val group = createCareGroup("primary-removal")
        val familyMemberId = approveActiveFamily(group, "primary-removal")
        transferPrimary(group, familyMemberId)

        mockMvc
            .perform(
                delete("/api/v1/care-groups/${group.groupId}/members/$familyMemberId")
                    .param("actorUserId", group.ownerUserId.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("REMOVED"))
            .andExpect(jsonPath("$.data.isPrimary").value(false))

        val primaryMemberIds =
            members
                .findByCareGroupIdOrderByIdAsc(group.groupId)
                .filter { it.isPrimary }
                .map { it.id }
        assertThat(primaryMemberIds).containsExactly(group.ownerMemberId)

        mockMvc
            .perform(
                get("/api/v1/care-groups/${group.groupId}/change-log")
                    .param("limit", "1"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].targetType").value("MEMBER"))
            .andExpect(jsonPath("$.data[0].field").value("is_primary"))
            .andExpect(jsonPath("$.data[0].oldValue").value(familyMemberId.toString()))
            .andExpect(jsonPath("$.data[0].newValue").value(group.ownerMemberId.toString()))
    }

    @Test
    fun `care group stays editable after the primary caregiver is removed`() {
        val group = createCareGroup("primary-stuck")
        val familyMemberId = approveActiveFamily(group, "primary-stuck")
        transferPrimary(group, familyMemberId)

        mockMvc
            .perform(
                delete("/api/v1/care-groups/${group.groupId}/members/$familyMemberId")
                    .param("actorUserId", group.ownerUserId.toString()),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                put("/api/v1/seniors/${group.seniorUserId}/meal-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "actorUserId": ${group.ownerUserId},
                          "breakfast": "08:00",
                          "lunch": "12:30",
                          "dinner": "18:30"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isOk)
        transferPrimary(group, group.ownerMemberId)
    }

    private fun transferPrimary(
        group: CreatedGroup,
        memberId: Long,
    ) {
        mockMvc
            .perform(
                patch("/api/v1/care-groups/${group.groupId}/primary")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${group.ownerUserId}, "memberId": $memberId}"""),
            ).andExpect(status().isOk)
    }

    private fun approveActiveFamily(
        group: CreatedGroup,
        suffix: String,
    ): Long {
        val token = createInviteToken(group.ownerUserId, group.groupId)
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
        val ownerMemberId: Long,
    )
}
