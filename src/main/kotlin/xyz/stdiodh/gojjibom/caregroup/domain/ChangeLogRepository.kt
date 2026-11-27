package xyz.stdiodh.gojjibom.caregroup.domain

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ChangeLogRepository : JpaRepository<ChangeLogEntity, Long> {
    fun findByCareGroupIdOrderByCreatedAtDesc(
        careGroupId: Long,
        pageable: Pageable,
    ): List<ChangeLogEntity>
}
