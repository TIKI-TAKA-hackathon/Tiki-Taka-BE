package xyz.stdiodh.gojjibom.prescription

fun PharmacyEntity.requiredId(): Long = id ?: throw IllegalStateException("Pharmacy id is not assigned")

fun PrescriptionEntity.requiredId(): Long = id ?: throw IllegalStateException("Prescription id is not assigned")

fun MedicationEntity.requiredId(): Long = id ?: throw IllegalStateException("Medication id is not assigned")

fun DoseScheduleEntity.requiredId(): Long = id ?: throw IllegalStateException("Dose schedule id is not assigned")

fun DoseScheduleItemEntity.requiredId(): Long = id ?: throw IllegalStateException("Dose schedule item id missing")
