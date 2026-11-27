package xyz.stdiodh.gojjibom.notification.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import xyz.stdiodh.gojjibom.caregroup.domain.CareGroupMemberEntity
import xyz.stdiodh.gojjibom.caregroup.domain.CareGroupMemberRepository
import xyz.stdiodh.gojjibom.caregroup.domain.CareGroupRole
import xyz.stdiodh.gojjibom.caregroup.domain.MemberStatus
import xyz.stdiodh.gojjibom.caregroup.domain.UserEntity

class CaregiverRecipientResolverTest {
    private val members = mock(CareGroupMemberRepository::class.java)
    private val resolver = CaregiverRecipientResolver(members)

    @Test
    fun `active primary caregiver is preferred over active owner`() {
        val primary = member(id = 2, userId = 20, role = CareGroupRole.FAMILY, isPrimary = true)
        val owner = member(id = 1, userId = 10, role = CareGroupRole.OWNER)
        `when`(members.findByCareGroupIdAndIsPrimaryTrue(GROUP_ID)).thenReturn(primary)
        `when`(members.findByCareGroupIdOrderByIdAsc(GROUP_ID)).thenReturn(listOf(owner, primary))

        assertThat(resolver.resolve(GROUP_ID)?.userId).isEqualTo(20)
    }

    @Test
    fun `active social worker primary is excluded and active owner is preferred`() {
        val socialWorker =
            member(
                id = 1,
                userId = 10,
                role = CareGroupRole.SOCIAL_WORKER,
                isPrimary = true,
            )
        val owner = member(id = 2, userId = 20, role = CareGroupRole.OWNER)
        val family = member(id = 3, userId = 30, role = CareGroupRole.FAMILY)
        `when`(members.findByCareGroupIdAndIsPrimaryTrue(GROUP_ID)).thenReturn(socialWorker)
        `when`(members.findByCareGroupIdOrderByIdAsc(GROUP_ID)).thenReturn(listOf(socialWorker, owner, family))

        assertThat(resolver.resolve(GROUP_ID)?.userId).isEqualTo(20)
    }

    @Test
    fun `only active family is used when owners and social workers are ineligible`() {
        val pendingOwner =
            member(
                id = 1,
                userId = 10,
                role = CareGroupRole.OWNER,
                status = MemberStatus.PENDING,
            )
        val removedOwner =
            member(
                id = 2,
                userId = 20,
                role = CareGroupRole.OWNER,
                status = MemberStatus.REMOVED,
            )
        val socialWorker = member(id = 3, userId = 30, role = CareGroupRole.SOCIAL_WORKER)
        val family = member(id = 4, userId = 40, role = CareGroupRole.FAMILY)
        `when`(members.findByCareGroupIdAndIsPrimaryTrue(GROUP_ID)).thenReturn(null)
        `when`(members.findByCareGroupIdOrderByIdAsc(GROUP_ID))
            .thenReturn(listOf(pendingOwner, removedOwner, socialWorker, family))

        assertThat(resolver.resolve(GROUP_ID)?.userId).isEqualTo(40)

        `when`(members.findByCareGroupIdOrderByIdAsc(GROUP_ID))
            .thenReturn(listOf(pendingOwner, removedOwner, socialWorker))
        assertThat(resolver.resolve(GROUP_ID)).isNull()
    }

    private fun member(
        id: Long,
        userId: Long,
        role: CareGroupRole,
        status: MemberStatus = MemberStatus.ACTIVE,
        isPrimary: Boolean = false,
    ): CareGroupMemberEntity =
        CareGroupMemberEntity(
            id = id,
            user =
                UserEntity(
                    id = userId,
                    name = "Caregiver $userId",
                    phone = "010-$userId",
                ),
            role = role,
            status = status,
            isPrimary = isPrimary,
        )

    private companion object {
        private const val GROUP_ID = 1L
    }
}
