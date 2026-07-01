package xyz.stdiodh.gojjibom.caregroup

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.stdiodh.gojjibom.shared.ApiResponse

@RestController
@RequestMapping("/api/v1")
class CareGroupController(
    private val careGroupService: CareGroupService,
) {
    @PostMapping("/care-groups")
    fun createCareGroup(
        @Valid @RequestBody request: CreateCareGroupRequest,
    ): ResponseEntity<ApiResponse<CareGroupResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(careGroupService.createCareGroup(request)))

    @GetMapping("/care-groups/{id}")
    fun getCareGroup(
        @PathVariable id: Long,
    ): ApiResponse<CareGroupResponse> = ApiResponse.success(careGroupService.getCareGroup(id))

    @PostMapping("/care-groups/{id}/invite-links")
    fun createInviteLink(
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateInviteLinkRequest,
    ): ResponseEntity<ApiResponse<InviteLinkResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(careGroupService.createInviteLink(id, request)))

    @PostMapping("/invites/{token}:accept")
    fun acceptInvite(
        @PathVariable token: String,
        @Valid @RequestBody request: AcceptInviteRequest,
    ): ResponseEntity<ApiResponse<CareGroupMemberResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(careGroupService.acceptInvite(token, request)))

    @PatchMapping("/care-groups/{id}/members/{memberId}")
    fun updateMember(
        @PathVariable id: Long,
        @PathVariable memberId: Long,
        @RequestBody request: UpdateMemberRequest,
    ): ApiResponse<CareGroupMemberResponse> {
        val member = careGroupService.updateMember(id, memberId, request)
        return ApiResponse.success(member)
    }

    @DeleteMapping("/care-groups/{id}/members/{memberId}")
    fun removeMember(
        @PathVariable id: Long,
        @PathVariable memberId: Long,
        @RequestParam actorUserId: Long,
    ): ApiResponse<CareGroupMemberResponse> {
        val member = careGroupService.removeMember(id, memberId, actorUserId)
        return ApiResponse.success(member)
    }
}
