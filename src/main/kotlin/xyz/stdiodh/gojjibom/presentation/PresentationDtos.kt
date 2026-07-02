package xyz.stdiodh.gojjibom.presentation

/**
 * BFF DTOs mirroring the FE types.ts 1:1: lowercase enum values as String, ids as String,
 * Korean/Asia-Seoul labels. spec-005 adds NextDose.dispensingType, NextDose.photoThumbUrl,
 * and ConfirmLog.photoThumbUrl.
 */
data class Pill(
    val id: String,
    val name: String,
    val shape: String,
    val note: String,
)

data class Dose(
    val id: String,
    val label: String,
    val time: String,
    val mealTag: String,
    val pillCount: Int,
    val packetNo: Int,
    val status: String,
    val note: String,
)

data class NextDose(
    val doseId: String,
    val label: String,
    val alarmLabel: String,
    val pillCount: Int,
    val packetNo: Int,
    val mealTag: String,
    val includesNote: String,
    val baselineNote: String,
    val spokenText: String,
    val doneTimeLabel: String,
    val dispensingType: String,
    val photoThumbUrl: String?,
    val pills: List<Pill>,
)

data class PillTracking(
    val remaining: Int,
    val runOutDate: String,
    val refillDDay: String,
)

data class WeekDay(
    val label: String,
    val status: String,
)

data class ConfirmLog(
    val doseLabel: String,
    val status: String,
    val detail: String?,
    val photoThumbUrl: String?,
)

data class EscalationAlert(
    val doseLabel: String,
    val lastAlarm: String,
    val retries: Int,
    val minutesElapsed: Int,
    val steps: List<String>,
)

data class CareCircle(
    val family: Int,
    val social: Int,
)

data class SeniorDay(
    val dateLabel: String,
    val nextDose: NextDose?,
    val doses: List<Dose>,
)

data class CaregiverBoard(
    val patientName: String,
    val circle: CareCircle,
    val doses: List<Dose>,
    val confirmations: List<ConfirmLog>,
    val pills: PillTracking,
    val week: List<WeekDay>,
    val alert: EscalationAlert?,
)
