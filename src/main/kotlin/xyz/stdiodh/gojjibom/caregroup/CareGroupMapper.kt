package xyz.stdiodh.gojjibom.caregroup

import org.springframework.stereotype.Component

@Component
class CareGroupMapper(
    private val members: CareGroupMemberRepository,
) {
    fun toResponse(careGroup: CareGroupEntity): CareGroupResponse {
        val groupId = careGroup.requiredId()
        return CareGroupResponse(
            id = groupId,
            name = careGroup.name,
            senior = toSummaryResponse(careGroup.senior),
            members = members.findByCareGroupIdOrderByIdAsc(groupId).map { toResponse(it) },
        )
    }

    fun toResponse(member: CareGroupMemberEntity): CareGroupMemberResponse =
        CareGroupMemberResponse(
            id = member.requiredId(),
            user = toSummaryResponse(member.user),
            role = member.role,
            status = member.status,
            joinedAt = member.joinedAt,
            isPrimary = member.isPrimary,
            viewerOnly = !member.isPrimary,
        )

    fun toResponse(mealTimes: MealTimeEntity): MealTimesResponse =
        MealTimesResponse(
            seniorId = mealTimes.senior.requiredId(),
            breakfast = mealTimes.breakfastTime,
            lunch = mealTimes.lunchTime,
            dinner = mealTimes.dinnerTime,
            updatedAt = mealTimes.updatedAt,
        )

    fun toResponse(settings: NotificationSettingsEntity): NotificationSettingsResponse =
        NotificationSettingsResponse(
            seniorId = settings.senior.requiredId(),
            enabled = settings.enabled,
            remindIntervalMin = settings.remindIntervalMin,
            maxRetries = settings.maxRetries,
            updatedAt = settings.updatedAt,
        )

    fun defaultNotificationSettings(seniorId: Long): NotificationSettingsResponse =
        NotificationSettingsResponse(
            seniorId = seniorId,
            enabled = true,
            remindIntervalMin = NotificationSettingsEntity.DEFAULT_REMIND_INTERVAL_MIN,
            maxRetries = NotificationSettingsEntity.DEFAULT_MAX_RETRIES,
            updatedAt = null,
        )

    fun toResponse(log: ChangeLogEntity): ChangeLogResponse =
        ChangeLogResponse(
            id = log.requiredId(),
            actorUserId = log.actorUserId,
            targetType = log.targetType,
            targetId = log.targetId,
            field = log.field,
            oldValue = log.oldValue,
            newValue = log.newValue,
            createdAt = log.createdAt,
        )

    fun circleOf(members: List<CareGroupMemberEntity>): CareCircleResponse {
        val active = members.filter { it.status == MemberStatus.ACTIVE }
        val family = active.count { it.role == CareGroupRole.OWNER || it.role == CareGroupRole.FAMILY }
        val social = active.count { it.role == CareGroupRole.SOCIAL_WORKER }
        return CareCircleResponse(family = family, social = social)
    }

    fun toResponse(inviteLink: InviteLinkEntity): InviteLinkResponse =
        InviteLinkResponse(
            id = inviteLink.requiredId(),
            token = inviteLink.token,
            expiresAt = inviteLink.expiresAt,
            maxUses = inviteLink.maxUses,
            useCount = inviteLink.useCount,
        )

    private fun toSummaryResponse(user: UserEntity): UserSummaryResponse =
        UserSummaryResponse(
            id = user.requiredId(),
            name = user.name,
            userType = user.userType,
        )
}
