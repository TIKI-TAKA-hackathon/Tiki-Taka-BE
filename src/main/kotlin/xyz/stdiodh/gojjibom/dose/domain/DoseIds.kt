package xyz.stdiodh.gojjibom.dose.domain

fun DoseEventEntity.requiredId(): Long = id ?: throw IllegalStateException("Dose event id is not assigned")
