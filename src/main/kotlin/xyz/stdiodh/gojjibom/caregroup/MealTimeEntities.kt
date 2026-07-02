package xyz.stdiodh.gojjibom.caregroup

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalTime
import java.time.OffsetDateTime

enum class ChangeTargetType {
    MEAL_TIME,
    MEMBER,
    PRESCRIPTION,
}

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

@Entity
@Table(name = "change_log")
class ChangeLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "care_group_id", nullable = false)
    var careGroupId: Long = 0,
    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    var targetType: ChangeTargetType = ChangeTargetType.MEAL_TIME,
    @Column(name = "target_id")
    var targetId: Long? = null,
    @Column(nullable = false)
    var field: String = "",
    @Column(name = "old_value")
    var oldValue: String? = null,
    @Column(name = "new_value")
    var newValue: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
