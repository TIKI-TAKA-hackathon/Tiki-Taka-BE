package xyz.stdiodh.gojjibom.prescription

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate
import java.time.LocalTime

data class CreatePrescriptionRequest(
    val pharmacistUserId: Long,
    @field:Valid
    val pharmacy: PharmacyRequest,
    val prescribedDate: LocalDate,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    @field:Valid
    @field:NotEmpty
    val schedules: List<CreateDoseScheduleRequest>,
)

data class PharmacyRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val phone: String,
    val address: String?,
)

data class CreateDoseScheduleRequest(
    val slot: DoseSlot,
    @field:NotBlank
    val label: String,
    @field:Min(1)
    val packetNo: Int?,
    val scheduledTime: LocalTime,
    val mealRelation: MealRelation,
    @field:Min(0)
    val mealOffsetMin: Int?,
    @field:Min(1)
    val pillCount: Int?,
    @field:Valid
    @field:NotEmpty
    val items: List<CreateDoseScheduleItemRequest>,
)

data class CreateDoseScheduleItemRequest(
    @field:NotBlank
    val medicationName: String,
    val category: String?,
    val description: String?,
    @field:Min(1)
    val count: Int,
)

data class PharmacyResponse(
    val id: Long,
    val name: String,
    val phone: String,
    val address: String?,
)

data class PrescriptionResponse(
    val id: Long,
    val seniorId: Long,
    val pharmacy: PharmacyResponse,
    val registeredByUserId: Long?,
    val prescribedDate: LocalDate,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: PrescriptionStatus,
    val schedules: List<DoseScheduleResponse>,
)

data class DoseScheduleListResponse(
    val seniorId: Long,
    val schedules: List<DoseScheduleResponse>,
)

data class DoseScheduleResponse(
    val id: Long,
    val prescriptionId: Long,
    val slot: DoseSlot,
    val label: String,
    val packetNo: Int?,
    val scheduledTime: LocalTime,
    val mealRelation: MealRelation,
    val mealOffsetMin: Int?,
    val pillCount: Int?,
    val active: Boolean,
    val prescriptionStartDate: LocalDate,
    val prescriptionEndDate: LocalDate?,
    val items: List<DoseScheduleItemResponse>,
)

data class DoseScheduleItemResponse(
    val id: Long,
    val medicationId: Long,
    val medicationName: String,
    val category: String?,
    val description: String?,
    val count: Int,
)
