package xyz.stdiodh.gojjibom.caregroup.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalTime
import java.time.OffsetDateTime

@Entity
@Table(name = "meal_times")
class MealTimeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_id", nullable = false)
    var senior: UserEntity = UserEntity(),
    @Column(name = "breakfast_time", nullable = false)
    var breakfastTime: LocalTime = LocalTime.MIDNIGHT,
    @Column(name = "lunch_time", nullable = false)
    var lunchTime: LocalTime = LocalTime.MIDNIGHT,
    @Column(name = "dinner_time", nullable = false)
    var dinnerTime: LocalTime = LocalTime.MIDNIGHT,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    var updatedBy: UserEntity? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
