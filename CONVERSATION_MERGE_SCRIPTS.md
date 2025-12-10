# Conversation Merge Scripts

## Problem

You may have duplicate conversations with the same person/participants due to:
- Bad data from imports
- Multiple phone numbers for the same person
- Contact merges that didn't consolidate conversations

## Solution: One-Time SQL Cleanup Scripts

Since this is a **data cleanup issue** (not an ongoing problem), a SQL script is more appropriate than building a UI feature.

## Scripts Provided

### 1. `preview-duplicate-conversations.sql` (Safe - Read Only)

**Preview what duplicates exist without making changes.**

```bash
# Run preview on your server
docker exec -i sms-archive-db-1 psql -U sms_user -d sms_archive < preview-duplicate-conversations.sql

# Or copy to server and run
scp preview-duplicate-conversations.sql server:/tmp/
ssh server
docker exec -i sms-archive-db-1 psql -U sms_user -d sms_archive < /tmp/preview-duplicate-conversations.sql
```

**Output shows:**
- Which conversations are duplicates
- How many messages are in each
- Which conversation will be kept (oldest ID)
- Participant names

### 2. `merge-duplicate-conversations.sql` (Makes Changes)

**Actually merges duplicate conversations.**

```bash
# IMPORTANT: Backup first!
docker exec sms-archive-db-1 pg_dump -U sms_user sms_archive > backup-before-merge.sql

# Run the merge
docker exec -i sms-archive-db-1 psql -U sms_user -d sms_archive < merge-duplicate-conversations.sql
```

**What it does:**
1. ✅ Finds conversations with identical participant sets
2. ✅ Keeps the oldest conversation (lowest ID)
3. ✅ Moves all messages from duplicates to the primary conversation
4. ✅ Updates `last_message_at` timestamp
5. ✅ Removes duplicate conversation records
6. ✅ Shows summary of what was merged

## How Duplicates Are Detected

Conversations are considered duplicates if they have:
- **Same user_id** (same account)
- **Same participants** (exact match of contact IDs)

Example:
```
Conversation 5: participants [contact 12, contact 15]
Conversation 8: participants [contact 12, contact 15]
→ These are duplicates! Conv 5 will be kept, Conv 8 merged into it
```

## Safety Features

- **Uses a transaction** - either all changes succeed or all are rolled back
- **Keeps oldest conversation** - preserves history
- **Only affects duplicates** - unique conversations are untouched
- **Shows progress** - prints what it's doing at each step

## Example Output

```
┌──────────────────────────────────────────────────────────┐
│ Found 3 groups of duplicate conversations affecting      │
│ 5 conversations to be merged                            │
└──────────────────────────────────────────────────────────┘

┌──────────────┬────────────────┬──────────────┬────────────────┐
│ Keep (Prim)  │ Duplicate IDs  │ Total Dupes  │ Messages       │
├──────────────┼────────────────┼──────────────┼────────────────┤
│ 5            │ {5,8,12}       │ 3            │ 1,234          │
│ 15           │ {15,22}        │ 2            │ 567            │
└──────────────┴────────────────┴──────────────┴────────────────┘

Moved 789 messages to primary conversations
Cleanup complete! Merged 3 groups of duplicates
```

## When to Use This

Use these scripts when:
- ✅ You have duplicate conversations showing up in your UI
- ✅ After merging contacts, conversations are still separate
- ✅ You imported data multiple times and have duplicates
- ✅ Bad data from SMS Backup & Restore XML imports

## Troubleshooting

### "ERROR: permission denied"
Make sure you're using the postgres superuser or have appropriate permissions.

### "No duplicates found"
Good news! You don't have duplicate conversations. The merge may have already fixed them.

### "Want to undo the merge"
If you made a backup, restore it:
```bash
docker exec -i sms-archive-db-1 psql -U sms_user -d sms_archive < backup-before-merge.sql
```

## Why Not a UI Feature?

Building a UI for conversation merging would require:
- Identifying duplicate conversations (complex logic)
- UI for selecting which to merge
- Preview of what will happen
- Undo functionality
- Testing and maintenance

**This is overkill for a one-time data cleanup task.** A SQL script is:
- ✅ Faster to create
- ✅ Easier to verify/test
- ✅ More powerful (handles batch operations)
- ✅ Can be run as needed
- ✅ No ongoing maintenance

## Future Prevention

The contact merge feature (already implemented) will prevent new duplicates by:
- Consolidating contacts before conversations are created
- Marking archived contacts so they're not used
- Deduplicating messages during merge

## Questions?

- **Q: Will this delete messages?**  
  A: No! Messages are moved, not deleted. All data is preserved.

- **Q: What if I have group chats with similar participants?**  
  A: Only conversations with IDENTICAL participant sets are merged. If even one participant differs, they're kept separate.

- **Q: Can I preview on production without risk?**  
  A: Yes! Use `preview-duplicate-conversations.sql` - it's read-only.

- **Q: How long does it take?**  
  A: Depends on data size. Usually < 1 minute for normal databases.

## Summary

1. **Preview first:** Run `preview-duplicate-conversations.sql` to see what exists
2. **Backup:** Always backup before making changes
3. **Merge:** Run `merge-duplicate-conversations.sql` to fix duplicates
4. **Done!** This is a one-time cleanup, not needed regularly

---

**Created:** 2025-12-10  
**Purpose:** One-time cleanup of duplicate conversation data

