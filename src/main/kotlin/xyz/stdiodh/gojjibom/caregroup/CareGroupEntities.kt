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
import java.time.LocalDate
import java.time.OffsetDateTime

enum class UserType {
    SENIOR,
    CAREGIVER,
    PHARMACIST,
}

enum class CareGroupRole {
    OWNER,
    FAMILY,
    SOCIAL_WORKER,
}

enum class MemberStatus {
    ACTIVE,
    PENDING,
    REMOVED,
}

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    var userType: UserType = UserType.CAREGIVER,
    @Column(nullable = false)
    var name: String = "",
    @Column(nullable = false, unique = true)
    var phone: String = "",
    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,
    @Column(name = "pharmacy_id")
    var pharmacyId: Long? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "care_groups")
class CareGroupEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_id", nullable = false)
    var senior: UserEntity = UserEntity(),
    @Column(nullable = false)
    var name: String = "",
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "care_group_members")
class CareGroupMemberEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_group_id", nullable = false)
    var careGroup: CareGroupEntity = CareGroupEntity(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity = UserEntity(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: CareGroupRole = CareGroupRole.FAMILY,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MemberStatus = MemberStatus.PENDING,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    var invitedBy: UserEntity? = null,
    @Column(name = "joined_at")
    var joinedAt: OffsetDateTime? = null,
)

@Entity
@Table(name = "invite_links")
class InviteLinkEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_group_id", nullable = false)
    var careGroup: CareGroupEntity = CareGroupEntity(),
    @Column(nullable = false, unique = true)
    var token: String = "",
    @Column(name = "expires_at", nullable = false)
    var expiresAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "max_uses")
    var maxUses: Int? = null,
    @Column(name = "use_count", nullable = false)
    var useCount: Int = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    var createdBy: UserEntity = UserEntity(),
    @Column(name = "revoked_at")
    var revokedAt: OffsetDateTime? = null,
)
