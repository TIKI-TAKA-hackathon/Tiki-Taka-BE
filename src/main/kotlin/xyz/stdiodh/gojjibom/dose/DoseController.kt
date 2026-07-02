package xyz.stdiodh.gojjibom.dose

import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.stdiodh.gojjibom.shared.ApiResponse
import java.time.Clock
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1")
class DoseController(
    private val doseService: DoseService,
    private val clock: Clock,
) {
    @GetMapping("/seniors/{seniorId}/doses")
    fun listDoses(
        @PathVariable seniorId: Long,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?,
        @RequestParam actorUserId: Long,
    ): ApiResponse<DoseEventListResponse> {
        val resolvedDate = date ?: LocalDate.now(clock)
        return ApiResponse.success(doseService.listDoses(seniorId, resolvedDate, actorUserId))
    }

    @GetMapping("/dose-events/{id}")
    fun getDose(
        @PathVariable id: Long,
        @RequestParam actorUserId: Long,
    ): ApiResponse<DoseEventResponse> = ApiResponse.success(doseService.getDose(id, actorUserId))

    @PostMapping("/dose-events/{id}:confirm")
    fun confirm(
        @PathVariable id: Long,
        @Valid @RequestBody request: ConfirmDoseRequest,
    ): ApiResponse<DoseEventResponse> = ApiResponse.success(doseService.confirm(id, request))

    @PatchMapping("/dose-events/{id}/photo:review")
    fun reviewPhoto(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReviewPhotoRequest,
    ): ApiResponse<DoseEventResponse> = ApiResponse.success(doseService.reviewPhoto(id, request))
}
