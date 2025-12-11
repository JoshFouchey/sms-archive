#!/bin/bash
# Fix Flyway V4 Migration Checksum Mismatch

echo "🔧 Fixing Flyway V4 migration checksum..."

# The error message shows:
# -> Applied to database : -1666908351
# -> Resolved locally    : 1372284742

# Solution: Update the checksum in flyway_schema_history to match the current file

echo "Connecting to database..."
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
    -- Show current state
    SELECT version, description, checksum, success
    FROM flyway_schema_history
    WHERE version = '4';
"

echo ""
echo "Updating checksum to match local file..."
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
    UPDATE flyway_schema_history
    SET checksum = 1372284742
    WHERE version = '4';
"

echo ""
echo "Verifying fix..."
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
    SELECT version, description, checksum, success
    FROM flyway_schema_history
    WHERE version = '4';
"

echo ""
echo "✅ Done! Try starting your application again."
echo ""
echo "If you still have issues, run: ./gradlew flywayRepair"

