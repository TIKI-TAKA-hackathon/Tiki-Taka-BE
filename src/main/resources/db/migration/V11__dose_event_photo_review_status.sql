-- V11__dose_event_photo_review_status.sql
-- WP2 caregiver photo gallery: per-dose-event photo review state.
-- Nullable; set to PENDING when a confirmation photo is attached, then a
-- caregiver moves it to REVIEWED/FLAGGED. CHECK enum matches the UPPERCASE
-- Kotlin enum and the existing status/confirm_method style.

ALTER TABLE dose_events
    ADD COLUMN photo_review_status VARCHAR(20) NULL
        CHECK (photo_review_status IS NULL OR photo_review_status IN ('PENDING', 'REVIEWED', 'FLAGGED'));
