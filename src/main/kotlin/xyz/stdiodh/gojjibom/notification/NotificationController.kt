package xyz.stdiodh.gojjibom.notification

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.stdiodh.gojjibom.shared.ApiResponse

@RestController
@RequestMapping("/api/v1")
class NotificationController(
    private val notificationService: NotificationService,
) {
    @GetMapping("/seniors/{seniorId}/notifications")
    fun listForSenior(
        @PathVariable seniorId: Long,
        @RequestParam actorUserId: Long,
    ): ApiResponse<NotificationListResponse> {
        val list = notificationService.listForSenior(seniorId, actorUserId)
        return ApiResponse.success(list)
    }

    @GetMapping("/care-groups/{id}/notifications")
    fun listForCareGroup(
        @PathVariable id: Long,
    ): ApiResponse<NotificationListResponse> = ApiResponse.success(notificationService.listForCareGroup(id))

    @PatchMapping("/notifications/{id}:read")
    fun markRead(
        @PathVariable id: Long,
        @Valid @RequestBody request: MarkNotificationReadRequest,
    ): ApiResponse<NotificationView> = ApiResponse.success(notificationService.markRead(id, request.actorUserId))
}
