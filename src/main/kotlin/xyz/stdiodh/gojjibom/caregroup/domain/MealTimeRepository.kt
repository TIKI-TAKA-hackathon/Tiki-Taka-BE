package xyz.stdiodh.gojjibom.caregroup.domain

import org.springframework.data.jpa.repository.JpaRepository

interface MealTimeRepository : JpaRepository<MealTimeEntity, Long> {
    fun findBySeniorId(seniorId: Long): MealTimeEntity?
}
