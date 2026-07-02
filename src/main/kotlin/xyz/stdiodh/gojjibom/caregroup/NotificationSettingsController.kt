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
class NotificationSettingsController(
    private val careGroupService: CareGroupService,
) {
    @GetMapping("/seniors/{seniorId}/notification-settings")
    fun getNotificationSettings(
        @PathVariable seniorId: Long,
    ): ApiResponse<NotificationSettingsResponse> {
        val settings = careGroupService.getNotificationSettings(seniorId)
        return ApiResponse.success(settings)
    }

    @PutMapping("/seniors/{seniorId}/notification-settings")
    fun updateNotificationSettings(
        @PathVariable seniorId: Long,
        @Valid @RequestBody request: UpdateNotificationSettingsRequest,
    ): ApiResponse<NotificationSettingsResponse> {
        val settings = careGroupService.updateNotificationSettings(seniorId, request)
        return ApiResponse.success(settings)
    }
}
