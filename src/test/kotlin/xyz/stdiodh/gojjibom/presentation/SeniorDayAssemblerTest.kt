package xyz.stdiodh.gojjibom.presentation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import xyz.stdiodh.gojjibom.caregroup.MealTimeEntity
import xyz.stdiodh.gojjibom.caregroup.MealTimeRepository
import xyz.stdiodh.gojjibom.dose.DoseEventEntity
import xyz.stdiodh.gojjibom.dose.DoseEventStatus
import xyz.stdiodh.gojjibom.media.ImageService
import xyz.stdiodh.gojjibom.prescription.DispensingType
import xyz.stdiodh.gojjibom.prescription.DoseScheduleEntity
import xyz.stdiodh.gojjibom.prescription.DoseScheduleItemEntity
import xyz.stdiodh.gojjibom.prescription.DoseSlot
import xyz.stdiodh.gojjibom.prescription.MedicationEntity
import xyz.stdiodh.gojjibom.prescription.PillShape
import xyz.stdiodh.gojjibom.prescription.PrescriptionEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

class SeniorDayAssemblerTest {
    private val mealTimes = mock(MealTimeRepository::class.java)
    private val imageService = mock(ImageService::class.java)
    private val assembler = SeniorDayAssembler(mealTimes, imageService)

    @Test
    fun `next dose is the earliest scheduled event with ties broken by id`() {
        val taken = event(id = 1, at = "2026-07-02T07:00:00+09:00", status = DoseEventStatus.TAKEN)
        val laterId = event(id = 5, at = "2026-07-02T08:30:00+09:00")
        val earlierId = event(id = 3, at = "2026-07-02T08:30:00+09:00")
        val missed = event(id = 2, at = "2026-07-02T08:00:00+09:00", status = DoseEventStatus.MISSED)

        val day = assembler.assemble(SENIOR_ID, DATE, listOf(laterId, taken, earlierId, missed))

        assertThat(day.doses.map { it.id }).containsExactly("1", "2", "3", "5")
        assertThat(day.nextDose?.doseId).isEqualTo("3")
    }

    @Test
    fun `no next dose when every event is already resolved`() {
        val taken = event(id = 1, at = "2026-07-02T07:00:00+09:00", status = DoseEventStatus.TAKEN)
        val skipped = event(id = 2, at = "2026-07-02T12:00:00+09:00", status = DoseEventStatus.SKIPPED)

        val day = assembler.assemble(SENIOR_ID, DATE, listOf(taken, skipped))

        assertThat(day.nextDose).isNull()
        assertThat(day.doses.map { it.status }).containsExactly("done", "missed")
    }

    @Test
    fun `baseline note follows the meal time of the dose slot`() {
        `when`(mealTimes.findBySeniorId(SENIOR_ID)).thenReturn(
            MealTimeEntity(
                breakfastTime = LocalTime.of(8, 0),
                lunchTime = LocalTime.of(12, 30),
                dinnerTime = LocalTime.of(18, 40),
            ),
        )

        val notes =
            DoseSlot.entries.associateWith { slot ->
                val next = event(id = 1, at = "2026-07-02T08:30:00+09:00", slot = slot)
                assembler.assemble(SENIOR_ID, DATE, listOf(next)).nextDose?.baselineNote
            }

        assertThat(notes)
            .containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    DoseSlot.MORNING to "아침 식사 오전 8:00 기준",
                    DoseSlot.LUNCH to "점심 식사 오후 12:30 기준",
                    DoseSlot.DINNER to "저녁 식사 오후 6:40 기준",
                    DoseSlot.BEDTIME to "",
                    DoseSlot.CUSTOM to "",
                ),
            )
    }

    @Test
    fun `next dose lists pills by item id and names the first non blank category`() {
        val next = event(id = 7, at = "2026-07-02T08:30:00+09:00", dispensingType = DispensingType.ORGANIZER)
        val schedule = next.doseSchedule
        schedule.items +=
            listOf(
                item(
                    id = 9,
                    schedule = schedule,
                    medication = MedicationEntity(id = 90, name = "비타민", category = " ", shape = PillShape.OVAL),
                ),
                item(
                    id = 4,
                    schedule = schedule,
                    medication =
                        MedicationEntity(
                            id = 40,
                            name = "혈압약",
                            category = "혈압",
                            description = "식후 복용",
                            shape = PillShape.CAPSULE,
                        ),
                ),
            )

        val nextDose = assembler.assemble(SENIOR_ID, DATE, listOf(next)).nextDose

        assertThat(nextDose?.pills?.map { it.id }).containsExactly("40", "90")
        assertThat(nextDose?.pills?.map { it.shape }).containsExactly("capsule", "oval")
        assertThat(nextDose?.pills?.map { it.note }).containsExactly("식후 복용", "")
        assertThat(nextDose?.includesNote).isEqualTo("혈압약이 포함돼 있어요")
        assertThat(nextDose?.dispensingType).isEqualTo("organizer")
        assertThat(nextDose?.doneTimeLabel).isEmpty()
        assertThat(nextDose?.photoThumbUrl).isNull()
        verifyNoInteractions(imageService)
    }

    @Test
    fun `missing pill count and packet number render as zero without a packet suffix`() {
        val next =
            event(
                id = 1,
                at = "2026-07-01T23:30:00Z",
                pillCount = null,
                packetNo = null,
            )
        val taken =
            event(
                id = 2,
                at = "2026-07-01T22:00:00Z",
                status = DoseEventStatus.TAKEN,
                pillCount = null,
                packetNo = null,
            )

        val day = assembler.assemble(SENIOR_ID, DATE, listOf(next, taken))

        val dose = day.doses.single { it.id == "1" }
        assertThat(dose.time).isEqualTo("08:30")
        assertThat(dose.pillCount).isZero()
        assertThat(dose.packetNo).isZero()
        assertThat(day.doses.single { it.id == "2" }.note).isEqualTo("0개 모두 드셨어요")
        assertThat(day.nextDose?.label).isEqualTo("아침약")
        assertThat(day.nextDose?.pillCount).isZero()
        assertThat(day.nextDose?.packetNo).isZero()
        assertThat(day.nextDose?.spokenText).isEqualTo("아침약 0개를 드실 시간이에요.")
    }

    private fun event(
        id: Long,
        at: String,
        status: DoseEventStatus = DoseEventStatus.SCHEDULED,
        slot: DoseSlot = DoseSlot.MORNING,
        pillCount: Int? = 2,
        packetNo: Int? = 1,
        dispensingType: DispensingType = DispensingType.POUCH,
    ): DoseEventEntity =
        DoseEventEntity(
            id = id,
            doseSchedule =
                DoseScheduleEntity(
                    prescription = PrescriptionEntity(dispensingType = dispensingType),
                    slot = slot,
                    label = "아침약",
                    packetNo = packetNo,
                    scheduledTime = LocalTime.of(8, 30),
                    pillCount = pillCount,
                ),
            scheduledAt = OffsetDateTime.parse(at),
            status = status,
        )

    private fun item(
        id: Long,
        schedule: DoseScheduleEntity,
        medication: MedicationEntity,
    ) = DoseScheduleItemEntity(id = id, doseSchedule = schedule, medication = medication)

    private companion object {
        const val SENIOR_ID = 10L
        val DATE: LocalDate = LocalDate.of(2026, 7, 2)
    }
}
