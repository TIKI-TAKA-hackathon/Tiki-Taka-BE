package xyz.stdiodh.gojjibom.caregroup

fun UserEntity.requiredId(): Long = id ?: throw IllegalStateException("User id is not assigned")

fun CareGroupEntity.requiredId(): Long = id ?: throw IllegalStateException("Care group id is not assigned")

fun CareGroupMemberEntity.requiredId(): Long = id ?: throw IllegalStateException("Care group member id is not assigned")

fun InviteLinkEntity.requiredId(): Long = id ?: throw IllegalStateException("Invite link id is not assigned")

fun MealTimeEntity.requiredId(): Long = id ?: throw IllegalStateException("Meal time id is not assigned")

fun ChangeLogEntity.requiredId(): Long = id ?: throw IllegalStateException("Change log id is not assigned")
