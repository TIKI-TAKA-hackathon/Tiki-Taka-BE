package xyz.stdiodh.gojjibom.prescription.presentation

import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.prescription.PrescriptionEntity
import xyz.stdiodh.gojjibom.prescription.PrescriptionHistoryListView
import xyz.stdiodh.gojjibom.prescription.PrescriptionHistoryView
import xyz.stdiodh.gojjibom.prescription.requiredId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Builds the prescription history list view.
 * status is DERIVED lowercase: 'ended' when end_date < today(Asia/Seoul) else 'active'.
 */
@Component
class PrescriptionHistoryAssembler {
    fun toHistoryList(
        seniorId: Long,
        prescriptions: List<PrescriptionEntity>,
        today: LocalDate,
    ): PrescriptionHistoryListView =
        PrescriptionHistoryListView(
            seniorId = seniorId.toString(),
            items = prescriptions.map { toHistoryView(it, today) },
        )

    private fun toHistoryView(
        prescription: PrescriptionEntity,
        today: LocalDate,
    ): PrescriptionHistoryView =
        PrescriptionHistoryView(
            prescriptionId = prescription.requiredId().toString(),
            displayName = displayName(prescription),
            periodLabel = periodLabel(prescription.startDate, prescription.endDate),
            dispensingNumber = prescription.requiredId().toString(),
            pharmacyName = prescription.pharmacy.name,
            status = status(prescription.endDate, today),
        )

    private fun displayName(prescription: PrescriptionEntity): String {
        val schedule = prescription.schedules.minByOrNull { it.scheduledTime }
        val scheduleLabel = schedule?.label?.takeIf { it.isNotBlank() }
        if (scheduleLabel != null) {
            return scheduleLabel
        }
        return schedule
            ?.items
            ?.firstOrNull()
            ?.medication
            ?.name
            .orEmpty()
    }

    private fun periodLabel(
        startDate: LocalDate,
        endDate: LocalDate?,
    ): String {
        val start = startDate.format(DATE_FORMAT)
        return if (endDate != null) "$start ~ ${endDate.format(DATE_FORMAT)}" else "$start ~ 진행 중"
    }

    private fun status(
        endDate: LocalDate?,
        today: LocalDate,
    ): String = if (endDate != null && endDate.isBefore(today)) "ended" else "active"

    private companion object {
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    }
}
