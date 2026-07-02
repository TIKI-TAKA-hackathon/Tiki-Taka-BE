package xyz.stdiodh.gojjibom.caregroup

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.stdiodh.gojjibom.shared.ApiResponse

@RestController
@RequestMapping("/api/v1")
class MealTimeController(
    private val careGroupService: CareGroupService,
) {
    @GetMapping("/seniors/{seniorId}/meal-times")
    fun getMealTimes(
        @PathVariable seniorId: Long,
    ): ApiResponse<MealTimesResponse> = ApiResponse.success(careGroupService.getMealTimes(seniorId))

    @PutMapping("/seniors/{seniorId}/meal-times")
    fun updateMealTimes(
        @PathVariable seniorId: Long,
        @Valid @RequestBody request: UpdateMealTimesRequest,
    ): ApiResponse<MealTimesResponse> = ApiResponse.success(careGroupService.updateMealTimes(seniorId, request))
}
