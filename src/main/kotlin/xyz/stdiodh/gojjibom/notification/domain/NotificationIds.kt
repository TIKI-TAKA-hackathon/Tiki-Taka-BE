package xyz.stdiodh.gojjibom.notification.domain

fun NotificationEntity.requiredId(): Long = id ?: throw IllegalStateException("Notification id is not assigned")
