package xyz.stdiodh.gojjibom.dose

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class DoseIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        private val sequence = AtomicLong()

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()

    @Test
    fun `active schedule generates one requested-date event across repeated list reads`() {
        val fixture = createFixture()

        val first = listDoses(fixture.seniorId, fixture.ownerId)
        val second = listDoses(fixture.seniorId, fixture.ownerId)
        val firstEvent = eventAt(first)
        val secondEvent = eventAt(second)

        assertThat(first.path("events").size()).isEqualTo(1)
        assertThat(firstEvent.path("scheduledDate").asText()).isEqualTo(DOSE_DATE.toString())
        assertThat(firstEvent.path("scheduledTime").asText()).startsWith("08:30")
        assertThat(firstEvent.path("status").asText()).isEqualTo("SCHEDULED")
        assertThat(second.path("events").size()).isEqualTo(1)
        assertThat(secondEvent.path("id").asLong()).isEqualTo(firstEvent.path("id").asLong())

        val row =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS event_count, MIN(scheduled_at) AS scheduled_at
                FROM dose_events
                WHERE dose_schedule_id = ? AND scheduled_date = ?
                """.trimIndent(),
                { resultSet, _ ->
                    GeneratedEventRow(
                        count = resultSet.getLong("event_count"),
                        scheduledAt = resultSet.getObject("scheduled_at", OffsetDateTime::class.java),
                    )
                },
                fixture.scheduleId,
                DOSE_DATE,
            )
        assertThat(row.count).isEqualTo(1)
        assertThat(row.scheduledAt.toInstant()).isEqualTo(SCHEDULED_INSTANT)
    }

    @Test
    fun `active member can list get and confirm with exact persisted confirmation`() {
        val fixture = createFixture()
        val eventId = eventAt(listDoses(fixture.seniorId, fixture.ownerId)).path("id").asLong()

        mockMvc
            .perform(
                get("/api/v1/dose-events/$eventId")
                    .param("actorUserId", fixture.activeFamilyId.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(eventId))
            .andExpect(jsonPath("$.data.status").value("SCHEDULED"))

        val confirmed =
            mockMvc
                .perform(
                    post("/api/v1/dose-events/$eventId:confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(fixture.activeFamilyId, "BUTTON")),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("TAKEN"))
                .andExpect(jsonPath("$.data.confirmMethod").value("BUTTON"))
                .andExpect(jsonPath("$.data.confirmedByUserId").value(fixture.activeFamilyId))
                .andReturn()
                .dataNode()

        assertThat(OffsetDateTime.parse(confirmed.path("confirmedAt").asText()).toInstant())
            .isEqualTo(FIXED_INSTANT)

        val row = confirmationRow(eventId)
        assertThat(row.status).isEqualTo("TAKEN")
        assertThat(row.confirmedAt?.toInstant()).isEqualTo(FIXED_INSTANT)
        assertThat(row.confirmMethod).isEqualTo("BUTTON")
        assertThat(row.confirmedById).isEqualTo(fixture.activeFamilyId)
    }

    @Test
    fun `pending removed and outside users cannot list get or confirm dose events`() {
        val fixture = createFixture()
        val eventId = eventAt(listDoses(fixture.seniorId, fixture.ownerId)).path("id").asLong()
        val deniedActors = listOf(fixture.pendingId, fixture.removedId, fixture.outsiderId)

        deniedActors.forEach { actorId ->
            mockMvc
                .perform(
                    get("/api/v1/seniors/${fixture.seniorId}/doses")
                        .param("date", DOSE_DATE.toString())
                        .param("actorUserId", actorId.toString()),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("CARE_GROUP_MEMBER_REQUIRED"))

            mockMvc
                .perform(
                    get("/api/v1/dose-events/$eventId")
                        .param("actorUserId", actorId.toString()),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("CARE_GROUP_MEMBER_REQUIRED"))

            mockMvc
                .perform(
                    post("/api/v1/dose-events/$eventId:confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(actorId, "BUTTON")),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("CARE_GROUP_MEMBER_REQUIRED"))
        }

        assertThat(confirmationRow(eventId))
            .isEqualTo(
                ConfirmationRow(
                    status = "SCHEDULED",
                    confirmedAt = null,
                    confirmMethod = null,
                    confirmedById = null,
                    photoImageId = null,
                    photoReviewStatus = null,
                ),
            )
    }

    @Test
    fun `reconfirming keeps the first confirmation metadata and photo`() {
        val fixture = createFixture()
        val eventId = eventAt(listDoses(fixture.seniorId, fixture.ownerId)).path("id").asLong()
        val firstImageId = insertDoseEventImage(fixture.groupId, eventId, fixture.ownerId)
        val secondImageId = insertDoseEventImage(fixture.groupId, eventId, fixture.activeFamilyId)

        mockMvc
            .perform(
                post("/api/v1/dose-events/$eventId:confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(confirmBody(fixture.ownerId, "BUTTON", firstImageId)),
            ).andExpect(status().isOk)

        val first = confirmationRow(eventId)

        mockMvc
            .perform(
                post("/api/v1/dose-events/$eventId:confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(confirmBody(fixture.activeFamilyId, "CAREGIVER", secondImageId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.confirmMethod").value("BUTTON"))
            .andExpect(jsonPath("$.data.confirmedByUserId").value(fixture.ownerId))
            .andExpect(jsonPath("$.data.photoImageId").value(firstImageId))

        assertThat(confirmationRow(eventId)).isEqualTo(first)
        assertThat(first.photoImageId).isEqualTo(firstImageId)
        assertThat(first.photoReviewStatus).isEqualTo("PENDING")
    }

    @Test
    fun `confirmation accepts only an image owned by the same event and care group`() {
        val fixture = createFixture()
        val targetEventId = eventAt(listDoses(fixture.seniorId, fixture.ownerId)).path("id").asLong()
        val otherScheduleId = insertSchedule(fixture.prescriptionId, "점심약", LocalTime.of(12, 30))
        listDoses(fixture.seniorId, fixture.ownerId)
        val otherEventId = eventIdFor(otherScheduleId)
        val otherEventImageId = insertDoseEventImage(fixture.groupId, otherEventId, fixture.ownerId)

        val otherGroup = createFixture()
        val otherGroupImageId =
            insertDoseEventImage(
                careGroupId = otherGroup.groupId,
                doseEventId = targetEventId,
                uploadedBy = otherGroup.ownerId,
            )

        listOf(otherEventImageId, otherGroupImageId).forEach { imageId ->
            mockMvc
                .perform(
                    post("/api/v1/dose-events/$targetEventId:confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(fixture.ownerId, "BUTTON", imageId)),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_IMAGE_OWNER"))

            assertThat(confirmationRow(targetEventId).status).isEqualTo("SCHEDULED")
            assertThat(confirmationRow(targetEventId).photoImageId).isNull()
        }

        val validImageId = insertDoseEventImage(fixture.groupId, targetEventId, fixture.ownerId)
        mockMvc
            .perform(
                post("/api/v1/dose-events/$targetEventId:confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(confirmBody(fixture.ownerId, "CAREGIVER", validImageId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.photoImageId").value(validImageId))
            .andExpect(jsonPath("$.data.photoReviewStatus").value("PENDING"))
    }

    @Test
    fun `postgres unique constraint rejects a duplicate schedule date event`() {
        val fixture = createFixture()
        listDoses(fixture.seniorId, fixture.ownerId)

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO dose_events (
                    dose_schedule_id,
                    senior_id,
                    scheduled_date,
                    scheduled_at,
                    status
                )
                VALUES (?, ?, ?, ?, 'SCHEDULED')
                """.trimIndent(),
                fixture.scheduleId,
                fixture.seniorId,
                DOSE_DATE,
                OffsetDateTime.ofInstant(SCHEDULED_INSTANT, SEOUL_ZONE),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        val count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM dose_events
                WHERE dose_schedule_id = ? AND scheduled_date = ?
                """.trimIndent(),
                Long::class.java,
                fixture.scheduleId,
                DOSE_DATE,
            )
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `dose list requires an existing senior with a care group`() {
        val fixture = createFixture()
        val suffix = sequence.incrementAndGet()
        val seniorWithoutGroup = insertUser("SENIOR", "Lonely senior $suffix", "010-dose-l$suffix")

        listOf(
            Triple(UNKNOWN_ID, 404, "SENIOR_NOT_FOUND"),
            Triple(fixture.ownerId, 400, "SENIOR_REQUIRED"),
            Triple(seniorWithoutGroup, 400, "CARE_GROUP_REQUIRED"),
        ).forEach { (seniorId, httpStatus, code) ->
            mockMvc
                .perform(
                    get("/api/v1/seniors/$seniorId/doses")
                        .param("date", DOSE_DATE.toString())
                        .param("actorUserId", fixture.ownerId.toString()),
                ).andExpect(status().`is`(httpStatus))
                .andExpect(jsonPath("$.error.code").value(code))
        }

        val generated =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dose_events WHERE senior_id IN (?, ?)",
                Long::class.java,
                fixture.ownerId,
                seniorWithoutGroup,
            )
        assertThat(generated).isZero()
    }

    @Test
    fun `unknown dose event returns 404 for get confirm and photo review`() {
        val fixture = createFixture()

        mockMvc
            .perform(
                get("/api/v1/dose-events/$UNKNOWN_ID")
                    .param("actorUserId", fixture.ownerId.toString()),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("DOSE_EVENT_NOT_FOUND"))

        mockMvc
            .perform(
                post("/api/v1/dose-events/$UNKNOWN_ID:confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(confirmBody(fixture.ownerId, "BUTTON")),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("DOSE_EVENT_NOT_FOUND"))

        mockMvc
            .perform(
                patch("/api/v1/dose-events/$UNKNOWN_ID/photo:review")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actorUserId": ${fixture.ownerId}, "reviewStatus": "REVIEWED"}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("DOSE_EVENT_NOT_FOUND"))
    }

    private fun listDoses(
        seniorId: Long,
        actorUserId: Long,
    ): JsonNode =
        mockMvc
            .perform(
                get("/api/v1/seniors/$seniorId/doses")
                    .param("date", DOSE_DATE.toString())
                    .param("actorUserId", actorUserId.toString()),
            ).andExpect(status().isOk)
            .andReturn()
            .dataNode()

    private fun confirmBody(
        actorUserId: Long,
        method: String,
        imageId: Long? = null,
    ): String {
        val imageIdJson = imageId?.toString() ?: "null"
        return """
            {
              "actorUserId": $actorUserId,
              "method": "$method",
              "imageId": $imageIdJson
            }
            """.trimIndent()
    }

    private fun eventAt(response: JsonNode): JsonNode = response.path("events").path(0)

    private fun createFixture(): DoseFixture {
        val suffix = sequence.incrementAndGet()
        val seniorId = insertUser("SENIOR", "Senior $suffix", "010-dose-s$suffix")
        val ownerId = insertUser("CAREGIVER", "Owner $suffix", "010-dose-o$suffix")
        val activeFamilyId = insertUser("CAREGIVER", "Family $suffix", "010-dose-a$suffix")
        val pendingId = insertUser("CAREGIVER", "Pending $suffix", "010-dose-p$suffix")
        val removedId = insertUser("CAREGIVER", "Removed $suffix", "010-dose-r$suffix")
        val outsiderId = insertUser("CAREGIVER", "Outsider $suffix", "010-dose-x$suffix")
        val groupId =
            insertFixtureGroup(
                suffix = suffix,
                seniorId = seniorId,
                ownerId = ownerId,
                activeFamilyId = activeFamilyId,
                pendingId = pendingId,
                removedId = removedId,
            )
        val (prescriptionId, scheduleId) = insertFixturePrescription(suffix, seniorId)

        return DoseFixture(
            groupId = groupId,
            seniorId = seniorId,
            ownerId = ownerId,
            activeFamilyId = activeFamilyId,
            pendingId = pendingId,
            removedId = removedId,
            outsiderId = outsiderId,
            prescriptionId = prescriptionId,
            scheduleId = scheduleId,
        )
    }

    private fun insertFixtureGroup(
        suffix: Long,
        seniorId: Long,
        ownerId: Long,
        activeFamilyId: Long,
        pendingId: Long,
        removedId: Long,
    ): Long {
        val groupId =
            returningId(
                """
                INSERT INTO care_groups (senior_id, name)
                VALUES (?, ?)
                RETURNING id
                """,
                seniorId,
                "Dose group $suffix",
            )

        insertMember(groupId, ownerId, "OWNER", "ACTIVE", isPrimary = true)
        insertMember(groupId, activeFamilyId, "FAMILY", "ACTIVE")
        insertMember(groupId, pendingId, "FAMILY", "PENDING")
        insertMember(groupId, removedId, "FAMILY", "REMOVED")
        return groupId
    }

    private fun insertFixturePrescription(
        suffix: Long,
        seniorId: Long,
    ): Pair<Long, Long> {
        val pharmacyId =
            returningId(
                """
                INSERT INTO pharmacies (name, phone)
                VALUES (?, ?)
                RETURNING id
                """,
                "Dose pharmacy $suffix",
                "02-dose-$suffix",
            )
        val prescriptionId =
            returningId(
                """
                INSERT INTO prescriptions (
                    senior_id,
                    pharmacy_id,
                    prescribed_date,
                    start_date,
                    end_date,
                    status,
                    dispensing_type
                )
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'pouch')
                RETURNING id
                """,
                seniorId,
                pharmacyId,
                DOSE_DATE,
                DOSE_DATE,
                DOSE_DATE.plusDays(7),
            )
        val scheduleId = insertSchedule(prescriptionId, "아침약", LocalTime.of(8, 30))
        val medicationId =
            returningId(
                """
                INSERT INTO medications (name, category, shape)
                VALUES (?, ?, 'round')
                RETURNING id
                """,
                "Dose medication $suffix",
                "혈압",
            )
        jdbcTemplate.update(
            """
            INSERT INTO dose_schedule_items (dose_schedule_id, medication_id, count)
            VALUES (?, ?, 1)
            """.trimIndent(),
            scheduleId,
            medicationId,
        )
        return prescriptionId to scheduleId
    }

    private fun insertUser(
        userType: String,
        name: String,
        phone: String,
    ): Long =
        returningId(
            """
            INSERT INTO users (user_type, name, phone)
            VALUES (?, ?, ?)
            RETURNING id
            """,
            userType,
            name,
            phone,
        )

    private fun insertMember(
        careGroupId: Long,
        userId: Long,
        role: String,
        memberStatus: String,
        isPrimary: Boolean = false,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO care_group_members (
                care_group_id,
                user_id,
                role,
                status,
                joined_at,
                is_primary
            )
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
            """.trimIndent(),
            careGroupId,
            userId,
            role,
            memberStatus,
            isPrimary,
        )
    }

    private fun insertSchedule(
        prescriptionId: Long,
        label: String,
        scheduledTime: LocalTime,
    ): Long =
        returningId(
            """
            INSERT INTO dose_schedules (
                prescription_id,
                slot,
                label,
                packet_no,
                scheduled_time,
                meal_relation,
                meal_offset_min,
                pill_count,
                active,
                dose_basis
            )
            VALUES (?, 'CUSTOM', ?, 1, ?, 'NONE', NULL, 1, TRUE, 'fixed')
            RETURNING id
            """,
            prescriptionId,
            label,
            scheduledTime,
        )

    private fun insertDoseEventImage(
        careGroupId: Long,
        doseEventId: Long,
        uploadedBy: Long,
    ): Long {
        val imageSuffix = sequence.incrementAndGet()
        return returningId(
            """
            INSERT INTO images (
                owner_type,
                owner_id,
                object_key,
                content_type,
                size_bytes,
                uploaded_by
            )
            VALUES ('DOSE_EVENT', ?, ?, 'image/jpeg', 512, ?)
            RETURNING id
            """,
            doseEventId,
            "images/$careGroupId/dose-$imageSuffix.jpg",
            uploadedBy,
        )
    }

    private fun eventIdFor(scheduleId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM dose_events
            WHERE dose_schedule_id = ? AND scheduled_date = ?
            """.trimIndent(),
            Long::class.java,
            scheduleId,
            DOSE_DATE,
        ) ?: error("dose event id not found")

    private fun confirmationRow(eventId: Long): ConfirmationRow =
        jdbcTemplate.queryForObject(
            """
            SELECT
                status,
                confirmed_at,
                confirm_method,
                confirmed_by,
                photo_image_id,
                photo_review_status
            FROM dose_events
            WHERE id = ?
            """.trimIndent(),
            { resultSet, _ ->
                ConfirmationRow(
                    status = resultSet.getString("status"),
                    confirmedAt = resultSet.getObject("confirmed_at", OffsetDateTime::class.java),
                    confirmMethod = resultSet.getString("confirm_method"),
                    confirmedById = resultSet.getLongOrNull("confirmed_by"),
                    photoImageId = resultSet.getLongOrNull("photo_image_id"),
                    photoReviewStatus = resultSet.getString("photo_review_status"),
                )
            },
            eventId,
        )

    private fun returningId(
        sql: String,
        vararg arguments: Any,
    ): Long =
        jdbcTemplate.queryForObject(
            sql.trimIndent(),
            Long::class.java,
            *arguments,
        ) ?: error("insert did not return an id")

    private fun java.sql.ResultSet.getLongOrNull(columnLabel: String): Long? {
        val value = getLong(columnLabel)
        return if (wasNull()) null else value
    }

    private fun MvcResult.dataNode(): JsonNode = objectMapper.readTree(response.contentAsString).path("data")

    private data class DoseFixture(
        val groupId: Long,
        val seniorId: Long,
        val ownerId: Long,
        val activeFamilyId: Long,
        val pendingId: Long,
        val removedId: Long,
        val outsiderId: Long,
        val prescriptionId: Long,
        val scheduleId: Long,
    )

    private data class GeneratedEventRow(
        val count: Long,
        val scheduledAt: OffsetDateTime,
    )

    private data class ConfirmationRow(
        val status: String,
        val confirmedAt: OffsetDateTime?,
        val confirmMethod: String?,
        val confirmedById: Long?,
        val photoImageId: Long?,
        val photoReviewStatus: String?,
    )

    @TestConfiguration
    class FixedClockConfiguration {
        @Bean
        @Primary
        fun fixedClock(): Clock = Clock.fixed(FIXED_INSTANT, SEOUL_ZONE)
    }
}

private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
private val DOSE_DATE: LocalDate = LocalDate.of(2026, 7, 2)
private val FIXED_INSTANT: Instant = Instant.parse("2026-07-02T03:00:00Z")
private val SCHEDULED_INSTANT: Instant = Instant.parse("2026-07-01T23:30:00Z")
private const val UNKNOWN_ID = 999_999_999L
