-- Drop the existing unique constraint that doesn't properly handle NULL/empty body messages
ALTER TABLE messages DROP CONSTRAINT IF EXISTS unique_message_per_conversation;

-- Create a new unique constraint using md5 hash of body to properly handle NULL/empty values
-- This ensures messages with empty bodies can still be deduplicated based on the same key fields
CREATE UNIQUE INDEX unique_message_per_conversation 
ON messages (conversation_id, md5(COALESCE(body, '')), timestamp, direction, user_id);
