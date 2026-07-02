package xyz.stdiodh.gojjibom.caregroup

import jakarta.validation.constraints.Min
import java.time.OffsetDateTime

data class NotificationSettingsResponse(
    val seniorId: Long,
    val enabled: Boolean,
    val remindIntervalMin: Int,
    val maxRetries: Int,
    val updatedAt: OffsetDateTime?,
)

data class UpdateNotificationSettingsRequest(
    val actorUserId: Long,
    val enabled: Boolean,
    @field:Min(1)
    val remindIntervalMin: Int,
    @field:Min(0)
    val maxRetries: Int,
)
