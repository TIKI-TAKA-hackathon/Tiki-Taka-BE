package xyz.stdiodh.gojjibom.caregroup

import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CareGroupService(
    private val users: UserRepository,
    private val careGroups: CareGroupRepository,
    private val members: CareGroupMemberRepository,
    private val inviteLinks: InviteLinkRepository,
    private val mealTimes: MealTimeRepository,
    private val changeLogs: ChangeLogRepository,
    private val mapper: CareGroupMapper,
) {
    @Transactional
    fun createCareGroup(request: CreateCareGroupRequest): CareGroupResponse {
        val senior =
            findOrCreateUser(
                name = request.senior.name,
                phone = request.senior.phone,
                userType = UserType.SENIOR,
                birthDate = request.senior.birthDate,
            )
        val seniorId = senior.requiredId()

        if (careGroups.existsBySeniorId(seniorId)) {
            throw CareGroupErrors.conflict("CARE_GROUP_EXISTS", "Senior already has a care group")
        }

        val owner =
            findOrCreateUser(
                name = request.owner.name,
                phone = request.owner.phone,
                userType = UserType.CAREGIVER,
                birthDate = null,
            )

        if (senior.requiredId() == owner.requiredId()) {
            throw CareGroupErrors.badRequest("INVALID_OWNER", "Senior and owner must be different users")
        }

        val now = now()
        val group =
            careGroups.save(
                CareGroupEntity(
                    senior = senior,
                    name = request.name.trim(),
                    createdAt = now,
                ),
            )

        members.save(
            CareGroupMemberEntity(
                careGroup = group,
                user = owner,
                role = CareGroupRole.OWNER,
                status = MemberStatus.ACTIVE,
                joinedAt = now,
                isPrimary = true,
            ),
        )

        return mapper.toResponse(group)
    }

    @Transactional(readOnly = true)
    fun getCareGroup(id: Long): CareGroupResponse = mapper.toResponse(careGroupOrThrow(id))

    @Transactional
    fun createInviteLink(
        careGroupId: Long,
        request: CreateInviteLinkRequest,
    ): InviteLinkResponse {
        val group = careGroupOrThrow(careGroupId)
        requireActiveOwner(careGroupId, request.ownerUserId)

        val now = now()
        inviteLinks.findByCareGroupIdAndRevokedAtIsNull(careGroupId).forEach {
            it.revokedAt = now
        }

        val invite =
            inviteLinks.save(
                InviteLinkEntity(
                    careGroup = group,
                    token = UUID.randomUUID().toString(),
                    expiresAt = now.plusHours(INVITE_TTL_HOURS),
                    maxUses = request.maxUses,
                    useCount = 0,
                    createdBy = users.getReferenceById(request.ownerUserId),
                ),
            )

        return mapper.toResponse(invite)
    }

    @Transactional
    fun acceptInvite(
        token: String,
        request: AcceptInviteRequest,
    ): CareGroupMemberResponse {
        val invite =
            inviteLinks.findByToken(token)
                ?: throw CareGroupErrors.notFound("INVITE_NOT_FOUND", "Invite link not found")

        CareGroupRules.inviteAcceptanceFailure(invite, request.role, OffsetDateTime.now())?.let { throw it }

        val caregiver =
            findOrCreateUser(
                name = request.name,
                phone = request.phone,
                userType = UserType.CAREGIVER,
                birthDate = null,
            )
        val groupId = invite.careGroup.requiredId()
        val caregiverId = caregiver.requiredId()
        val existingMember = members.findByCareGroupIdAndUserId(groupId, caregiverId)
        val member = CareGroupRules.pendingInviteMember(existingMember, invite, caregiver, request.role)

        invite.useCount += 1
        return mapper.toResponse(members.save(member))
    }

    @Transactional
    fun updateMember(
        careGroupId: Long,
        memberId: Long,
        request: UpdateMemberRequest,
    ): CareGroupMemberResponse {
        requireActiveOwner(careGroupId, request.actorUserId)

        val member =
            members.findByIdAndCareGroupId(memberId, careGroupId)
                ?: throw CareGroupErrors.notFound("MEMBER_NOT_FOUND", "Care group member not found")

        CareGroupRules.updateFailure(member, request)?.let { throw it }
        CareGroupRules.applyUpdate(member, request, OffsetDateTime.now())

        return mapper.toResponse(member)
    }

    @Transactional
    fun removeMember(
        careGroupId: Long,
        memberId: Long,
        actorUserId: Long,
    ): CareGroupMemberResponse =
        updateMember(
            careGroupId = careGroupId,
            memberId = memberId,
            request = UpdateMemberRequest(actorUserId = actorUserId, status = MemberStatus.REMOVED, role = null),
        )

    @Transactional(readOnly = true)
    fun getMealTimes(seniorId: Long): MealTimesResponse {
        val mealTime =
            mealTimes.findBySeniorId(seniorId)
                ?: throw CareGroupErrors.notFound("MEAL_TIMES_NOT_FOUND", "Meal times are not set for this senior")
        return mapper.toResponse(mealTime)
    }

    @Transactional
    fun updateMealTimes(
        seniorId: Long,
        request: UpdateMealTimesRequest,
    ): MealTimesResponse {
        val group =
            careGroups.findBySeniorId(seniorId)
                ?: throw CareGroupErrors.notFound("CARE_GROUP_NOT_FOUND", "Senior does not have a care group")
        val groupId = group.requiredId()
        requirePrimary(groupId, request.actorUserId)

        val now = now()
        val existing = mealTimes.findBySeniorId(seniorId)
        val logs =
            CareGroupRules.mealTimeChangeLog(
                careGroupId = groupId,
                actorUserId = request.actorUserId,
                seniorId = seniorId,
                existing = existing,
                breakfast = request.breakfast,
                lunch = request.lunch,
                dinner = request.dinner,
                currentTime = now,
            )

        val actor = users.getReferenceById(request.actorUserId)
        val mealTime =
            if (existing == null) {
                MealTimeEntity(
                    senior = group.senior,
                    breakfastTime = request.breakfast,
                    lunchTime = request.lunch,
                    dinnerTime = request.dinner,
                    updatedBy = actor,
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                existing.breakfastTime = request.breakfast
                existing.lunchTime = request.lunch
                existing.dinnerTime = request.dinner
                existing.updatedBy = actor
                existing.updatedAt = now
                existing
            }
        val saved = mealTimes.save(mealTime)

        if (logs.isNotEmpty()) {
            changeLogs.saveAll(logs)
        }

        return mapper.toResponse(saved)
    }

    @Transactional
    fun transferPrimary(
        careGroupId: Long,
        request: UpdatePrimaryRequest,
    ): CareGroupResponse {
        requirePrimary(careGroupId, request.actorUserId)

        val target =
            members.findByIdAndCareGroupId(request.memberId, careGroupId)
                ?: throw CareGroupErrors.notFound("MEMBER_NOT_FOUND", "Care group member not found")
        CareGroupRules.transferPrimaryFailure(target)?.let { throw it }

        val current = members.findByCareGroupIdAndIsPrimaryTrue(careGroupId)
        // Clear the old primary and flush before setting the new one so the partial
        // unique index (one is_primary=TRUE per group) never sees two live primaries.
        if (current != null && current.requiredId() != target.requiredId()) {
            current.isPrimary = false
            members.saveAndFlush(current)
        }
        target.isPrimary = true

        changeLogs.save(
            ChangeLogEntity(
                careGroupId = careGroupId,
                actorUserId = request.actorUserId,
                targetType = ChangeTargetType.MEMBER,
                targetId = target.requiredId(),
                field = "is_primary",
                oldValue = current?.requiredId()?.toString(),
                newValue = target.requiredId().toString(),
                createdAt = now(),
            ),
        )

        return mapper.toResponse(careGroupOrThrow(careGroupId))
    }

    @Transactional(readOnly = true)
    fun getChangeLog(
        careGroupId: Long,
        limit: Int,
    ): List<ChangeLogResponse> {
        careGroupOrThrow(careGroupId)
        return changeLogs
            .findByCareGroupIdOrderByCreatedAtDesc(careGroupId, PageRequest.of(0, limit))
            .map { mapper.toResponse(it) }
    }

    private fun requirePrimary(
        careGroupId: Long,
        userId: Long,
    ) {
        careGroupOrThrow(careGroupId)
        val isPrimary =
            members.existsByCareGroupIdAndUserIdAndStatusAndIsPrimaryTrue(
                careGroupId = careGroupId,
                userId = userId,
                status = MemberStatus.ACTIVE,
            )
        if (!isPrimary) {
            throw CareGroupErrors.forbidden("PRIMARY_REQUIRED", "Only the primary caregiver can perform this action")
        }
    }

    private fun findOrCreateUser(
        name: String,
        phone: String,
        userType: UserType,
        birthDate: LocalDate?,
    ): UserEntity {
        val normalizedPhone = phone.trim()
        val existing = users.findByPhone(normalizedPhone)
        if (existing != null) {
            if (existing.userType != userType) {
                throw CareGroupErrors.conflict("USER_TYPE_CONFLICT", "Phone is already used by another user type")
            }
            return existing
        }

        return users.save(
            UserEntity(
                userType = userType,
                name = name.trim(),
                phone = normalizedPhone,
                birthDate = birthDate,
                createdAt = now(),
            ),
        )
    }

    private fun requireActiveOwner(
        careGroupId: Long,
        userId: Long,
    ) {
        careGroupOrThrow(careGroupId)
        val isOwner =
            members.existsByCareGroupIdAndUserIdAndRoleAndStatus(
                careGroupId = careGroupId,
                userId = userId,
                role = CareGroupRole.OWNER,
                status = MemberStatus.ACTIVE,
            )

        if (!isOwner) {
            throw CareGroupErrors.forbidden("OWNER_REQUIRED", "Only an active owner can perform this action")
        }
    }

    private fun careGroupOrThrow(id: Long): CareGroupEntity =
        careGroups.findByIdOrNull(id)
            ?: throw CareGroupErrors.notFound("CARE_GROUP_NOT_FOUND", "Care group not found")

    private fun now(): OffsetDateTime = OffsetDateTime.now()

    private companion object {
        private const val INVITE_TTL_HOURS = 24L
    }
}
