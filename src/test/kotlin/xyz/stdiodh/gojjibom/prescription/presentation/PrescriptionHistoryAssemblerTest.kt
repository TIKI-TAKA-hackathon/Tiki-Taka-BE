package xyz.stdiodh.gojjibom.prescription.presentation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.stdiodh.gojjibom.prescription.DoseScheduleEntity
import xyz.stdiodh.gojjibom.prescription.DoseScheduleItemEntity
import xyz.stdiodh.gojjibom.prescription.MedicationEntity
import xyz.stdiodh.gojjibom.prescription.PharmacyEntity
import xyz.stdiodh.gojjibom.prescription.PrescriptionEntity
import java.time.LocalDate
import java.time.LocalTime

class PrescriptionHistoryAssemblerTest {
    private val assembler = PrescriptionHistoryAssembler()
    private val today = LocalDate.of(2026, 7, 15)

    @Test
    fun `display name uses the earliest schedule label`() {
        val prescription = prescription(id = 1)
        prescription.schedules +=
            listOf(
                schedule(prescription, "저녁약", LocalTime.of(19, 0)),
                schedule(prescription, "아침약", LocalTime.of(8, 0)),
            )

        assertThat(single(prescription).displayName).isEqualTo("아침약")
    }

    @Test
    fun `display name falls back to the first medication when the earliest label is blank`() {
        val prescription = prescription(id = 2)
        val earliest = schedule(prescription, "  ", LocalTime.of(7, 0))
        earliest.items +=
            listOf(
                DoseScheduleItemEntity(doseSchedule = earliest, medication = MedicationEntity(name = "혈압약")),
                DoseScheduleItemEntity(doseSchedule = earliest, medication = MedicationEntity(name = "당뇨약")),
            )
        prescription.schedules += listOf(schedule(prescription, "저녁약", LocalTime.of(19, 0)), earliest)

        assertThat(single(prescription).displayName).isEqualTo("혈압약")
    }

    @Test
    fun `display name is empty without schedules or without medications`() {
        val noSchedules = prescription(id = 3)
        val blankWithoutItems = prescription(id = 4)
        blankWithoutItems.schedules += schedule(blankWithoutItems, "", LocalTime.of(8, 0))

        assertThat(single(noSchedules).displayName).isEmpty()
        assertThat(single(blankWithoutItems).displayName).isEmpty()
    }

    @Test
    fun `status is ended only when the end date is before today`() {
        val endedYesterday = prescription(id = 5, endDate = today.minusDays(1))
        val endsToday = prescription(id = 6, endDate = today)
        val openEnded = prescription(id = 7, endDate = null)

        val view = assembler.toHistoryList(42, listOf(endedYesterday, endsToday, openEnded), today)

        assertThat(view.seniorId).isEqualTo("42")
        assertThat(view.items.map { it.prescriptionId to it.status })
            .containsExactly("5" to "ended", "6" to "active", "7" to "active")
    }

    @Test
    fun `period label shows the end date or an ongoing marker`() {
        val bounded = prescription(id = 8, startDate = LocalDate.of(2026, 7, 2), endDate = LocalDate.of(2026, 7, 16))
        val openEnded = prescription(id = 9, startDate = LocalDate.of(2026, 7, 2), endDate = null)

        val items = assembler.toHistoryList(42, listOf(bounded, openEnded), today).items

        assertThat(items[0].periodLabel).isEqualTo("2026.07.02 ~ 2026.07.16")
        assertThat(items[1].periodLabel).isEqualTo("2026.07.02 ~ 진행 중")
        assertThat(items[0].dispensingNumber).isEqualTo("8")
        assertThat(items[0].pharmacyName).isEqualTo("고찌봄 약국")
    }

    private fun single(prescription: PrescriptionEntity) =
        assembler
            .toHistoryList(42, listOf(prescription), today)
            .items
            .single()

    private fun prescription(
        id: Long,
        startDate: LocalDate = LocalDate.of(2026, 7, 1),
        endDate: LocalDate? = null,
    ) = PrescriptionEntity(
        id = id,
        pharmacy = PharmacyEntity(name = "고찌봄 약국"),
        startDate = startDate,
        endDate = endDate,
    )

    private fun schedule(
        prescription: PrescriptionEntity,
        label: String,
        scheduledTime: LocalTime,
    ) = DoseScheduleEntity(prescription = prescription, label = label, scheduledTime = scheduledTime)
}
