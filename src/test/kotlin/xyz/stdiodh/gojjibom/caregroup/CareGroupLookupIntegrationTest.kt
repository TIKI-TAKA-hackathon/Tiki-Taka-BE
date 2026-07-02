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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CareGroupLookupIntegrationTest {
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
    fun `lookup by senior phone returns care group summary`() {
        val seniorPhone = "010-lookup-0001"
        val created = createCareGroup("lookup", seniorPhone)

        mockMvc
            .perform(get("/api/v1/care-groups:lookup").param("seniorPhone", seniorPhone))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.careGroupId").value(created.path("id").asLong()))
            .andExpect(jsonPath("$.data.seniorId").value(created.path("senior").path("id").asLong()))
            .andExpect(jsonPath("$.data.seniorName").value("Senior lookup"))
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `lookup by unknown senior phone returns 404`() {
        mockMvc
            .perform(get("/api/v1/care-groups:lookup").param("seniorPhone", "010-nobody-9999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("CARE_GROUP_NOT_FOUND"))
    }

    private fun createCareGroup(
        suffix: String,
        seniorPhone: String,
    ): JsonNode {
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
                                "phone": "$seniorPhone",
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

        return objectMapper.readTree(result.response.contentAsString).path("data")
    }
}
