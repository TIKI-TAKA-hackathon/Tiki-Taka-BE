package xyz.stdiodh.gojjibom.prescription

/**
 * FE-facing, PII-sanitized prescription views (BFF representation tier).
 * Enums are lowercase strings, ids are strings, dates are Korean Asia/Seoul labels.
 * PRV-01: excludes 병명(medication category), pharmacy phone/address, and any senior
 * field beyond the display name.
 */
data class ConfirmMedsView(
    val seniorDisplayName: String,
    val dispensingType: String,
    val pharmacyName: String,
    val registrationCode: String,
    val schedules: List<ConfirmMedsScheduleView>,
)

data class ConfirmMedsScheduleView(
    val displayName: String,
    val timesPerDay: Int,
    val doseBasis: String,
    val offsetMin: Int?,
    val pillCount: Int?,
    val dispensedDays: Int?,
    val dispensingNumber: String,
    val prescribedDateLabel: String,
    val pharmacyName: String,
)

data class PrescriptionHistoryListView(
    val seniorId: String,
    val items: List<PrescriptionHistoryView>,
)

data class PrescriptionHistoryView(
    val prescriptionId: String,
    val displayName: String,
    val periodLabel: String,
    val dispensingNumber: String,
    val pharmacyName: String,
    val status: String,
)
