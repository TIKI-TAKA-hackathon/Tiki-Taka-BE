package xyz.stdiodh.gojjibom.prescription

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.stdiodh.gojjibom.shared.ApiResponse

@RestController
@RequestMapping("/api/v1")
class PrescriptionController(
    private val prescriptionService: PrescriptionService,
) {
    @PostMapping("/seniors/{seniorId}/prescriptions")
    fun createPrescription(
        @PathVariable seniorId: Long,
        @Valid @RequestBody request: CreatePrescriptionRequest,
    ): ResponseEntity<ApiResponse<PrescriptionResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(prescriptionService.createPrescription(seniorId, request)))

    @GetMapping("/seniors/{seniorId}/dose-schedules")
    fun getDoseSchedules(
        @PathVariable seniorId: Long,
        @RequestParam actorUserId: Long,
    ): ApiResponse<DoseScheduleListResponse> {
        val schedules = prescriptionService.getDoseSchedules(seniorId, actorUserId)
        return ApiResponse.success(schedules)
    }
}
