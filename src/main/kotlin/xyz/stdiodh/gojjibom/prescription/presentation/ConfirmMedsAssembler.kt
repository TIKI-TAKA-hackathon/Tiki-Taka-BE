package xyz.stdiodh.gojjibom.prescription.presentation

import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.prescription.ConfirmMedsScheduleView
import xyz.stdiodh.gojjibom.prescription.ConfirmMedsView
import xyz.stdiodh.gojjibom.prescription.DoseScheduleEntity
import xyz.stdiodh.gojjibom.prescription.PrescriptionEntity
import xyz.stdiodh.gojjibom.prescription.requiredId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Builds the PRV-01 sanitized confirm-meds view.
 * Isolates Korean/lowercase/string formatting; exposes pharmacy NAME only and drops
 * medication category and every senior field beyond the display name.
 */
@Component
class ConfirmMedsAssembler {
    fun toConfirmMedsView(
        prescription: PrescriptionEntity,
        schedules: List<DoseScheduleEntity>,
    ): ConfirmMedsView {
        val pharmacyName = prescription.pharmacy.name
        val dispensingNumber = prescription.requiredId().toString()
        val prescribedDateLabel = koreanDateLabel(prescription.prescribedDate)
        val dispensedDays = dispensedDays(prescription.startDate, prescription.endDate)
        val timesPerDay = schedules.size

        return ConfirmMedsView(
            seniorDisplayName = prescription.senior.name,
            dispensingType = prescription.dispensingType.name.lowercase(),
            pharmacyName = pharmacyName,
            registrationCode = prescription.registrationCode.orEmpty(),
            schedules =
                schedules.map { schedule ->
                    ConfirmMedsScheduleView(
                        displayName = schedule.label,
                        timesPerDay = timesPerDay,
                        doseBasis = schedule.doseBasis.name.lowercase(),
                        offsetMin = schedule.mealOffsetMin,
                        pillCount = schedule.pillCount,
                        dispensedDays = dispensedDays,
                        dispensingNumber = dispensingNumber,
                        prescribedDateLabel = prescribedDateLabel,
                        pharmacyName = pharmacyName,
                    )
                },
        )
    }

    private fun dispensedDays(
        startDate: LocalDate,
        endDate: LocalDate?,
    ): Int? = endDate?.let { (ChronoUnit.DAYS.between(startDate, it) + 1).toInt() }

    private fun koreanDateLabel(date: LocalDate): String = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"
}
