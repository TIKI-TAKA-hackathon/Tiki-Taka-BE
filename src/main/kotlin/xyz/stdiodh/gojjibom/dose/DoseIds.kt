package xyz.stdiodh.gojjibom.dose

fun DoseEventEntity.requiredId(): Long = id ?: throw IllegalStateException("Dose event id is not assigned")
