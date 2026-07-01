package xyz.stdiodh.gojjibom.prescription

import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.caregroup.requiredId

@Component
class PrescriptionMapper {
    fun toResponse(
        prescription: PrescriptionEntity,
        schedules: List<DoseScheduleEntity>,
    ): PrescriptionResponse =
        PrescriptionResponse(
            id = prescription.requiredId(),
            seniorId = prescription.senior.requiredId(),
            pharmacy = toResponse(prescription.pharmacy),
            registeredByUserId = prescription.registeredBy?.requiredId(),
            prescribedDate = prescription.prescribedDate,
            startDate = prescription.startDate,
            endDate = prescription.endDate,
            status = prescription.status,
            schedules = schedules.map { toResponse(it) },
        )

    fun toListResponse(
        seniorId: Long,
        schedules: List<DoseScheduleEntity>,
    ): DoseScheduleListResponse =
        DoseScheduleListResponse(
            seniorId = seniorId,
            schedules = schedules.map { toResponse(it) },
        )

    private fun toResponse(pharmacy: PharmacyEntity): PharmacyResponse =
        PharmacyResponse(
            id = pharmacy.requiredId(),
            name = pharmacy.name,
            phone = pharmacy.phone,
            address = pharmacy.address,
        )

    private fun toResponse(schedule: DoseScheduleEntity): DoseScheduleResponse =
        DoseScheduleResponse(
            id = schedule.requiredId(),
            prescriptionId = schedule.prescription.requiredId(),
            slot = schedule.slot,
            label = schedule.label,
            packetNo = schedule.packetNo,
            scheduledTime = schedule.scheduledTime,
            mealRelation = schedule.mealRelation,
            mealOffsetMin = schedule.mealOffsetMin,
            pillCount = schedule.pillCount,
            active = schedule.active,
            prescriptionStartDate = schedule.prescription.startDate,
            prescriptionEndDate = schedule.prescription.endDate,
            items = schedule.items.sortedBy { it.requiredId() }.map { toResponse(it) },
        )

    private fun toResponse(item: DoseScheduleItemEntity): DoseScheduleItemResponse =
        DoseScheduleItemResponse(
            id = item.requiredId(),
            medicationId = item.medication.requiredId(),
            medicationName = item.medication.name,
            category = item.medication.category,
            description = item.medication.description,
            count = item.count,
        )
}
