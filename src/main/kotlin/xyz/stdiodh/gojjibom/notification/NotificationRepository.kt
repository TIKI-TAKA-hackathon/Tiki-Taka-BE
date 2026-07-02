package xyz.stdiodh.gojjibom.notification

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<NotificationEntity, Long> {
    fun findByCareGroupIdOrderByCreatedAtDescIdDesc(careGroupId: Long): List<NotificationEntity>

    fun findBySeniorIdOrderByCreatedAtDescIdDesc(seniorId: Long): List<NotificationEntity>

    fun existsByDoseEventIdAndTypeAndLevel(
        doseEventId: Long,
        type: NotificationType,
        level: Int,
    ): Boolean

    /** Latest unresolved MISSED/ESCALATION for a senior (read_at IS NULL), newest first. */
    fun findFirstBySeniorIdAndTypeInAndReadAtIsNullOrderByCreatedAtDescIdDesc(
        seniorId: Long,
        types: Collection<NotificationType>,
    ): NotificationEntity?

    fun countByDoseEventIdAndType(
        doseEventId: Long,
        type: NotificationType,
    ): Int
}
