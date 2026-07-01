# 001 — Care groups and invite approval

## Goal
Implement the first Phase 1 slice: create a care group for one senior, invite caregivers
with a 24-hour invite link, and let the owner approve, reject, or remove members.

This establishes the group and membership foundation needed before prescriptions and daily
dose tracking are added.

## Assumptions
- Authentication is not implemented yet. Mutating owner actions receive `ownerUserId` or
  `actorUserId` in the request and validate that the user is an active OWNER of the group.
- `POST /api/v1/care-groups` creates or reuses the senior and owner users by phone number,
  then creates one care group and one ACTIVE OWNER membership.
- A senior can belong to one care group in this MVP slice.
- Invite links expire after 24 hours. Creating a new invite link revokes any existing
  unrevoked invite links for the group.
- Invite acceptance creates a CAREGIVER user when needed and a PENDING group member.
- Rejected or removed members use the ERD status `REMOVED`.

## API contract
- `POST /api/v1/care-groups`
  - Request:
    ```json
    {
      "name": "어머니 복약 상태",
      "senior": { "name": "김고찌", "phone": "010-0000-0001", "birthDate": "1940-01-01" },
      "owner": { "name": "김보호", "phone": "010-0000-0002" }
    }
    ```
  - Response: `201 { "data": { "id", "name", "senior", "members" }, "error": null }`.
- `GET /api/v1/care-groups/{id}`
  - Response: `200 { "data": { "id", "name", "senior", "members" }, "error": null }`.
- `POST /api/v1/care-groups/{id}/invite-links`
  - Request: `{ "ownerUserId": 1, "maxUses": 1 }`. `maxUses` is optional.
  - Response: `201 { "data": { "id", "token", "expiresAt", "maxUses", "useCount" }, "error": null }`.
- `POST /api/v1/invites/{token}:accept`
  - Request: `{ "name": "김가족", "phone": "010-0000-0003", "role": "FAMILY" }`.
  - Allowed roles: `FAMILY`, `SOCIAL_WORKER`.
  - Response: `201 { "data": { "id", "user", "role", "status", "joinedAt" }, "error": null }`.
- `PATCH /api/v1/care-groups/{id}/members/{memberId}`
  - Request: `{ "actorUserId": 1, "status": "ACTIVE", "role": "FAMILY" }`.
  - `status` and `role` are optional, but at least one must be present.
  - Allowed status values: `ACTIVE`, `REMOVED`.
  - Response: `200 { "data": { "id", "user", "role", "status", "joinedAt" }, "error": null }`.
- `DELETE /api/v1/care-groups/{id}/members/{memberId}?actorUserId=1`
  - Response: `200 { "data": { "id", "user", "role", "status", "joinedAt" }, "error": null }`.
- Error responses use the common envelope:
  `{ "data": null, "error": { "code": "...", "message": "..." } }`.

## Acceptance criteria
- [ ] Flyway creates `users`, `care_groups`, `care_group_members`, and `invite_links`
      using table and column names from `docs/ERD.md`.
- [ ] Creating a care group creates/reuses one SENIOR user, one CAREGIVER owner, and one
      ACTIVE OWNER membership.
- [ ] Only an ACTIVE OWNER can issue invite links, approve/reject members, or remove members.
- [ ] Reissuing an invite link revokes previous unrevoked links for the same group.
- [ ] Accepting a valid invite creates a PENDING FAMILY or SOCIAL_WORKER member.
- [ ] Expired, revoked, overused, duplicate-member, and invalid-owner cases return envelope errors.
- [ ] Owner membership cannot be removed through member moderation endpoints.
- [ ] Controllers return DTOs/envelopes, never JPA entities.
- [ ] `./gradlew ktlintCheck detekt test build` passes.

## Out of scope
- Login/session/JWT authentication and authorization middleware.
- Senior device pairing.
- Prescriptions, medications, dose schedules, dose events, adherence, dashboard, notifications,
  reminders, escalation, inventory, and pharmacy workflows.
- Frontend and infra changes.
