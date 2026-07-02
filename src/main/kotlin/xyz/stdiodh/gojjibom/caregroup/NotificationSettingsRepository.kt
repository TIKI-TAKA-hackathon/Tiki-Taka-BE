package xyz.stdiodh.gojjibom.caregroup

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingsRepository : JpaRepository<NotificationSettingsEntity, Long> {
    fun findBySeniorId(seniorId: Long): NotificationSettingsEntity?
}
