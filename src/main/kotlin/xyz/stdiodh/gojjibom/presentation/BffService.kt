package xyz.stdiodh.gojjibom.presentation

import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import xyz.stdiodh.gojjibom.caregroup.CareGroupRepository
import xyz.stdiodh.gojjibom.caregroup.requiredId
import xyz.stdiodh.gojjibom.dose.DoseEventGenerator
import xyz.stdiodh.gojjibom.dose.DoseEventRepository
import xyz.stdiodh.gojjibom.shared.ApiException
import java.time.LocalDate

/**
 * Backend-for-frontend aggregate reads. Unauthenticated for the demo contract;
 * lazily generates the day's dose events (idempotent) before assembling the FE payload.
 */
@Service
class BffService(
    private val careGroups: CareGroupRepository,
    private val doseEvents: DoseEventRepository,
    private val generator: DoseEventGenerator,
    private val seniorDayAssembler: SeniorDayAssembler,
    private val caregiverBoardAssembler: CaregiverBoardAssembler,
    private val photoGalleryAssembler: PhotoGalleryAssembler,
) {
    @Transactional
    fun seniorToday(
        seniorId: Long,
        date: LocalDate,
    ): SeniorDay {
        generator.ensureEventsFor(seniorId, date)
        val events = doseEvents.findBySeniorIdAndScheduledDate(seniorId, date)
        return seniorDayAssembler.assemble(seniorId, date, events)
    }

    @Transactional
    fun caregiverBoard(
        careGroupId: Long,
        date: LocalDate,
    ): CaregiverBoard {
        val careGroup =
            careGroups.findByIdOrNull(careGroupId)
                ?: throw ApiException(HttpStatus.NOT_FOUND, "CARE_GROUP_NOT_FOUND", "Care group not found")
        val seniorId = careGroup.senior.requiredId()
        generator.ensureEventsFor(seniorId, date)
        val events = doseEvents.findBySeniorIdAndScheduledDate(seniorId, date)
        return caregiverBoardAssembler.assemble(careGroup, date, events)
    }

    @Transactional(readOnly = true)
    fun photoGallery(
        careGroupId: Long,
        from: LocalDate,
        to: LocalDate,
    ): PhotoGallery {
        val careGroup =
            careGroups.findByIdOrNull(careGroupId)
                ?: throw ApiException(HttpStatus.NOT_FOUND, "CARE_GROUP_NOT_FOUND", "Care group not found")
        val seniorId = careGroup.senior.requiredId()
        val events = doseEvents.findPhotosBySeniorIdAndScheduledDateBetween(seniorId, from, to)
        return photoGalleryAssembler.assemble(careGroupId, events)
    }
}
