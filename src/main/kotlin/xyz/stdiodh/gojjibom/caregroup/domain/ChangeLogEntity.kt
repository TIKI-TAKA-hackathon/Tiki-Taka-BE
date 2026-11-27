package xyz.stdiodh.gojjibom.caregroup.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

enum class ChangeTargetType {
    MEAL_TIME,
    MEMBER,
    PRESCRIPTION,
}

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
