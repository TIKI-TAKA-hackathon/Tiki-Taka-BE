package xyz.stdiodh.gojjibom.notification

/**
 * BFF-style notification view: lowercase enum type, string ids, ISO timestamps.
 * Mirrors the presentation-tier contract (see PresentationDtos) so the FE
 * consumes ids as strings and enums as lowercase.
 */
data class NotificationView(
    val id: String,
    val type: String,
    val level: Int,
    val title: String,
    val body: String,
    val doseEventId: String?,
    val createdAt: String,
    val readAt: String?,
)

data class NotificationListResponse(
    val notifications: List<NotificationView>,
)

data class MarkNotificationReadRequest(
    val actorUserId: Long,
)
