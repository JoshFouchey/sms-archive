-- DEV ONLY: Full reset of public schema and Flyway history so migrations (V1, V2, ...) re-run
-- CAUTION: Destroys ALL data in the database. Do NOT run in production.

-- 1. Drop and recreate schema (drops tables, indexes, sequences, functions, triggers, etc.)
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO public; -- adjust if you have a stricter role model

-- 2. (Optional) If you prefer to keep the schema but wipe data instead, comment out above and use:
-- TRUNCATE TABLE message_parts, messages, contacts RESTART IDENTITY CASCADE;
-- DELETE FROM flyway_schema_history WHERE success = false; -- remove failed entries if any

-- 3. After running this script, restart the Spring Boot application so Flyway applies migrations fresh.
-- Flyway will recreate flyway_schema_history and apply V1__baseline.sql then V2__message_dedupe_index.sql.

