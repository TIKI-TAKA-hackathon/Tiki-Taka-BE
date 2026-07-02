package xyz.stdiodh.gojjibom.caregroup

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

data class CreateCareGroupRequest(
    @field:NotBlank
    val name: String,
    @field:Valid
    val senior: CreateSeniorRequest,
    @field:Valid
    val owner: CreateOwnerRequest,
)

data class CreateSeniorRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val phone: String,
    val birthDate: LocalDate?,
)

data class CreateOwnerRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val phone: String,
)

data class CreateInviteLinkRequest(
    val ownerUserId: Long,
    @field:Min(1)
    val maxUses: Int? = 1,
)

data class AcceptInviteRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val phone: String,
    val role: CareGroupRole,
)

data class UpdateMemberRequest(
    val actorUserId: Long,
    val status: MemberStatus?,
    val role: CareGroupRole?,
)

data class UserSummaryResponse(
    val id: Long,
    val name: String,
    val userType: UserType,
)

data class CareGroupMemberResponse(
    val id: Long,
    val user: UserSummaryResponse,
    val role: CareGroupRole,
    val status: MemberStatus,
    val joinedAt: OffsetDateTime?,
    @get:JsonProperty("isPrimary")
    val isPrimary: Boolean,
    val viewerOnly: Boolean,
)

data class UpdatePrimaryRequest(
    val actorUserId: Long,
    val memberId: Long,
)

data class MealTimesResponse(
    val seniorId: Long,
    val breakfast: LocalTime,
    val lunch: LocalTime,
    val dinner: LocalTime,
    val updatedAt: OffsetDateTime,
)

data class UpdateMealTimesRequest(
    val actorUserId: Long,
    @field:NotNull
    val breakfast: LocalTime,
    @field:NotNull
    val lunch: LocalTime,
    @field:NotNull
    val dinner: LocalTime,
)

data class ChangeLogResponse(
    val id: Long,
    val actorUserId: Long,
    val targetType: ChangeTargetType,
    val targetId: Long?,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val createdAt: OffsetDateTime,
)

data class CareCircleResponse(
    val family: Int,
    val social: Int,
)

data class CareGroupResponse(
    val id: Long,
    val name: String,
    val senior: UserSummaryResponse,
    val members: List<CareGroupMemberResponse>,
)

data class CareGroupLookupResponse(
    val careGroupId: Long,
    val seniorId: Long,
    val seniorName: String,
)

data class InviteLinkResponse(
    val id: Long,
    val token: String,
    val expiresAt: OffsetDateTime,
    val maxUses: Int?,
    val useCount: Int,
)
