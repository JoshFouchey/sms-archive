# SMS Archive (WIP)

Ingests backup XML files for SMS, MMS, and RCS messages, normalizes them, and stores structured data (and media) in PostgreSQL for querying and future analysis.

## Status
Experimental - schema, importer, and API surface likely to change.

## Features (current)
- Parse XML backups (SMS / MMS / RCS) (details TBD).
- Persist messages (PostgreSQL 15) via Spring Data JPA / Hibernate.
- Flyway migrations for schema versioning.
- JSONB support (via `hibernate-types` and `hypersistence-utils`) for flexible payload fields.
- Attachment / media handling (thumbnail generation via Thumbnailator).
- Optional SQLite usage (test/import staging) present via `sqlite-jdbc`.
- Java 25 toolchain build (Gradle) with Spring Boot 3.5.6.
- Node 22.20.0 & Vue frontend scaffolding (assumed).

## Roadmap (abridged)
- Full field mapping documentation.
- REST search & filtering endpoints.
- Media storage abstraction (filesystem vs object storage).
- Authentication / authorization layer.
- Docker / compose deployment.
- Frontend message timeline & attachment viewer.

## Tech Stack
- Java 25 (Gradle toolchain enforced).
- Spring Boot 3.5.6 (Web, Data JPA).
- PostgreSQL 16 (primary DB) + Flyway (Testcontainers for integration tests).
- Testcontainers PostgreSQL for integration tests (real jsonb & indexes).
- Node 22.20.0 (frontend: Vue / TypeScript).
- JAXB for XML parsing.
- Lombok 1.18.42 (pending long-term JDK 25 stability).
- Commons IO, Thumbnailator utilities.

## Project Structure (conceptual)
1. Import layer: XML ingestion → parser → normalized DTOs.
2. Persistence layer: Entities (Message, Thread, Contact, Attachment, Metadata).
3. API layer: (planned) REST controllers (messages, threads, attachments).
4. Frontend: Vue app consuming REST API (TBD).

## Build & Run (backend)
1. Ensure Java 25 installed (or allow Gradle toolchain provisioning).
2. Start PostgreSQL 15.
3. Create database:

## Testcontainers Reuse (Faster Integration Tests)
To speed up local and CI runs, enable container reuse so the PostgreSQL Testcontainer is not re-created for each test JVM.

Add to your `~/.testcontainers.properties` (DO NOT COMMIT THIS FILE):
```
testcontainers.reuse.enable=true
```
The singleton container in `EnhancedPostgresTestContainer` already calls `.withReuse(true)`. Without the global flag, Testcontainers will still create a fresh container.

### CI Setup
In CI, create the properties file in the home directory of the build user or export:
```
mkdir -p ~/.testcontainers && echo 'testcontainers.reuse.enable=true' >> ~/.testcontainers.properties
```
(Exact location may vary depending on runner image.)

## Schema Verification Tests
`SchemaMigrationTest` asserts the presence of critical indexes, constraints, and triggers after Flyway runs:
- Indexes: `ux_messages_dedupe`, `ix_messages_dedupe_prefix`, `idx_messages_body_fts`
- Constraints: `chk_messages_protocol`, `chk_messages_direction`
- Trigger: `trg_messages_updated_at`
This guards against accidental migration regressions.

## Removed Legacy Test Properties
The legacy `application-test.properties` file is deprecated and now empty. All test profile configuration lives in `application-test.yml` plus dynamic Testcontainer overrides in `EnhancedPostgresTestContainer`. You can safely delete `src/test/resources/application-test.properties`:
```
rm src/test/resources/application-test.properties
```
(No replacement needed; Spring Boot will load `application-test.yml` when `@ActiveProfiles("test")` is used.)

## Controller Test Modernization
`MediaControllerTest` no longer uses deprecated `@MockBean`; a lightweight stub bean is supplied via an inner `@TestConfiguration` to avoid Mockito deprecation warnings.
