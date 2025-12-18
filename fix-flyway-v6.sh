#!/bin/bash
# Script to fix Flyway V6 migration issue on the server

echo "This script will:"
echo "1. Mark V6 as successful in Flyway history (even though it failed)"
echo "2. Drop the existing unique_message_per_conversation index"
echo "3. Restart the app so V7 can run and recreate the index properly"
echo ""
read -p "Press Enter to continue or Ctrl+C to cancel..."

# Mark V6 as successful so Flyway moves on to V7
echo "Step 1: Marking V6 as successful in Flyway history..."
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
UPDATE flyway_schema_history 
SET success = true 
WHERE version = '6' AND success = false;
"

# Drop the existing index so V7 can recreate it properly
echo "Step 2: Dropping existing unique_message_per_conversation index..."
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
DROP INDEX IF EXISTS unique_message_per_conversation CASCADE;
ALTER TABLE messages DROP CONSTRAINT IF EXISTS unique_message_per_conversation CASCADE;
"

# Verify what exists now
echo "Step 3: Verifying Flyway history..."
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
SELECT version, description, success, installed_on 
FROM flyway_schema_history 
WHERE version IN ('6', '7')
ORDER BY installed_rank;
"

echo ""
echo "Step 4: Restarting app container to apply V7..."
docker restart sms-archive-app-1

echo ""
echo "Waiting for app to start (30 seconds)..."
sleep 30

echo ""
echo "Step 5: Checking if V7 was applied..."
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
SELECT version, description, success 
FROM flyway_schema_history 
WHERE version IN ('6', '7')
ORDER BY installed_rank;
"

echo ""
echo "Step 6: Verifying the index was created properly..."
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
SELECT pg_get_indexdef(indexrelid) 
FROM pg_index 
JOIN pg_class ON pg_class.oid = pg_index.indexrelid 
WHERE pg_class.relname = 'unique_message_per_conversation';
"

echo ""
echo "Done! Check the output above to verify V7 was applied successfully."
