package xyz.stdiodh.gojjibom.caregroup

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "notification_settings")
class NotificationSettingsEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_id", nullable = false)
    var senior: UserEntity = UserEntity(),
    @Column(nullable = false)
    var enabled: Boolean = true,
    @Column(name = "remind_interval_min", nullable = false)
    var remindIntervalMin: Int = DEFAULT_REMIND_INTERVAL_MIN,
    @Column(name = "max_retries", nullable = false)
    var maxRetries: Int = DEFAULT_MAX_RETRIES,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    var updatedBy: UserEntity? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        const val DEFAULT_REMIND_INTERVAL_MIN = 5
        const val DEFAULT_MAX_RETRIES = 3
    }
}
