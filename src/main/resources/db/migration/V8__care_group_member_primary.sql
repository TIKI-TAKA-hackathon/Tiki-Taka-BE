-- V8__care_group_member_primary.sql
-- 대표자(primary) on care_group_members. Single primary per group among non-REMOVED rows.
-- viewerOnly is DERIVED in the DTO (= !is_primary), never stored.

ALTER TABLE care_group_members
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: the earliest-joined active OWNER of each group becomes the 대표자 (primary).
UPDATE care_group_members m
SET is_primary = TRUE
FROM (
    SELECT DISTINCT ON (care_group_id) id
    FROM care_group_members
    WHERE role = 'OWNER' AND status = 'ACTIVE'
    ORDER BY care_group_id, joined_at ASC NULLS LAST, id ASC
) first_owner
WHERE m.id = first_owner.id;

-- Enforce single primary per care group (only counts non-removed rows).
CREATE UNIQUE INDEX ux_care_group_members_primary
    ON care_group_members (care_group_id)
    WHERE is_primary = TRUE AND status <> 'REMOVED';
