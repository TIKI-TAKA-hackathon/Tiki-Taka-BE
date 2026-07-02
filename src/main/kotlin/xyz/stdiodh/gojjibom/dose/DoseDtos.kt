package xyz.stdiodh.gojjibom.dose

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import xyz.stdiodh.gojjibom.prescription.DoseScheduleItemResponse
import xyz.stdiodh.gojjibom.prescription.DoseSlot
import xyz.stdiodh.gojjibom.prescription.MealRelation
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

data class DoseEventResponse(
    val id: Long,
    val doseScheduleId: Long,
    val seniorId: Long,
    val scheduledDate: LocalDate,
    val scheduledAt: OffsetDateTime,
    val status: DoseEventStatus,
    val confirmedAt: OffsetDateTime?,
    val confirmMethod: ConfirmMethod?,
    val confirmedByUserId: Long?,
    val photoImageId: Long?,
    val photoReviewStatus: PhotoReviewStatus?,
    val label: String,
    val slot: DoseSlot,
    val packetNo: Int?,
    val scheduledTime: LocalTime,
    val mealRelation: MealRelation,
    val mealOffsetMin: Int?,
    val doseBasis: String?,
    val pillCount: Int?,
    val items: List<DoseScheduleItemResponse>,
)

data class DoseEventListResponse(
    val seniorId: Long,
    val date: LocalDate,
    val events: List<DoseEventResponse>,
)

data class ConfirmDoseRequest(
    @field:Min(1)
    val actorUserId: Long,
    @field:NotNull
    val method: ConfirmMethod,
    @field:Min(1)
    val imageId: Long?,
)

data class ReviewPhotoRequest(
    @field:Min(1)
    val actorUserId: Long,
    @field:NotNull
    val reviewStatus: PhotoReviewStatus,
)
