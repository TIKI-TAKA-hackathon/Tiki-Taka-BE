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
        )

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
