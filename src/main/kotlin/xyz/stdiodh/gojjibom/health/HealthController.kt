package xyz.stdiodh.gojjibom.health

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Tag(name = "Health")
@RestController
class HealthController {
    @Operation(summary = "Check API health")
    @GetMapping("/api/health")
    fun health(): Map<String, Any> =
        mapOf(
            "status" to "UP",
            "service" to "gojjibom-api",
            "time" to Instant.now().toString(),
        )
}
