# 002 — Prescriptions and dose schedules

## Goal
Implement the prescription and dose schedule source data needed for the senior "today's
medicine" screen and caregiver monitoring screens.

This slice lets a pharmacist register a senior's prescription, packed dose schedules, and
medication items. It also lets active care-group members read the senior's active dose
schedules.

## Assumptions
- Authentication is not implemented yet. The registering pharmacist is passed as
  `pharmacistUserId`; the reader is passed as `actorUserId`.
- A prescription can be registered only for a `SENIOR` user that already has a care group.
- `pharmacistUserId` must point to an existing `PHARMACIST` user.
- Any `ACTIVE` member of the senior's care group can read dose schedules. `PENDING` and
  `REMOVED` members cannot.
- Medication rows are reused by exact `name` + `category` match. This keeps the current
  slice simple without introducing a medication catalog management API.
- If `pillCount` is supplied on a schedule, it must equal the sum of item counts. If it is
  omitted, the service stores the calculated sum.
- This slice stores schedule definitions only. It does not instantiate daily `dose_events`.

## API contract
- `POST /api/v1/seniors/{seniorId}/prescriptions`
  - Request:
    ```json
    {
      "pharmacistUserId": 10,
      "pharmacy": {
        "name": "고찌봄약국",
        "phone": "02-0000-0000",
        "address": "서울시 ..."
      },
      "prescribedDate": "2026-07-02",
      "startDate": "2026-07-02",
      "endDate": "2026-07-16",
      "schedules": [
        {
          "slot": "MORNING",
          "label": "아침약 · 1번 봉지",
          "packetNo": 1,
          "scheduledTime": "08:30",
          "mealRelation": "AFTER_MEAL",
          "mealOffsetMin": 30,
          "pillCount": 2,
          "items": [
            {
              "medicationName": "혈압약",
              "category": "혈압약",
              "description": "처방전에 등록된 약",
              "count": 1
            },
            {
              "medicationName": "위장약",
              "category": "위장약",
              "count": 1
            }
          ]
        }
      ]
    }
    ```
  - Response: `201 { "data": { "id", "seniorId", "pharmacy", "status", "schedules" }, "error": null }`.
- `GET /api/v1/seniors/{seniorId}/dose-schedules?actorUserId=1`
  - Response: `200 { "data": { "seniorId", "schedules" }, "error": null }`.
  - `schedules` are active schedules from active prescriptions, ordered by scheduled time.
- Error responses use the common envelope:
  `{ "data": null, "error": { "code": "...", "message": "..." } }`.

## Acceptance criteria
- [ ] Flyway creates `pharmacies`, `prescriptions`, `medications`,
      `dose_schedules`, and `dose_schedule_items` using table and column names from
      `docs/ERD.md`.
- [ ] A pharmacist can register a prescription with one or more schedules and one or more
      medication items per schedule.
- [ ] The API rejects non-senior prescription targets, seniors without a care group,
      non-pharmacist registrants, empty schedules, empty schedule items, invalid item counts,
      and mismatched `pillCount`.
- [ ] Only active care-group members can read a senior's active dose schedules.
- [ ] Dose schedule responses include slot, label, packet number, scheduled time,
      meal relation, pill count, prescription period, and medication item details.
- [ ] Controllers return DTOs/envelopes, never JPA entities.
- [ ] `./gradlew ktlintCheck detekt test build` passes.

## Out of scope
- Login/session/JWT authentication and authorization middleware.
- Daily `dose_events`, reminders, missed-dose detection, button/voice confirmation,
  adherence logs, dashboard aggregation, timeline, notifications, inventory counts, and
  medication image upload.
- Frontend and infra changes.
