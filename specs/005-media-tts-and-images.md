# 005 — Media TTS and images

## Goal
Add Phase 5 media support: private image storage through presigned S3-compatible URLs
and cached Korean TTS MP3 clips for medication information announcements.

This keeps blobs out of PostgreSQL and lets the same backend code target AWS S3 or MinIO.

## Assumptions
- Authentication is not implemented yet. Media requests include `actorUserId` and
  `careGroupId`; the backend authorizes access by requiring an ACTIVE row in
  `care_group_members`.
- `origin/main` does not yet contain Phase 1 tables, so this slice creates the minimal
  `users`, `care_groups`, and `care_group_members` tables needed for membership checks.
- `images.owner_type` and `images.owner_id` are polymorphic metadata. Until medication and
  dose-event owner tables exist, the API also receives `careGroupId` for authorization.
- Images are stored in a private bucket. The DB stores only `object_key` and metadata.
- TTS text is generated from a fixed Korean medication-information template; clients cannot
  submit arbitrary medical advice text.
- `KOKORO_TTS_URL` points at a swappable worker. The included worker is a stub by default so
  backend build gates do not depend on model setup.

## API contract
- `POST /api/v1/media/images/upload-url`
  - Request:
    ```json
    {
      "actorUserId": 1,
      "careGroupId": 1,
      "ownerType": "MEDICATION",
      "ownerId": 10,
      "contentType": "image/jpeg",
      "sizeBytes": 120000
    }
    ```
  - Response: `201 { "data": { "objectKey", "uploadUrl", "expiresAt", "requiredHeaders" }, "error": null }`.
- `POST /api/v1/media/images`
  - Request: image metadata with `actorUserId`, `careGroupId`, `ownerType`, `ownerId`,
    `objectKey`, `contentType`, and `sizeBytes`.
  - Response: `201 { "data": { "id", "ownerType", "ownerId", "objectKey", "contentType", "sizeBytes", "uploadedBy", "createdAt" }, "error": null }`.
- `GET /api/v1/media/images/{id}/view-url?actorUserId=1&careGroupId=1`
  - Response: `200 { "data": { "imageId", "viewUrl", "expiresAt" }, "error": null }`.
- `POST /api/v1/media/tts-clips`
  - Request:
    ```json
    {
      "actorUserId": 1,
      "careGroupId": 1,
      "voice": "ko_default",
      "doseLabel": "저녁약",
      "scheduledTime": "19:30"
    }
    ```
  - Response: `200 { "data": { "id", "voice", "text", "objectKey", "playUrl", "expiresAt", "durationMs" }, "error": null }`.
- Error responses use the common envelope:
  `{ "data": null, "error": { "code": "...", "message": "..." } }`.

## Acceptance criteria
- [ ] Flyway creates `tts_clips` and `images` with ERD column names; prerequisite
      membership tables exist for auth checks.
- [ ] S3-compatible storage uses AWS SDK v2 and supports bucket, region, endpoint, and
      optional static credentials from env.
- [ ] Image upload/view URLs are presigned, short-lived, and require ACTIVE care-group
      membership.
- [ ] Image content type and max size are validated before presigning and registration.
- [ ] TTS requests build a Korean medication-information sentence, hash normalized text
      with voice, cache hits avoid worker calls, and cache misses store MP3 bytes in object
      storage before saving `tts_clips`.
- [ ] The worker scaffold runs independently and can be replaced with real Kokoro setup.
- [ ] Controllers return DTOs/envelopes, never JPA entities.
- [ ] `./gradlew ktlintCheck detekt test build` passes.

## Out of scope
- Real authentication/JWT/session middleware.
- Medication, prescription, dose-event, dashboard, notification, and scheduler APIs.
- Real Kokoro model installation in backend build gates.
- Public buckets, storing image/MP3 blobs in PostgreSQL, CDN, lifecycle policies, and virus scanning.
