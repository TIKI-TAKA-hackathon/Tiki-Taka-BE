-- V7__prescription_005_fields.sql
-- Spec 005 additions on existing tables (no new tables). media=V5, dose_events=V6, this=V7.
-- DB CHECK values are LOWERCASE; Kotlin enum names stay UPPERCASE (bridged via AttributeConverters).

-- 1) medications.shape (R6): round|oval|capsule, default round.
ALTER TABLE medications
    ADD COLUMN shape VARCHAR(20) NOT NULL DEFAULT 'round'
        CHECK (shape IN ('round', 'oval', 'capsule'));

-- 2) prescriptions.dispensing_type (U3): pouch|organizer, default pouch.
ALTER TABLE prescriptions
    ADD COLUMN dispensing_type VARCHAR(20) NOT NULL DEFAULT 'pouch'
        CHECK (dispensing_type IN ('pouch', 'organizer'));

-- 3) prescriptions.registration_code (PRV-01 / R3): nullable, unique when present.
ALTER TABLE prescriptions
    ADD COLUMN registration_code VARCHAR(64) NULL;

CREATE UNIQUE INDEX ux_prescriptions_registration_code
    ON prescriptions (registration_code)
    WHERE registration_code IS NOT NULL;

-- 4) dose_schedules.dose_basis: canonical 복용기준. Add nullable, backfill, then NOT NULL + CHECK.
ALTER TABLE dose_schedules
    ADD COLUMN dose_basis VARCHAR(20) NULL;

UPDATE dose_schedules
SET dose_basis = CASE
    WHEN meal_relation = 'BEFORE_MEAL' THEN 'before_meal'
    WHEN meal_relation = 'AFTER_MEAL' THEN 'after_meal'
    WHEN meal_relation = 'WITH_MEAL' THEN 'after_meal'
    WHEN meal_relation = 'NONE' AND slot = 'BEDTIME' THEN 'bedtime'
    ELSE 'fixed'
END;

ALTER TABLE dose_schedules
    ALTER COLUMN dose_basis SET NOT NULL;

ALTER TABLE dose_schedules
    ADD CONSTRAINT ck_dose_schedules_dose_basis
        CHECK (dose_basis IN ('before_meal', 'after_meal', 'bedtime', 'empty_stomach', 'fixed'));
