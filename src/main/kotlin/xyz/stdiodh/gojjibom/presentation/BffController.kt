package xyz.stdiodh.gojjibom.presentation

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.stdiodh.gojjibom.shared.ApiResponse
import java.time.Clock
import java.time.LocalDate

/**
 * FE-shaped BFF aggregate endpoints. Unauthenticated demo (SYNC-PLAN §4-7);
 * date defaults to today in Asia/Seoul via the injected Clock.
 */
@RestController
@RequestMapping("/api/v1")
class BffController(
    private val bffService: BffService,
    private val clock: Clock,
) {
    @GetMapping("/senior/today")
    fun seniorToday(
        @RequestParam seniorId: Long,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?,
    ): ApiResponse<SeniorDay> {
        val resolvedDate = date ?: LocalDate.now(clock)
        return ApiResponse.success(bffService.seniorToday(seniorId, resolvedDate))
    }

    @GetMapping("/care-groups/{id}/board")
    fun board(
        @PathVariable id: Long,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?,
    ): ApiResponse<CaregiverBoard> {
        val resolvedDate = date ?: LocalDate.now(clock)
        return ApiResponse.success(bffService.caregiverBoard(id, resolvedDate))
    }
}
