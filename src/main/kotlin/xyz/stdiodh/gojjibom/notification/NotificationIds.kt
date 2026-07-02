package xyz.stdiodh.gojjibom.notification

fun NotificationEntity.requiredId(): Long = id ?: throw IllegalStateException("Notification id is not assigned")
