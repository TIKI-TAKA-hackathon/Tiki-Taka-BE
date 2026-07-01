package xyz.stdiodh.gojjibom.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class HealthController {
    @GetMapping("/api/health")
    fun health(): Map<String, Any> =
        mapOf(
            "status" to "UP",
            "service" to "gojjibom-api",
            "time" to Instant.now().toString(),
        )
}
