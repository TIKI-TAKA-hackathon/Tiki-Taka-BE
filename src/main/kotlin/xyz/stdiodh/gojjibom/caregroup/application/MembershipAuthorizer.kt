package xyz.stdiodh.gojjibom.caregroup.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class MembershipAuthorizer(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun requireActiveMember(
        careGroupId: Long,
        actorUserId: Long,
    ) {
        val count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM care_group_members
                WHERE care_group_id = ?
                  AND user_id = ?
                  AND status = 'ACTIVE'
                """.trimIndent(),
                Long::class.java,
                careGroupId,
                actorUserId,
            ) ?: 0L

        if (count == 0L) {
            throw CareGroupErrors.forbidden("CARE_GROUP_MEMBER_REQUIRED", "Active care group membership is required")
        }
    }
}
