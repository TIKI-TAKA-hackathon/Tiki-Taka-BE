package xyz.stdiodh.gojjibom.caregroup

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MealTimeRepository : JpaRepository<MealTimeEntity, Long> {
    fun findBySeniorId(seniorId: Long): MealTimeEntity?
}

interface ChangeLogRepository : JpaRepository<ChangeLogEntity, Long> {
    fun findByCareGroupIdOrderByCreatedAtDesc(
        careGroupId: Long,
        pageable: Pageable,
    ): List<ChangeLogEntity>
}
