-- V3: Add non-unique composite prefix index to accelerate duplicate existence checks
-- This index matches the leading columns used in duplicate probing before body comparison.
-- The existing V2 unique functional index (with md5(lower(coalesce(body,'')))) remains for enforcement.
-- Having both lets the planner use the cheaper prefix index for broad filtering and fall back
-- to the functional unique index when necessary.

CREATE INDEX IF NOT EXISTS ix_messages_dedupe_prefix
    ON messages (contact_id, "timestamp", msg_box, protocol);

