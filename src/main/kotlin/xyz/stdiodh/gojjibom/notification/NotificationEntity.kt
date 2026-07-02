package xyz.stdiodh.gojjibom.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * In-app notification kinds. UPPERCASE values match the V12 CHECK constraint.
 * Wording stays at the "약 미확인" (medication unconfirmed) level; there is NO
 * fall/emergency detection here (spec 005 §G safety copy).
 */
enum class NotificationType {
    REMINDER,
    MISSED,
    ESCALATION,
}

@Entity
@Table(name = "notifications")
class NotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "care_group_id", nullable = false)
    var careGroupId: Long = 0,
    @Column(name = "senior_id", nullable = false)
    var seniorId: Long = 0,
    @Column(name = "dose_event_id")
    var doseEventId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: NotificationType = NotificationType.REMINDER,
    @Column(nullable = false)
    var level: Int = 0,
    @Column(nullable = false)
    var title: String = "",
    @Column(nullable = false)
    var body: String = "",
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "read_at")
    var readAt: OffsetDateTime? = null,
    // WP3b caregiver dispatch outcome (kakao 알림톡 / SMS; currently a STUB).
    // dispatchedAt != null means this row was already delivered — used to keep
    // the escalation evaluator idempotent (never re-dispatch a sent row).
    @Column(name = "dispatched_at")
    var dispatchedAt: OffsetDateTime? = null,
    @Column(name = "dispatch_target", length = 30)
    var dispatchTarget: String? = null,
    @Column(name = "dispatch_channel", length = 20)
    var dispatchChannel: String? = null,
)
