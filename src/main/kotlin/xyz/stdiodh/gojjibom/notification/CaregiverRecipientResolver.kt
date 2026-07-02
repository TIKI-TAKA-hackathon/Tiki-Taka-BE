package xyz.stdiodh.gojjibom.notification

import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberEntity
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberRepository
import xyz.stdiodh.gojjibom.caregroup.CareGroupRole
import xyz.stdiodh.gojjibom.caregroup.MemberStatus

/** A caregiver chosen to receive a dispatched notification. */
data class CaregiverRecipient(
    val userId: Long,
    val name: String,
    val phone: String,
)

/**
 * Picks the caregiver who should receive a care group's dispatched notification.
 *
 * Preference order:
 *   1. the 대표자 (primary, `is_primary = TRUE`) if active;
 *   2. otherwise the earliest-joined active OWNER;
 *   3. otherwise the earliest-joined active FAMILY member.
 *
 * SOCIAL_WORKER and non-ACTIVE members are never chosen. Returns null when the group
 * has no eligible caregiver (e.g. only a removed/pending member) — the caller then
 * records the notification without dispatching.
 */
@Component
class CaregiverRecipientResolver(
    private val members: CareGroupMemberRepository,
) {
    fun resolve(careGroupId: Long): CaregiverRecipient? {
        val primary = members.findByCareGroupIdAndIsPrimaryTrue(careGroupId)
        if (primary != null && primary.status == MemberStatus.ACTIVE) {
            return primary.toRecipient()
        }

        val active = members.findByCareGroupIdOrderByIdAsc(careGroupId).filter { it.status == MemberStatus.ACTIVE }
        val fallback =
            active.firstOrNull { it.role == CareGroupRole.OWNER }
                ?: active.firstOrNull { it.role == CareGroupRole.FAMILY }
        return fallback?.toRecipient()
    }

    private fun CareGroupMemberEntity.toRecipient(): CaregiverRecipient =
        CaregiverRecipient(
            userId = user.id ?: error("care group member user id is not assigned"),
            name = user.name,
            phone = user.phone,
        )
}
