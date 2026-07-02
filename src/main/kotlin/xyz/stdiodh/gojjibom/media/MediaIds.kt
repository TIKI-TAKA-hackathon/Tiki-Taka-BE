package xyz.stdiodh.gojjibom.media

fun ImageEntity.requiredId(): Long = id ?: throw IllegalStateException("Image id is not assigned")
