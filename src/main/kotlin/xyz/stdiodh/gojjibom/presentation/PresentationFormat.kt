package xyz.stdiodh.gojjibom.presentation

import xyz.stdiodh.gojjibom.dose.ConfirmMethod
import xyz.stdiodh.gojjibom.dose.DoseEventStatus
import xyz.stdiodh.gojjibom.prescription.DoseSlot
import xyz.stdiodh.gojjibom.prescription.MealRelation
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure formatting helpers for the BFF presentation tier.
 * Isolates all Korean / Asia-Seoul / lowercase-enum / string-id formatting so the
 * granular contract (UPPERCASE enums, Long ids, ISO times) stays untouched.
 */
object PresentationFormat {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    private val KOREAN_DAY_OF_WEEK =
        mapOf(
            java.time.DayOfWeek.MONDAY to "월요일",
            java.time.DayOfWeek.TUESDAY to "화요일",
            java.time.DayOfWeek.WEDNESDAY to "수요일",
            java.time.DayOfWeek.THURSDAY to "목요일",
            java.time.DayOfWeek.FRIDAY to "금요일",
            java.time.DayOfWeek.SATURDAY to "토요일",
            java.time.DayOfWeek.SUNDAY to "일요일",
        )

    private val CONFIRM_METHOD_KOREAN =
        mapOf(
            ConfirmMethod.VOICE to "음성 확인",
            ConfirmMethod.BUTTON to "버튼 확인",
            ConfirmMethod.CAREGIVER to "보호자 확인",
            ConfirmMethod.AUTO to "자동 확인",
        )

    /** '2026년 7월 2일 목요일' */
    fun dateLabel(date: LocalDate): String {
        val dayOfWeek = KOREAN_DAY_OF_WEEK.getValue(date.dayOfWeek)
        return "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 $dayOfWeek"
    }

    /** '오후 7:30' (12h with 오전/오후). */
    fun clockLabel(time: LocalTime): String {
        val meridiem = if (time.hour < NOON_HOUR) "오전" else "오후"
        val hour12 =
            when (val h = time.hour % NOON_HOUR) {
                0 -> NOON_HOUR
                else -> h
            }
        val minute = time.minute.toString().padStart(2, '0')
        return "$meridiem $hour12:$minute"
    }

    /** '식후 30분' / '식전 30분' / '취침 전' / '' */
    fun mealTag(
        mealRelation: MealRelation,
        offsetMin: Int?,
        slot: DoseSlot,
    ): String {
        val offsetSuffix = offsetMin?.takeIf { it > 0 }?.let { " ${it}분" }.orEmpty()
        return when (mealRelation) {
            MealRelation.BEFORE_MEAL -> "식전$offsetSuffix"
            MealRelation.AFTER_MEAL -> "식후$offsetSuffix"
            MealRelation.WITH_MEAL -> "식사와 함께"
            MealRelation.NONE -> if (slot == DoseSlot.BEDTIME) "취침 전" else ""
        }
    }

    /** TAKEN -> done, SCHEDULED -> upcoming, MISSED & SKIPPED -> missed. */
    fun statusToLower(status: DoseEventStatus): String =
        when (status) {
            DoseEventStatus.TAKEN -> "done"
            DoseEventStatus.SCHEDULED -> "upcoming"
            DoseEventStatus.MISSED, DoseEventStatus.SKIPPED -> "missed"
        }

    fun methodToLower(method: ConfirmMethod?): String = method?.name?.lowercase().orEmpty()

    fun methodKorean(method: ConfirmMethod?): String = method?.let { CONFIRM_METHOD_KOREAN[it] }.orEmpty()

    /** '저녁약 · 1번 봉지' */
    fun slotLabel(
        label: String,
        packetNo: Int?,
    ): String = if (packetNo != null) "$label · ${packetNo}번 봉지" else label

    /** '7월 14일' */
    fun runOutLabel(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일"

    /** 'D-13' */
    fun refillDDay(
        runOutDate: LocalDate,
        today: LocalDate,
    ): String {
        val days = ChronoUnit.DAYS.between(today, runOutDate)
        return when {
            days > 0 -> "D-$days"
            days == 0L -> "D-day"
            else -> "D+${-days}"
        }
    }

    private const val NOON_HOUR = 12
}
