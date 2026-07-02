package xyz.stdiodh.gojjibom.dose

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import xyz.stdiodh.gojjibom.caregroup.CareGroupEntity
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberRepository
import xyz.stdiodh.gojjibom.caregroup.CareGroupRepository
import xyz.stdiodh.gojjibom.caregroup.MemberStatus
import xyz.stdiodh.gojjibom.caregroup.UserRepository
import xyz.stdiodh.gojjibom.caregroup.UserType
import xyz.stdiodh.gojjibom.caregroup.requiredId
import xyz.stdiodh.gojjibom.media.ImageService
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class DoseService(
    private val users: UserRepository,
    private val careGroups: CareGroupRepository,
    private val members: CareGroupMemberRepository,
    private val doseEvents: DoseEventRepository,
    private val generator: DoseEventGenerator,
    private val imageService: ImageService,
    private val mapper: DoseMapper,
    private val clock: Clock,
) {
    @Transactional
    fun listDoses(
        seniorId: Long,
        date: LocalDate,
        actorUserId: Long,
    ): DoseEventListResponse {
        seniorOrThrow(seniorId)
        val careGroup = careGroupOrThrow(seniorId)
        requireActiveMember(careGroup.requiredId(), actorUserId)

        generator.ensureEventsFor(seniorId, date)
        val events = doseEvents.findBySeniorIdAndScheduledDate(seniorId, date)
        return mapper.toListResponse(seniorId, date, events)
    }

    @Transactional(readOnly = true)
    fun getDose(
        id: Long,
        actorUserId: Long,
    ): DoseEventResponse {
        val event =
            doseEvents.findDetailById(id)
                ?: throw DoseErrors.notFound("DOSE_EVENT_NOT_FOUND", "Dose event not found")
        val careGroup = careGroupOrThrow(event.senior.requiredId())
        requireActiveMember(careGroup.requiredId(), actorUserId)
        return mapper.toResponse(event)
    }

    @Transactional
    fun confirm(
        id: Long,
        request: ConfirmDoseRequest,
    ): DoseEventResponse {
        val event =
            doseEvents.findDetailById(id)
                ?: throw DoseErrors.notFound("DOSE_EVENT_NOT_FOUND", "Dose event not found")
        val careGroup = careGroupOrThrow(event.senior.requiredId())
        val careGroupId = careGroup.requiredId()
        requireActiveMember(careGroupId, request.actorUserId)

        if (event.status == DoseEventStatus.TAKEN) {
            return mapper.toResponse(event)
        }

        request.imageId?.let { imageId ->
            imageService.assertDoseEventImage(imageId, event.requiredId(), careGroupId)
            event.photoImageId = imageId
        }

        event.status = DoseEventStatus.TAKEN
        event.confirmedAt = OffsetDateTime.now(clock)
        event.confirmMethod = request.method
        event.confirmedById = request.actorUserId

        return mapper.toResponse(event)
    }

    private fun seniorOrThrow(seniorId: Long) {
        val senior =
            users.findById(seniorId).orElseThrow {
                DoseErrors.notFound("SENIOR_NOT_FOUND", "Senior not found")
            }
        if (senior.userType != UserType.SENIOR) {
            throw DoseErrors.badRequest("SENIOR_REQUIRED", "Dose target must be a senior")
        }
    }

    private fun careGroupOrThrow(seniorId: Long): CareGroupEntity =
        careGroups.findBySeniorId(seniorId)
            ?: throw DoseErrors.badRequest("CARE_GROUP_REQUIRED", "Senior must have a care group")

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
            throw DoseErrors.forbidden(
                "CARE_GROUP_MEMBER_REQUIRED",
                "Only active care group members can access dose events",
            )
        }
    }
}
