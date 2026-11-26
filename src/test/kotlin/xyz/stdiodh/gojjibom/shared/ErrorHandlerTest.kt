package xyz.stdiodh.gojjibom.shared

import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

class ErrorHandlerTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(FailingController())
            .setControllerAdvice(ErrorHandler())
            .build()

    @Test
    fun `unexpected exception returns a 500 envelope without the internal message`() {
        mockMvc
            .perform(get("/unexpected"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.data").hasJsonPath())
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.error.message").value("Internal server error"))
    }

    @Test
    fun `framework exceptions keep their own status`() {
        mockMvc
            .perform(get("/typed").param("id", "not-a-number"))
            .andExpect(status().isBadRequest)
        mockMvc
            .perform(post("/typed").param("id", "1"))
            .andExpect(status().isMethodNotAllowed)
        mockMvc
            .perform(get("/conflict"))
            .andExpect(status().isConflict)
    }

    @RestController
    private class FailingController {
        @GetMapping("/unexpected")
        fun unexpected(): String = error("care group member 42 has no user")

        @GetMapping("/typed")
        fun typed(
            @RequestParam id: Long,
        ): Long = id

        @GetMapping("/conflict")
        fun conflict(): String = throw ResponseStatusException(HttpStatus.CONFLICT)
    }
}
