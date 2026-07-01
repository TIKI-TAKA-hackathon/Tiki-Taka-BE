# Frontend/backend coverage checklist

This checklist tracks the Firebase frontend at `https://gojjibom.web.app` against backend
data and APIs. The frontend is currently mock-data driven and shows `https://api.stdiodh.xyz/api`
as the API base. Backend paths below assume the frontend calls `/api/v1/...`.

## Current screen data requirements

| Frontend route | Required backend data | Planned API | Status |
| --- | --- | --- | --- |
| `/onboarding` | Product notice, caregiver/senior entry points | Static + `POST /api/v1/care-groups` | PR #2 |
| `/senior/register` | Senior name, phone, birth date, caregiver owner | `POST /api/v1/care-groups` | PR #2 |
| `/senior/connected` | Senior/group connection confirmation | `GET /api/v1/care-groups/{id}` | PR #2 |
| `/senior/login` | Device pairing/login token | `POST /api/v1/senior-devices:pair` | Planned |
| `/senior` | Date label, next dose, all today's doses, completion state, baseline note, TTS text | `GET /api/v1/seniors/{id}/doses?date=YYYY-MM-DD` | Planned 003 |
| `/senior/dose` | Dose detail, meal tag, packet number, pill count, medication list, note, TTS/play URL | `GET /api/v1/dose-events/{id}` | Planned 003 + media |
| `/senior/done` | Confirmation result, confirmed time, next dose summary | `POST /api/v1/dose-events/{id}:confirm` | Planned 003 |
| `/senior/photo` | Medication/dose image view URL | `GET /api/v1/images/{id}/view-url` | PR #3, migration to rebase |
| `/senior/alerts` | Senior reminders and confirmation notifications | `GET /api/v1/seniors/{id}/notifications` | Planned 007 |
| `/caregiver` | Patient summary, group counts, dose status, confirmations, inventory, alert card, 7-day summary | `GET /api/v1/care-groups/{id}/dashboard?date=YYYY-MM-DD` | Planned 004 |
| `/caregiver/manage` | Active/pending group members, invite link, approval/reject/remove | `GET /api/v1/care-groups/{id}`, invite/member APIs | PR #2 |
| `/caregiver/pills` | Remaining medication/inventory per prescription and per medication | `GET /api/v1/care-groups/{id}/inventory` | Planned 005 |
| `/caregiver/timeline` | Reminder, confirmation, missed-dose, escalation events | `GET /api/v1/care-groups/{id}/timeline?date=YYYY-MM-DD` | Planned 004+ |

## Implementation order

1. `001-care-group-and-invites` — group, senior, owner, invite approval.
2. `002-prescriptions-and-dose-schedules` — pharmacy prescription source data, packed
   schedules, medication items.
3. `003-daily-doses-and-confirmation` — daily dose events, senior home/dose detail,
   button confirmation, adherence logs.
4. `004-guardian-dashboard-and-timeline` — caregiver dashboard aggregates, timeline,
   alert cards, 7-day summary.
5. `005-inventory-and-pharmacy` — remaining counts, expected depletion, refill D-day,
   pharmacist-facing prescription updates.
6. `006-media-tts-and-images` — private image URLs and TTS play URLs. The existing media
   PR must be rebased/renumbered after the core schema phases before merge.
7. `007-notifications-and-escalation` — reminder delivery, missed-dose ladder,
   notification inbox.

## Data that must not be missed

- Senior: id, name, phone, birth date, care group id.
- Care-group member: id, user id, name, role, status, joined time.
- Invite: token, expiry, use count, max uses, revoked state.
- Prescription: pharmacy, pharmacist, prescribed/start/end dates, active/ended status.
- Dose schedule: slot, label, packet number, scheduled time, meal relation, meal offset,
  pill count, active state.
- Medication item: medication name, category, description/photo metadata, count in packet.
- Daily dose event: scheduled date/time, status, confirmed time, confirm method,
  confirmed-by actor.
- Dashboard aggregate: next dose, per-dose statuses, completion counts, 7-day adherence,
  current alert.
- Timeline event: event type, time, title/body, related dose event, target/actor.
- Inventory: remaining count per medication/prescription, unit, expected depletion date,
  refill due date.
- Media: private object key, content type, size, owner type/id, short-lived signed URLs.
- TTS: Korean medication-info script, voice, normalized text hash, cached object key.
