package xyz.stdiodh.gojjibom.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
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
class AuthOtpIntegrationTest {
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

    @Test
    fun `otp request returns sent true`() {
        mockMvc
            .perform(
                post("/api/v1/auth/otp:request")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"phone": "010-1234-5678"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sent").value(true))
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `otp verify accepts a six digit code`() {
        mockMvc
            .perform(
                post("/api/v1/auth/otp:verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"phone": "010-1234-5678", "code": "123456"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.verified").value(true))
    }

    @Test
    fun `otp verify rejects a non six digit code`() {
        mockMvc
            .perform(
                post("/api/v1/auth/otp:verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"phone": "010-1234-5678", "code": "1234"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("OTP_INVALID"))
    }
}
