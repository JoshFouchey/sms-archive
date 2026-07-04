# Testing Notes

State of the backend test suite, what was repaired in July 2026, and what's still known-broken.

## Running tests

```bash
./gradlew test                      # full suite (needs Docker for Testcontainers)
./gradlew test --tests "*Import*"   # filtered
```

Integration tests use a **reused** PostgreSQL Testcontainer (`pgvector/pgvector:pg15`).
For faster runs, add to `~/.testcontainers.properties`:

```
testcontainers.reuse.enable=true
```

Note: Testcontainers occasionally rewrites that file and drops this line — re-add it if
container startup gets slow again.

## July 2026 repairs

The integration suite had been **completely unrunnable since roughly October 2025** — every
`@SpringBootTest` failed during context startup, so no integration test had actually
executed in months. Root causes, all fixed:

1. **Docker Desktop dropped engine API < 1.44**, while the docker-java bundled with
   Testcontainers 1.21.x defaults to API 1.32. Every container start failed with
   `Could not find a valid Docker environment` (400 on `/info`). Fixed via
   `systemProperty 'api.version', '1.44'` in the `test` task (build.gradle).
2. **V6 migration failed on any fresh database**: V4 created
   `unique_message_per_conversation` as an *index*, but V6 only dropped the *constraint*
   before recreating it. Worked only on databases that predated V6. Fixed by adding
   `DROP INDEX IF EXISTS` to V6. **This changes V6's checksum** — existing databases need a
   one-time repair (see below).
3. **Test container image lacked pgvector**, so V9 (`CREATE EXTENSION vector`) failed.
   Switched `EnhancedPostgresTestContainer` from `postgres:15-alpine` to
   `pgvector/pgvector:pg15` (matches docker-compose).
4. **Entity caches survived test DB wipes.** The Spring `currentUser` cache and the import
   pipeline's contact cache hold JPA entities; test `deleteAll()` cleanup made them stale,
   causing `TransientPropertyValueException` / phantom 404s / empty query results in later
   tests. Fixed by `spring.cache.type: none` in `src/test/resources/application-test.yml`,
   `ImportService.clearImportCaches()` in test cleanup, and a self-healing stale-cache path
   in `ContactResolver` (a real production fix: contact merge + import within 10 minutes
   could crash the import before).
5. **Auto-embedding after imports interfered across tests** — background embedding threads
   from one test outlived it and collided with the next test's cleanup (FK rows on users,
   concurrent writes). Disabled via `smsarchive.ai.embedding.auto-embed: false` in the test
   profile. Test cleanups also delete `embedding_jobs` before `users`
   (that FK has no `ON DELETE CASCADE`).
6. **Stale assertions** from the April 2026 E.164 normalization change
   (`15551234567` → `+15551234567`) in several tests.

## Flyway checksum repair (one-time, existing databases)

Because V6 was edited, Flyway will refuse to start against a database that already ran the
old V6:

```
Migration checksum mismatch for migration version 6
-> Applied to database : <old>
-> Resolved locally    : <new>
```

Take the `Resolved locally` value from the error and run:

```bash
docker exec <postgres-container> psql -U <db_user> -d <db_name> \
  -c "UPDATE flyway_schema_history SET checksum = <new> WHERE version = '6';"
```

Then restart the app. (Same procedure as FIX_FLYWAY_CHECKSUM.md, which covered V4.)

## Known remaining issues (pre-existing, not yet fixed)

Verified present **before** the July 2026 import fixes via baseline comparison (stash +
rerun), so these are inherited test-suite rot, not regressions:

- **Order-dependent failures**: `ImportServiceTest.smsMessagesAreAssignedToConversations`,
  `smsAndMmsFromSameContactShouldShareConversation`, and
  `SmsMmsSameConversationIntegrationTest` pass in isolation but can fail depending on which
  other classes run in the same JVM. Cross-class contamination through the shared reused
  container + multiple cached Spring contexts.
- **~14 failures across other classes at baseline**: `ConversationServiceTest` (4),
  `MediaServiceTest` (3), `AnalyticsServiceRangeTests` (2), and one each in
  `ConversationControllerTest`, `ContactControllerTest`, `DistinctContactsControllerTest`,
  `AnalyticsServiceReversedDatesTest`, `AuthIntegrationTest`. Mix of stale expectations and
  the same isolation problems. Needs a dedicated cleanup pass.

## Test-design gotcha

Do **not** mark tests that call `importService.startImportAsync(...)` as `@Transactional`.
The import runs on a worker thread with its own DB session, which cannot see rows created
in the test's uncommitted transaction — the import fails with "transient User" errors that
only reproduce in some run orders. Use committed setup data (the `cleanDb()` pattern)
instead.
