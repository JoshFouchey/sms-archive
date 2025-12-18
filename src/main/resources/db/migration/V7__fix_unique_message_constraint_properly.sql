-- Fix the unique constraint to properly handle duplicate empty body messages
-- V6 may have failed on server if constraint already existed, so we need to handle both cases

-- Drop the index if it exists (may be standalone or part of constraint)
DROP INDEX IF EXISTS unique_message_per_conversation;

-- Drop the constraint if it exists (in case it was created as a constraint not just an index)  
ALTER TABLE messages DROP CONSTRAINT IF EXISTS unique_message_per_conversation;

-- Create the proper unique index using md5 hash of body to handle NULL/empty values
-- This ensures messages with empty bodies can still be deduplicated
CREATE UNIQUE INDEX unique_message_per_conversation 
ON messages (conversation_id, md5(COALESCE(body, '')), timestamp, direction, user_id);
