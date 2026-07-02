package xyz.stdiodh.gojjibom.prescription

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import xyz.stdiodh.gojjibom.caregroup.CareGroupEntity
import xyz.stdiodh.gojjibom.caregroup.CareGroupMemberRepository
import xyz.stdiodh.gojjibom.caregroup.CareGroupRepository
import xyz.stdiodh.gojjibom.caregroup.MemberStatus
import xyz.stdiodh.gojjibom.caregroup.UserEntity
import xyz.stdiodh.gojjibom.caregroup.UserRepository
import xyz.stdiodh.gojjibom.caregroup.UserType
import xyz.stdiodh.gojjibom.caregroup.requiredId
import xyz.stdiodh.gojjibom.prescription.presentation.ConfirmMedsAssembler
import xyz.stdiodh.gojjibom.prescription.presentation.PrescriptionHistoryAssembler
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class PrescriptionService(
    private val users: UserRepository,
    private val careGroups: CareGroupRepository,
    private val members: CareGroupMemberRepository,
    private val pharmacies: PharmacyRepository,
    private val prescriptions: PrescriptionRepository,
    private val medications: MedicationRepository,
    private val doseSchedules: DoseScheduleRepository,
    private val doseScheduleItems: DoseScheduleItemRepository,
    private val mapper: PrescriptionMapper,
    private val confirmMedsAssembler: ConfirmMedsAssembler,
    private val prescriptionHistoryAssembler: PrescriptionHistoryAssembler,
    private val clock: Clock,
) {
    @Transactional
    fun createPrescription(
        seniorId: Long,
        request: CreatePrescriptionRequest,
    ): PrescriptionResponse {
        val senior = seniorOrThrow(seniorId, "Prescription target must be a senior")
        careGroupOrThrow(seniorId)
        val pharmacist = pharmacistOrThrow(request.pharmacistUserId)
        validateDateRange(request)

        val pharmacy = findOrCreatePharmacy(request.pharmacy)
        val prescription =
            prescriptions.save(
                PrescriptionEntity(
                    senior = senior,
                    pharmacy = pharmacy,
                    registeredBy = pharmacist,
                    prescribedDate = request.prescribedDate,
                    startDate = request.startDate,
                    endDate = request.endDate,
                    status = PrescriptionStatus.ACTIVE,
                    dispensingType = request.dispensingType ?: DispensingType.POUCH,
                    registrationCode = request.registrationCode?.trim()?.ifBlank { null },
                    createdAt = OffsetDateTime.now(),
                ),
            )

        val savedSchedules = request.schedules.map { saveSchedule(prescription, it) }
        prescription.schedules.addAll(savedSchedules)

        return mapper.toResponse(prescription, savedSchedules)
    }

    @Transactional(readOnly = true)
    fun getDoseSchedules(
        seniorId: Long,
        actorUserId: Long,
    ): DoseScheduleListResponse {
        seniorOrThrow(seniorId, "Dose schedules target must be a senior")
        val careGroup = careGroupOrThrow(seniorId)
        requireActiveMember(careGroup.requiredId(), actorUserId)

        val schedules = doseSchedules.findActiveBySeniorId(seniorId)
        return mapper.toListResponse(seniorId, schedules)
    }

    @Transactional(readOnly = true)
    fun lookupByRegistrationCode(code: String): ConfirmMedsView {
        val prescription =
            prescriptions.findForConfirmByCode(code)
                ?: throw PrescriptionErrors.notFound("PRESCRIPTION_NOT_FOUND", "Prescription not found for code")
        val schedules =
            prescription.schedules
                .filter { it.active }
                .sortedWith(compareBy({ it.scheduledTime }, { it.requiredId() }))
        return confirmMedsAssembler.toConfirmMedsView(prescription, schedules)
    }

    @Transactional(readOnly = true)
    fun getPrescriptionHistory(
        seniorId: Long,
        actorUserId: Long,
    ): PrescriptionHistoryListView {
        seniorOrThrow(seniorId, "Prescription history target must be a senior")
        val careGroup = careGroupOrThrow(seniorId)
        requireActiveMember(careGroup.requiredId(), actorUserId)

        val history = prescriptions.findAllBySeniorIdOrderByStartDateDesc(seniorId)
        val today = LocalDate.now(clock)
        return prescriptionHistoryAssembler.toHistoryList(seniorId, history, today)
    }

    private fun seniorOrThrow(
        seniorId: Long,
        typeErrorMessage: String,
    ): UserEntity {
        val senior =
            users.findByIdOrNull(seniorId)
                ?: throw PrescriptionErrors.notFound("SENIOR_NOT_FOUND", "Senior not found")
        if (senior.userType != UserType.SENIOR) {
            throw PrescriptionErrors.badRequest("SENIOR_REQUIRED", typeErrorMessage)
        }
        return senior
    }

    private fun careGroupOrThrow(seniorId: Long): CareGroupEntity =
        careGroups.findBySeniorId(seniorId)
            ?: throw PrescriptionErrors.badRequest("CARE_GROUP_REQUIRED", "Senior must have a care group")

    private fun pharmacistOrThrow(pharmacistUserId: Long): UserEntity {
        val pharmacist =
            users.findByIdOrNull(pharmacistUserId)
                ?: throw PrescriptionErrors.notFound("PHARMACIST_NOT_FOUND", "Pharmacist not found")
        if (pharmacist.userType != UserType.PHARMACIST) {
            throw PrescriptionErrors.forbidden("PHARMACIST_REQUIRED", "Only a pharmacist can register a prescription")
        }
        return pharmacist
    }

    private fun requireActiveMember(
        careGroupId: Long,
        actorUserId: Long,
    ) {
        val isActiveMember =
            members.existsByCareGroupIdAndUserIdAndStatus(
                careGroupId = careGroupId,
                userId = actorUserId,
                status = MemberStatus.ACTIVE,
            )
        if (!isActiveMember) {
            throw PrescriptionErrors.forbidden(
                "CARE_GROUP_MEMBER_REQUIRED",
                "Only active care group members can read dose schedules",
            )
        }
    }

    private fun validateDateRange(request: CreatePrescriptionRequest) {
        val endDate = request.endDate ?: return
        if (endDate.isBefore(request.startDate)) {
            throw PrescriptionErrors.badRequest("INVALID_DATE_RANGE", "endDate must not be before startDate")
        }
    }

    private fun findOrCreatePharmacy(request: PharmacyRequest): PharmacyEntity {
        val name = request.name.trim()
        val phone = request.phone.trim()
        return pharmacies.findByNameAndPhone(name, phone)
            ?: pharmacies.save(
                PharmacyEntity(
                    name = name,
                    phone = phone,
                    address = request.address?.trim()?.ifBlank { null },
                ),
            )
    }

    private fun saveSchedule(
        prescription: PrescriptionEntity,
        request: CreateDoseScheduleRequest,
    ): DoseScheduleEntity {
        val pillCount = validatedPillCount(request)
        val schedule =
            doseSchedules.save(
                DoseScheduleEntity(
                    prescription = prescription,
                    slot = request.slot,
                    label = request.label.trim(),
                    packetNo = request.packetNo,
                    scheduledTime = request.scheduledTime,
                    mealRelation = request.mealRelation,
                    mealOffsetMin = request.mealOffsetMin,
                    pillCount = pillCount,
                    doseBasis = request.doseBasis ?: deriveDoseBasis(request.mealRelation, request.slot),
                    active = true,
                ),
            )

        val items =
            request.items.map {
                val medication = findOrCreateMedication(it)
                doseScheduleItems.save(
                    DoseScheduleItemEntity(
                        doseSchedule = schedule,
                        medication = medication,
                        count = it.count,
                    ),
                )
            }
        schedule.items.addAll(items)

        return schedule
    }

    private fun validatedPillCount(request: CreateDoseScheduleRequest): Int {
        val itemKeys =
            request.items.map {
                it.medicationName.trim() to it.category?.trim()?.ifBlank { null }
            }
        if (itemKeys.toSet().size != itemKeys.size) {
            throw PrescriptionErrors.badRequest(
                "DUPLICATE_SCHEDULE_ITEM",
                "A medication can appear only once in a dose schedule",
            )
        }
        val totalItemCount = request.items.sumOf { it.count }
        val requestedPillCount = request.pillCount
        if (requestedPillCount != null && requestedPillCount != totalItemCount) {
            throw PrescriptionErrors.badRequest("PILL_COUNT_MISMATCH", "pillCount must equal the sum of item counts")
        }
        return requestedPillCount ?: totalItemCount
    }

    private fun findOrCreateMedication(request: CreateDoseScheduleItemRequest): MedicationEntity {
        val name = request.medicationName.trim()
        val category = request.category?.trim()?.ifBlank { null }
        return medications.findFirstByNameAndCategory(name, category)
            ?: medications.save(
                MedicationEntity(
                    name = name,
                    category = category,
                    description = request.description?.trim()?.ifBlank { null },
                    shape = request.shape ?: PillShape.ROUND,
                ),
            )
    }

    private fun deriveDoseBasis(
        mealRelation: MealRelation,
        slot: DoseSlot,
    ): DoseBasis =
        when {
            mealRelation == MealRelation.BEFORE_MEAL -> DoseBasis.BEFORE_MEAL
            mealRelation == MealRelation.AFTER_MEAL -> DoseBasis.AFTER_MEAL
            mealRelation == MealRelation.WITH_MEAL -> DoseBasis.AFTER_MEAL
            slot == DoseSlot.BEDTIME -> DoseBasis.BEDTIME
            else -> DoseBasis.FIXED
        }
}
