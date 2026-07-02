package xyz.stdiodh.gojjibom.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import xyz.stdiodh.gojjibom.dose.ConfirmMethod
import xyz.stdiodh.gojjibom.dose.DoseEventStatus
import xyz.stdiodh.gojjibom.prescription.DoseSlot
import xyz.stdiodh.gojjibom.prescription.MealRelation
import java.time.LocalDate
import java.time.LocalTime

class PresentationFormatTest {
    @Test
    fun `dateLabel renders Korean day of week`() {
        assertEquals("2026년 7월 2일 목요일", PresentationFormat.dateLabel(LocalDate.of(2026, 7, 2)))
    }

    @Test
    fun `clockLabel uses 12 hour with meridiem`() {
        assertEquals("오전 8:30", PresentationFormat.clockLabel(LocalTime.of(8, 30)))
        assertEquals("오후 7:30", PresentationFormat.clockLabel(LocalTime.of(19, 30)))
        assertEquals("오후 12:05", PresentationFormat.clockLabel(LocalTime.of(12, 5)))
        assertEquals("오전 12:00", PresentationFormat.clockLabel(LocalTime.of(0, 0)))
    }

    @Test
    fun `mealTag renders offset and bedtime`() {
        assertEquals("식후 30분", PresentationFormat.mealTag(MealRelation.AFTER_MEAL, 30, DoseSlot.MORNING))
        assertEquals("식전 15분", PresentationFormat.mealTag(MealRelation.BEFORE_MEAL, 15, DoseSlot.MORNING))
        assertEquals("취침 전", PresentationFormat.mealTag(MealRelation.NONE, null, DoseSlot.BEDTIME))
        assertEquals("", PresentationFormat.mealTag(MealRelation.NONE, null, DoseSlot.CUSTOM))
    }

    @Test
    fun `statusToLower folds missed and skipped`() {
        assertEquals("done", PresentationFormat.statusToLower(DoseEventStatus.TAKEN))
        assertEquals("upcoming", PresentationFormat.statusToLower(DoseEventStatus.SCHEDULED))
        assertEquals("missed", PresentationFormat.statusToLower(DoseEventStatus.MISSED))
        assertEquals("missed", PresentationFormat.statusToLower(DoseEventStatus.SKIPPED))
    }

    @Test
    fun `methodToLower lowercases the enum name`() {
        assertEquals("voice", PresentationFormat.methodToLower(ConfirmMethod.VOICE))
        assertEquals("button", PresentationFormat.methodToLower(ConfirmMethod.BUTTON))
        assertEquals("", PresentationFormat.methodToLower(null))
    }

    @Test
    fun `slotLabel appends packet number`() {
        assertEquals("저녁약 · 1번 봉지", PresentationFormat.slotLabel("저녁약", 1))
        assertEquals("저녁약", PresentationFormat.slotLabel("저녁약", null))
    }

    @Test
    fun `runOutLabel and refillDDay render Korean labels`() {
        assertEquals("7월 14일", PresentationFormat.runOutLabel(LocalDate.of(2026, 7, 14)))
        assertEquals("D-13", PresentationFormat.refillDDay(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 1)))
        assertEquals("D-day", PresentationFormat.refillDDay(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)))
    }
}
