package xyz.stdiodh.gojjibom.caregroup.domain

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingsRepository : JpaRepository<NotificationSettingsEntity, Long> {
    fun findBySeniorId(seniorId: Long): NotificationSettingsEntity?
}
