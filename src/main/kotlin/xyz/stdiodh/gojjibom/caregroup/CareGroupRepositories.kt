package xyz.stdiodh.gojjibom.caregroup

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByPhone(phone: String): UserEntity?
}

interface CareGroupRepository : JpaRepository<CareGroupEntity, Long> {
    fun existsBySeniorId(seniorId: Long): Boolean

    fun findBySeniorId(seniorId: Long): CareGroupEntity?
}

interface CareGroupMemberRepository : JpaRepository<CareGroupMemberEntity, Long> {
    fun existsByCareGroupIdAndUserIdAndRoleAndStatus(
        careGroupId: Long,
        userId: Long,
        role: CareGroupRole,
        status: MemberStatus,
    ): Boolean

    fun existsByCareGroupIdAndUserIdAndStatus(
        careGroupId: Long,
        userId: Long,
        status: MemberStatus,
    ): Boolean

    fun existsByCareGroupIdAndUserIdAndStatusAndIsPrimaryTrue(
        careGroupId: Long,
        userId: Long,
        status: MemberStatus,
    ): Boolean

    fun findByCareGroupIdAndIsPrimaryTrue(careGroupId: Long): CareGroupMemberEntity?

    fun findByCareGroupIdOrderByIdAsc(careGroupId: Long): List<CareGroupMemberEntity>

    fun findByCareGroupIdAndUserId(
        careGroupId: Long,
        userId: Long,
    ): CareGroupMemberEntity?

    fun findByIdAndCareGroupId(
        id: Long,
        careGroupId: Long,
    ): CareGroupMemberEntity?
}

interface InviteLinkRepository : JpaRepository<InviteLinkEntity, Long> {
    fun findByToken(token: String): InviteLinkEntity?

    fun findByCareGroupIdAndRevokedAtIsNull(careGroupId: Long): List<InviteLinkEntity>
}
