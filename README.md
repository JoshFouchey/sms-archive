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
- PostgreSQL 15 (primary DB) + Flyway (Testcontainers for integration tests).  
  (Pinned to 15 due to prior Flyway compatibility issue; bump to 16+ once validated.)
- Testcontainers PostgreSQL for integration tests (real jsonb & indexes).
- Node 22.20.0 (frontend: Vue / TypeScript).
- JAXB for XML parsing.
- Lombok 1.18.42 (pending long-term JDK 25 stability).
- Commons IO, Thumbnailator utilities.

## Quickstart (Docker Compose)
Run both database (PostgreSQL 15) and the application container:

```bash
# Build & start (first run builds the backend image)
docker compose up --build -d

# View logs
docker compose logs -f app

# Stop
docker compose down
```
The compose file pins `postgres:15-alpine` to avoid Flyway compatibility issues observed with 16.x. Adjust to 16+ only after validating against Flyway migrations locally.

To run backend locally without containers:
```bash
# Start local PostgreSQL 15 however you prefer (brew, container, etc.)
# Example with Docker:
docker run --rm -p 5432:5432 -e POSTGRES_DB=sms_archive -e POSTGRES_USER=sms_user -e POSTGRES_PASSWORD=sms_pass postgres:15-alpine

# In another shell, run backend
gradle bootRun
```
Ensure `spring.datasource.*` properties or environment variables point to your local instance if you override defaults.

## Project Structure (conceptual)
1. Import layer: XML ingestion → parser → normalized DTOs.
2. Persistence layer: Entities (Message, Thread, Contact, Attachment, Metadata).
3. API layer: (planned) REST controllers (messages, threads, attachments).
4. Frontend: Vue app consuming REST API (TBD).

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

## Deployment (Docker / Home Server)
This project ships with a `docker-compose.yml` and a `.env.example` you can copy to `.env` for production-ish or homelab use.

### 1. Prepare Host Paths
Choose a fast disk (SSD) for Postgres if possible, and a larger HDD path for media if you prefer:
```bash
sudo mkdir -p /srv/sms-archive/postgres
sudo mkdir -p /srv/sms-archive/media/messages
# Postgres UID/GID in official image is 999:999
sudo chown 999:999 /srv/sms-archive/postgres
# App runs as non-root; ensure write access to media
sudo chown $(id -u):$(id -g) /srv/sms-archive/media/messages
```

### 2. External Proxy Network (Optional)
If you use Nginx Proxy Manager or another reverse proxy, create an external network once:
```bash
docker network create proxy || true
```
`docker-compose.yml` attaches the `app` service to this network so a proxy container (on the same network) can route to `sms-archive-app:8080`.

### 3. Configure Environment
Copy and edit environment variables:
```bash
cp .env.example .env
$EDITOR .env
```
Key variables:
- `DB_VERSION` (Postgres image tag, pinned to 15-alpine)
- `DB_DATA_HOST_PATH` (persistent DB storage)
- `MEDIA_ROOT_HOST_PATH` (media + thumbnails)
- `APP_BASE_URL` (for links / future absolute URL generation)
- `LOG_LEVEL` (default INFO)

### 4. Build & Run
```bash
docker compose --env-file .env up --build -d
```
Check services:
```bash
docker compose ps
docker compose logs -f app
```

### 5. Reverse Proxy Headers
`server.forward-headers-strategy=framework` is enabled so `X-Forwarded-*` headers from your proxy are honored (scheme, host). Ensure your proxy forwards them (Nginx Proxy Manager does by default).

### 6. Updating
```bash
git pull
./gradlew clean bootJar
docker compose build app
docker compose up -d app
```
(Or rely on the multi-stage build in the Compose file to recompile automatically.)

### 7. Manual Backups (Recommended)
Until an automated job is added, you can run:
```bash
docker exec -t sms-archive-postgres pg_dump -U $DB_USER $DB_NAME | gzip > sms-archive_$(date +%F).sql.gz
```
Store dumps somewhere off-host or in a different disk.

### 8. Media Directory Layout
Files are stored under `${MEDIA_ROOT_HOST_PATH}` with per-import UUID folders. Thumbnails (`*_thumb.jpg`) are generated alongside originals.

### 9. Zero-Downtime Rebuild (Optional)
For small homelab setups a quick restart is fine. If you later add a separate migration job, run migrations before rolling out new code.

### 10. Security Notes
- `.env` is ignored by Git; keep secrets (future JWT, etc.) there.
- Expose only port 8080 internally and use the reverse proxy for public access.
- Consider adding a firewall rule or restricting the Postgres port to the Docker network only (omit the `ports:` entry if the DB need not be accessed from host directly).
