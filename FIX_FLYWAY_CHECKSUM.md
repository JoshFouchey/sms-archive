# 🔧 Fix Flyway V4 Migration Checksum Mismatch

## The Problem

```
Migration checksum mismatch for migration version 4
-> Applied to database : -1666908351
-> Resolved locally    : 1372284742
```

**What happened:**
- V4 migration was applied to your database with one version of the file
- We fixed the file multiple times (table names, syntax errors, MD5 hash)
- Now Flyway detects the file changed and refuses to start

## The Solution

You have 3 options:

---

### Option 1: Quick Fix - Update Checksum (Fastest) ✅

**Run this SQL command:**

```bash
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "UPDATE flyway_schema_history SET checksum = 1372284742 WHERE version = '4';"
```

**What it does:** Updates Flyway's tracking table to accept the new file version.

**When to use:** Local development, when you know the migration is already applied correctly.

---

### Option 2: Flyway Repair Command

**Run:**

```bash
cd /Users/jfouchey/development/git/sms-archive
./gradlew flywayRepair
```

**What it does:** Recalculates checksums for all applied migrations.

**When to use:** When multiple migrations have checksum issues.

---

### Option 3: Nuclear Option - Reset V4 Migration

**If the migration is partially applied or broken, completely reset it:**

```bash
# 1. Delete V4 from history
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "DELETE FROM flyway_schema_history WHERE version = '4';"

# 2. Drop the unique index that V4 creates
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "DROP INDEX IF EXISTS unique_message_per_conversation;"

# 3. Restart your app - Flyway will re-apply V4
./gradlew bootRun
```

**When to use:** When the migration is broken or partially applied.

---

## Recommended Steps

**1. Try Option 1 first (quickest):**

```bash
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "UPDATE flyway_schema_history SET checksum = 1372284742 WHERE version = '4';"
```

**2. Restart your application:**

```bash
./gradlew bootRun
```

**3. If it still fails, check what's in the database:**

```bash
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
SELECT version, description, checksum, success, installed_on 
FROM flyway_schema_history 
ORDER BY installed_rank;"
```

**4. If V4 shows `success = false`, use Option 3 (Nuclear).**

---

## Why This Happened

We modified the V4 migration file multiple times to fix:
1. ❌ Wrong table name (`contact` → `contacts`)
2. ❌ Wrong table name (`message` → `messages`)  
3. ❌ PostgreSQL syntax error (IF NOT EXISTS on ALTER TABLE)
4. ❌ Index size limit (body too large → MD5 hash)

Each time we fixed it, Flyway calculated a new checksum. Since you already ran the migration once, Flyway detected the mismatch.

---

## Prevention for Production

In production, **NEVER modify an applied migration**. Instead:

1. Create a new migration file (V5, V6, etc.)
2. Or use `flyway.validateOnMigrate=false` in application.properties (not recommended)

For local development, it's fine to:
- Modify migrations that aren't in production yet
- Use `flywayRepair` to sync checksums
- Delete migrations and re-apply them

---

## Quick Verification

After fixing, verify the migration is recognized:

```bash
# Check if V4 is marked successful
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "
SELECT version, success, checksum 
FROM flyway_schema_history 
WHERE version = '4';"
```

Expected output:
```
 version | success |  checksum
---------+---------+------------
 4       | t       | 1372284742
```

---

## TL;DR - Just Fix It Now

**Copy/paste this:**

```bash
# Update the checksum
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive -c "UPDATE flyway_schema_history SET checksum = 1372284742 WHERE version = '4';"

# Restart app
cd /Users/jfouchey/development/git/sms-archive && ./gradlew bootRun
```

✅ **Done!** Your app should start now.

---

## Still Failing?

If you're still seeing errors after Option 1, run **Option 3 (Nuclear)**:

```bash
# Remove V4 completely
docker exec sms-archive-db-1 psql -U sms_user -d sms_archive << 'EOF'
-- Delete from Flyway history
DELETE FROM flyway_schema_history WHERE version = '4';

-- Drop the unique index (if it exists)
DROP INDEX IF EXISTS unique_message_per_conversation;

-- Show remaining migrations
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
EOF

# Restart - V4 will be re-applied fresh
./gradlew bootRun
```

This will let Flyway apply the correct, fixed version of V4 from scratch.

---

**Need Help?** 
- Check logs: `docker logs sms-archive-db-1 | tail -50`
- Verify schema: `\d messages` in psql
- Check Flyway: `SELECT * FROM flyway_schema_history;`

