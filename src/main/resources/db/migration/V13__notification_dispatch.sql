-- V13__notification_dispatch.sql
-- WP3b caregiver notification DISPATCH tracking (카카오 알림톡 / SMS).
--
-- A MISSED/ESCALATION notification is delivered to the care group's caregiver(s)
-- (senior misses a dose -> notify the 대표자/primary caregiver). These columns
-- record the outcome of that delivery so the evaluator stays idempotent:
-- dispatched_at IS NOT NULL means the row was already sent and must not re-send.
--
-- The provider is a STUB for now (channel = 'STUB'). Real Kakao 알림톡 needs a
-- business channel + a pre-approved template; SMS needs a provider API key.
-- Wording stays at the "약 미확인" (medication unconfirmed) level.

ALTER TABLE notifications
    ADD COLUMN dispatched_at    TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN dispatch_target  VARCHAR(30) NULL,
    ADD COLUMN dispatch_channel VARCHAR(20) NULL;
