package xyz.stdiodh.gojjibom.caregroup

import xyz.stdiodh.gojjibom.shared.ApiException
import java.time.OffsetDateTime

object CareGroupRules {
    fun inviteAcceptanceFailure(
        invite: InviteLinkEntity,
        role: CareGroupRole,
        currentTime: OffsetDateTime,
    ): ApiException? {
        val maxUses = invite.maxUses
        return when {
            role == CareGroupRole.OWNER ->
                CareGroupErrors.badRequest("INVALID_MEMBER_ROLE", "Invitees cannot join as OWNER")
            invite.revokedAt != null ->
                CareGroupErrors.gone("INVITE_REVOKED", "Invite link has been revoked")
            !invite.expiresAt.isAfter(currentTime) ->
                CareGroupErrors.gone("INVITE_EXPIRED", "Invite link has expired")
            maxUses != null && invite.useCount >= maxUses ->
                CareGroupErrors.conflict("INVITE_EXHAUSTED", "Invite link has no remaining uses")
            else -> null
        }
    }

    fun updateFailure(
        member: CareGroupMemberEntity,
        request: UpdateMemberRequest,
    ): ApiException? =
        when {
            request.status == null && request.role == null ->
                CareGroupErrors.badRequest("INVALID_MEMBER_UPDATE", "Either status or role must be provided")
            member.role == CareGroupRole.OWNER ->
                CareGroupErrors.badRequest("OWNER_MEMBER_IMMUTABLE", "Owner membership cannot be changed")
            request.role == CareGroupRole.OWNER ->
                CareGroupErrors.badRequest("INVALID_MEMBER_ROLE", "Member role cannot be changed to OWNER")
            request.status == MemberStatus.PENDING ->
                CareGroupErrors.badRequest("INVALID_MEMBER_STATUS", "Member status cannot be changed to PENDING")
            else -> null
        }

    fun applyUpdate(
        member: CareGroupMemberEntity,
        request: UpdateMemberRequest,
        currentTime: OffsetDateTime,
    ) {
        request.role?.let { member.role = it }
        request.status?.let {
            member.status = it
            if (it == MemberStatus.ACTIVE && member.joinedAt == null) {
                member.joinedAt = currentTime
            }
        }
    }

    fun pendingInviteMember(
        existingMember: CareGroupMemberEntity?,
        invite: InviteLinkEntity,
        caregiver: UserEntity,
        role: CareGroupRole,
    ): CareGroupMemberEntity =
        when (existingMember?.status) {
            MemberStatus.ACTIVE,
            MemberStatus.PENDING,
            -> throw CareGroupErrors.conflict("MEMBER_ALREADY_EXISTS", "User is already a care group member")

            MemberStatus.REMOVED -> {
                existingMember.role = role
                existingMember.status = MemberStatus.PENDING
                existingMember.invitedBy = invite.createdBy
                existingMember.joinedAt = null
                existingMember
            }

            null ->
                CareGroupMemberEntity(
                    careGroup = invite.careGroup,
                    user = caregiver,
                    role = role,
                    status = MemberStatus.PENDING,
                    invitedBy = invite.createdBy,
                    joinedAt = null,
                )
        }
}
