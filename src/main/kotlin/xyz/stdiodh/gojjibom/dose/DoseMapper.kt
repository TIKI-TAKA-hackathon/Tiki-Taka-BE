package xyz.stdiodh.gojjibom.dose

import org.springframework.stereotype.Component
import xyz.stdiodh.gojjibom.caregroup.requiredId
import xyz.stdiodh.gojjibom.prescription.DoseScheduleItemEntity
import xyz.stdiodh.gojjibom.prescription.DoseScheduleItemResponse
import xyz.stdiodh.gojjibom.prescription.requiredId
import java.time.LocalDate

@Component
class DoseMapper {
    fun toResponse(event: DoseEventEntity): DoseEventResponse {
        val schedule = event.doseSchedule
        return DoseEventResponse(
            id = event.requiredId(),
            doseScheduleId = schedule.requiredId(),
            seniorId = event.senior.requiredId(),
            scheduledDate = event.scheduledDate,
            scheduledAt = event.scheduledAt,
            status = event.status,
            confirmedAt = event.confirmedAt,
            confirmMethod = event.confirmMethod,
            confirmedByUserId = event.confirmedById,
            photoImageId = event.photoImageId,
            photoReviewStatus = event.photoReviewStatus,
            label = schedule.label,
            slot = schedule.slot,
            packetNo = schedule.packetNo,
            scheduledTime = schedule.scheduledTime,
            mealRelation = schedule.mealRelation,
            mealOffsetMin = schedule.mealOffsetMin,
            doseBasis = schedule.doseBasis.name.lowercase(),
            pillCount = schedule.pillCount,
            items = schedule.items.sortedBy { it.requiredId() }.map { toItemResponse(it) },
        )
    }

    fun toListResponse(
        seniorId: Long,
        date: LocalDate,
        events: List<DoseEventEntity>,
    ): DoseEventListResponse =
        DoseEventListResponse(
            seniorId = seniorId,
            date = date,
            events = events.map { toResponse(it) },
        )

    private fun toItemResponse(item: DoseScheduleItemEntity): DoseScheduleItemResponse =
        DoseScheduleItemResponse(
            id = item.requiredId(),
            medicationId = item.medication.requiredId(),
            medicationName = item.medication.name,
            category = item.medication.category,
            description = item.medication.description,
            count = item.count,
            shape = item.medication.shape,
        )
}
