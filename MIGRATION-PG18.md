# Migrating an existing install from PostgreSQL 15 to 18

PostgreSQL 18 cannot open a PostgreSQL 15 data directory, so the upgrade is a dump of the old database, a fresh PostgreSQL 18 data directory, and a restore. Media files (attachments, thumbnails) live on disk outside the database and are not touched.

The procedure never modifies or deletes the old PostgreSQL 15 directory. Rolling back is a config change, not a restore.

> **Do not run** `docker compose down -v`, `rm -rf` on any Postgres directory, or point the new version at the old data directory. None of the steps below need them.

All commands run from the directory containing `docker-compose.yml` and `.env`. They use the `POSTGRES_USER` / `POSTGRES_DB` variables already set inside the `db` container, so nothing needs to be exported in your shell.

## 0. Before you start

- **Before pulling this version of the repo**, add this line to `.env`. The pg18-era compose file mounts the data directory at `/var/lib/postgresql`; this keeps your PostgreSQL 15 directory at its old mount point:
  ```dotenv
  DB_DATA_CONTAINER_PATH=/var/lib/postgresql/data
  ```
  Without it, the pg15 container finds no cluster at its mount point and initialises a new, empty database (your real data stays untouched in the host directory, but the app comes up empty). Pull, `docker compose up -d`, and confirm your data is there and the app is healthy on PostgreSQL 15.
- Take your normal off-host backup (external HDD, NAS, ...).
- Check free disk space: you need roughly 2x the database size plus the dump file.
  ```bash
  docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select pg_size_pretty(pg_database_size(current_database()))"'
  df -h .
  ```
- Note the current values of `DB_VERSION` and `DB_DATA_HOST_PATH` in `.env`. You need them for rollback.
- Check for corrupt text indexes. If the host OS or the postgres:15 image's glibc changed since the database was created, text indexes may be mis-sorted. Their unique checks then miss existing rows, so duplicate contacts get inserted, and the restore into PostgreSQL 18 fails with `could not create unique index "ux_contacts_user_normalized"`. This check only reads; the `amcheck` extension is created inside a transaction that is rolled back:
  ```bash
  cat <<'SQL' | docker compose exec -T db sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' 2>&1 | grep BROKEN
  BEGIN;
  CREATE EXTENSION IF NOT EXISTS amcheck;
  DO $$
  DECLARE r record;
  BEGIN
    FOR r IN SELECT c.oid, c.relname FROM pg_index i
      JOIN pg_class c ON c.oid = i.indexrelid JOIN pg_am a ON a.oid = c.relam
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE a.amname = 'btree' AND n.nspname = 'public'
    LOOP
      BEGIN PERFORM bt_index_check(r.oid, true);
      EXCEPTION WHEN others THEN RAISE NOTICE 'BROKEN %: %', r.relname, SQLERRM;
      END;
    END LOOP;
  END $$;
  ROLLBACK;
  SQL
  ```
  No output means all indexes are fine. If anything is reported, stop the app and run [`scripts/fix-duplicate-contacts.sql`](scripts/fix-duplicate-contacts.sql). It merges duplicate contacts and rebuilds the indexes on `contacts` and `conversations`. Run it with `-v apply=0` for a dry run first, then `-v apply=1`. Re-run the check until it prints nothing.

## 1. Stop writers and record row counts

Stop the app so nothing is imported or written mid-dump:
```bash
docker compose stop app frontend
```

Save row counts from PostgreSQL 15:
```bash
cat > pg-counts.sql <<'SQL'
select 'users', count(*) from users
union all select 'contacts', count(*) from contacts
union all select 'conversations', count(*) from conversations
union all select 'conversation_contacts', count(*) from conversation_contacts
union all select 'messages', count(*) from messages
union all select 'message_parts', count(*) from message_parts
union all select 'message_embeddings', count(*) from message_embeddings
union all select 'embedding_jobs', count(*) from embedding_jobs
union all select 'flyway_schema_history', count(*) from flyway_schema_history
order by 1;
SQL
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA' < pg-counts.sql | tee counts-pg15.txt
```

## 2. Dump the database

```bash
DUMP=sms-archive-pg15-$(date +%F).dump
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$DUMP"
ls -lh "$DUMP"
# Integrity check: must list the tables and finish without errors
docker compose exec -T db pg_restore -l < "$DUMP" | grep -c "TABLE DATA"
```

Copy `$DUMP` off the server (external HDD, laptop) before continuing.

## 3. Switch to PostgreSQL 18 in a new, empty directory

```bash
docker compose stop db
```

Create a **new** directory next to the old one (example path; use your own base):
```bash
sudo mkdir -p /opt/stacks/sms-archive/postgres18
```

Edit `.env`: change these three values:
```dotenv
DB_VERSION=pg18
DB_DATA_HOST_PATH=/opt/stacks/sms-archive/postgres18
DB_DATA_CONTAINER_PATH=/var/lib/postgresql
```
(With the repo's compose files you can delete the `DB_DATA_CONTAINER_PATH` line instead, since `/var/lib/postgresql` is the default. Setting it explicitly also works with compose files that require the variable.)

Start only the database. PostgreSQL 18 initialises an empty cluster with the same database name, user, and password:
```bash
docker compose up -d db
docker compose ps db          # wait until (healthy)
docker compose exec -T db psql --version   # PostgreSQL 18.x
```

## 4. Restore and verify

```bash
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error' < "$DUMP"
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "ANALYZE"'

docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA' < pg-counts.sql | tee counts-pg18.txt
diff counts-pg15.txt counts-pg18.txt && echo "COUNTS MATCH"
```

If `pg_restore` reports an error or the counts differ, stop here and roll back (below).

## 5. Start the app

```bash
docker compose up -d
docker compose logs app | grep -i -E "flyway|started"
```

Flyway should report the schema is up to date, or apply only migrations that are newer than the app version you ran on PostgreSQL 15 (for example V18 if you upgraded the app and the database together). Log in and spot-check conversations, search, and media.

## Rollback

The PostgreSQL 15 directory is untouched, so rolling back is:

1. `docker compose stop`
2. In `.env`, restore all three PostgreSQL 15 values:
   ```dotenv
   DB_VERSION=pg15
   DB_DATA_HOST_PATH=<your old path>
   DB_DATA_CONTAINER_PATH=/var/lib/postgresql/data
   ```
3. `docker compose up -d`

Any messages imported while on PostgreSQL 18 are not in the PostgreSQL 15 database.

## Cleanup (weeks later)

Once you are confident in PostgreSQL 18, delete the old PostgreSQL 15 directory and the `.dump` file by hand. Double-check the path before deleting anything.
