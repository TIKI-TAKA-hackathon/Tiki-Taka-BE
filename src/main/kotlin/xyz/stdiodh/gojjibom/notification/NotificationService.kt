package xyz.stdiodh.gojjibom.notification

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberRepository
import xyz.stdiodh.gojjibom.caregroup.CareGroupRepository
import xyz.stdiodh.gojjibom.caregroup.MemberStatus
import xyz.stdiodh.gojjibom.caregroup.requiredId
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class NotificationService(
    private val notifications: NotificationRepository,
    private val careGroups: CareGroupRepository,
    private val members: CareGroupMemberRepository,
    private val clock: Clock,
) {
    /** Membership-gated list for a senior, newest first. */
    @Transactional(readOnly = true)
    fun listForSenior(
        seniorId: Long,
        actorUserId: Long,
    ): NotificationListResponse {
        val careGroupId =
            careGroups.findBySeniorId(seniorId)?.requiredId()
                ?: throw NotificationErrors.notFound("CARE_GROUP_NOT_FOUND", "Senior does not have a care group")
        requireActiveMember(careGroupId, actorUserId)
        return toListResponse(notifications.findBySeniorIdOrderByCreatedAtDescIdDesc(seniorId))
    }

    /** BFF demo list for a care group, newest first (unauthenticated, like the board). */
    @Transactional(readOnly = true)
    fun listForCareGroup(careGroupId: Long): NotificationListResponse {
        careGroups.findByIdOrNull(careGroupId)
            ?: throw NotificationErrors.notFound("CARE_GROUP_NOT_FOUND", "Care group not found")
        return toListResponse(notifications.findByCareGroupIdOrderByCreatedAtDescIdDesc(careGroupId))
    }

    /** Marks a notification read (membership-gated on the owning care group). */
    @Transactional
    fun markRead(
        notificationId: Long,
        actorUserId: Long,
    ): NotificationView {
        val notification =
            notifications.findByIdOrNull(notificationId)
                ?: throw NotificationErrors.notFound("NOTIFICATION_NOT_FOUND", "Notification not found")
        requireActiveMember(notification.careGroupId, actorUserId)
        if (notification.readAt == null) {
            notification.readAt = OffsetDateTime.now(clock)
        }
        return toView(notification)
    }

    private fun requireActiveMember(
        careGroupId: Long,
        actorUserId: Long,
    ) {
        val isActiveMember =
            members.existsByCareGroupIdAndUserIdAndStatus(
                careGroupId = careGroupId,
                userId = actorUserId,
                status = MemberStatus.ACTIVE,
            )
        if (!isActiveMember) {
            throw NotificationErrors.forbidden(
                "CARE_GROUP_MEMBER_REQUIRED",
                "Only active care group members can access notifications",
            )
        }
    }

    private fun toListResponse(entities: List<NotificationEntity>): NotificationListResponse =
        NotificationListResponse(entities.map { toView(it) })

    private fun toView(entity: NotificationEntity): NotificationView =
        NotificationView(
            id = entity.requiredId().toString(),
            type = entity.type.name.lowercase(),
            level = entity.level,
            title = entity.title,
            body = entity.body,
            doseEventId = entity.doseEventId?.toString(),
            createdAt = entity.createdAt.format(TIMESTAMP_FORMAT),
            readAt = entity.readAt?.format(TIMESTAMP_FORMAT),
        )

    private companion object {
        private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}
