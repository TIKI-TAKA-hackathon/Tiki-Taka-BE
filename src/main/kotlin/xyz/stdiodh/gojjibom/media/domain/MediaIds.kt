package xyz.stdiodh.gojjibom.media.domain

fun ImageEntity.requiredId(): Long = id ?: throw IllegalStateException("Image id is not assigned")
