# SMS Archive (WIP)

Ingests backup XML files for SMS, MMS, and RCS messages, normalizes them, and stores structured data (and media) in PostgreSQL for querying and future analysis.

## Status
Experimental - schema, importer, and API surface likely to change.

## Features (current)
- Parse XML backups (SMS / MMS / RCS) (details TBD).
- Persist messages (PostgreSQL 18 + pgvector) via Spring Data JPA / Hibernate.
- Flyway migrations for schema versioning.
- JSONB support (via `hibernate-types` and `hypersistence-utils`) for flexible payload fields.
- Attachment / media handling (thumbnail generation via Thumbnailator).
- Optional SQLite usage (test/import staging) present via `sqlite-jdbc`.
- Java 25 toolchain build (Gradle) with Spring Boot 4.1.2.
- Node 26 LTS & Vue frontend scaffolding (assumed).
- **Import Directory Watcher** - Drop large XML files directly on the server to bypass upload limits. See [IMPORT_DIRECTORY.md](IMPORT_DIRECTORY.md) for details.

## Roadmap (abridged)
- Full field mapping documentation.
- REST search & filtering endpoints.
- Media storage abstraction (filesystem vs object storage).
- Authentication / authorization layer.
- Docker / compose deployment.
- Frontend message timeline & attachment viewer.

## Tech Stack
- Java 25 (Gradle toolchain enforced).
- Spring Boot 4.1.2 (Web MVC, Data JPA / Hibernate 7, Security 7, Jackson 3).
- Spring AI 2.0 (OpenAI-compatible client for llama.cpp chat + embeddings; base URLs must end in `/v1`).
- PostgreSQL 18 with pgvector (`pgvector/pgvector:pg18`) + Flyway (Testcontainers for integration tests).  
  Upgrading an existing PostgreSQL 15 install: see [MIGRATION-PG18.md](MIGRATION-PG18.md).
- Testcontainers PostgreSQL for integration tests (real jsonb & indexes).
- Node 26 LTS (frontend: Vue 3.5, TypeScript 6, Vite 8, Pinia 4, Vue Router 5, PrimeVue 5).
  - PrimeVue 5, `@primeuix/themes` 3 and `primeicons` 8 use the commercial PrimeUI License. Without a key every page shows an "Invalid PrimeUI License" badge (nothing else is disabled). See [PrimeUI license key](#primeui-license-key).
  - TypeScript 7 is not yet usable: `vue-tsc` requires the TypeScript JS API that 7.x no longer ships.
- JAXB for XML parsing.
- Lombok (version managed by the Spring Boot BOM).
- Commons IO, Thumbnailator utilities.

## Quickstart (Docker Compose)
Run both database (PostgreSQL 18) and the application container:

```bash
# Build & start (first run builds the backend image)
docker compose up --build -d

# View logs
docker compose logs -f app

# Stop
docker compose down
```
The compose file uses `pgvector/pgvector:${DB_VERSION}` (`pg18`). Existing PostgreSQL 15 data directories can't be opened by 18 directly; follow [MIGRATION-PG18.md](MIGRATION-PG18.md).

To run backend locally without containers:
```bash
# Start local PostgreSQL 18 with pgvector however you prefer (brew, container, etc.)
# Example with Docker:
docker run --rm -p 5432:5432 -e POSTGRES_DB=sms_archive -e POSTGRES_USER=sms_user -e POSTGRES_PASSWORD=sms_pass pgvector/pgvector:pg18

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

## Test Profile Configuration
All test profile configuration lives in `src/test/resources/application-test.yml` (cache disabled, auto-embedding off, inline imports) plus dynamic Testcontainer overrides in `EnhancedPostgresTestContainer`. Keep it there only: a second `application-test.yml` under `src/main/resources` is shadowed on the test classpath and would ship in the production jar.

## Controller Test Modernization
`MediaControllerTest` no longer uses deprecated `@MockBean`; a lightweight stub bean is supplied via an inner `@TestConfiguration` to avoid Mockito deprecation warnings.

## Deployment (Docker / Home Server)
This project ships with a `docker-compose.yml` and a `.env.example` you can copy to `.env` for production-ish or homelab use.

### 1. Prepare Host Paths
Choose a fast disk (SSD) for Postgres if possible, and a larger HDD path for media if you prefer:
```bash
sudo mkdir -p /srv/sms-archive/postgres18
sudo mkdir -p /srv/sms-archive/media/messages
# Postgres UID/GID in official image is 999:999
sudo chown 999:999 /srv/sms-archive/postgres18
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
- `DB_VERSION` (pgvector image tag, `pg18`; changing the major version needs a dump/restore, see MIGRATION-PG18.md)
- `DB_DATA_HOST_PATH` (persistent DB storage)
- `MEDIA_ROOT_HOST_PATH` (media + thumbnails)
- `APP_BASE_URL` (for links / future absolute URL generation)
- `LOG_LEVEL` (default INFO). Message bodies, search queries and generated SQL are only logged at DEBUG; to troubleshoot search or Q&A without enabling DEBUG everywhere, set `LOGGING_LEVEL_COM_JOSHFOUCHEY_SMSARCHIVE_SERVICE=DEBUG` on the app container.

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

#### Prebuilt images (GitHub Actions)
`.github/workflows/docker-images.yml` builds and pushes both images to GHCR on every push to `master` (docs/SQL-only changes are skipped) and on manual dispatch. A `test` job runs the backend suite (`./gradlew test`, Testcontainers on the runner's Docker) first; if it fails, no images are pushed and the test reports are attached to the run as an artifact:
- `ghcr.io/joshfouchey/sms-archive` (backend)
- `ghcr.io/joshfouchey/sms-archive-frontend` (frontend, built with empty `VITE_API_BASE` so it uses relative URLs via the nginx proxy)

Each is tagged `latest` and `sha-<short>`. To deploy without building locally:
```bash
docker compose pull app frontend
docker compose up -d
```
Pin a specific build with `IMAGE_TAG=sha-abc1234` in `.env`.

#### PrimeUI license key
The key is read at build time from `VITE_PRIMEUI_LICENSE` and is checked offline in the browser. It ends up in the frontend JavaScript bundle (that is how PrimeUI keys work), so keep it out of git and keep the frontend image private.
- **CI:** add a repository secret `PRIMEUI_LICENSE` (Settings → Secrets and variables → Actions). The workflow passes it as a BuildKit secret, so it is not stored in image metadata, and passes a fingerprint of it so adding or rotating the key triggers a rebuild.
- **Local:** put `VITE_PRIMEUI_LICENSE=<key>` in `frontend/.env.local` (gitignored). `npm run dev`, `npm run build` and `docker compose build frontend` all pick it up.

### 7. Manual Backups (Recommended)
Until an automated job is added, you can run:
```bash
docker compose exec -T db pg_dump -U "$DB_USER" "$DB_NAME" | gzip > sms-archive_$(date +%F).sql.gz
```
Store dumps somewhere off-host or in a different disk.

### 8. Media Directory Layout
Files are stored under `${MEDIA_ROOT_HOST_PATH}/<conversation id>/`. Thumbnails (`*_thumb.jpg`) are generated alongside originals. Media for messages that could not be assigned a conversation stays in `_noconversation/`.

Older versions leaked a copy of every MMS attachment into `_noconversation/` each time an already-imported message was re-imported. To reclaim that space (run from the compose directory, after a backup):

```bash
scripts/cleanup-noconversation.sh           # dry run: lists unreferenced files and their total size
scripts/cleanup-noconversation.sh --apply   # moves them to _quarantine-<timestamp>/ (nothing is deleted)
```

Files still referenced by a message are never touched, nor are files modified in the last hour. After checking the gallery, delete the quarantine folder; the script prints the undo command too.

### 9. Zero-Downtime Rebuild (Optional)
For small homelab setups a quick restart is fine. If you later add a separate migration job, run migrations before rolling out new code.

### 10. Security Notes
- `.env` is ignored by Git; keep secrets (future JWT, etc.) there.
- Expose only port 8080 internally and use the reverse proxy for public access.
- Consider adding a firewall rule or restricting the Postgres port to the Docker network only (omit the `ports:` entry if the DB need not be accessed from host directly).

## Full Docker Deployment (Backend + Frontend)
This repo now includes a dedicated frontend image (Vue static build + Nginx) alongside the backend Spring Boot service.

### Build & Run (All Services)
```bash
# Ensure external proxy network exists if you use a global reverse proxy
docker network create proxy || true

# Build and start everything
docker compose up --build -d

# View logs
docker compose logs -f frontend
docker compose logs -f app
```
Frontend listens on port 80 (mapped to host 80). Backend remains available directly on 8080 for debugging (you may remove the port mapping if you want it isolated).

### Nginx Frontend Proxy Behavior
`nginx.conf` proxies dynamic paths to the backend:
- `/api` → `app:8080/api`
- `/search` → `app:8080/search`
- `/import` → `app:8080/import`
- `/media` → `app:8080/media` (image & thumbnail files)
All other paths fall back to `index.html` for SPA routing.

### Custom Domain / Reverse Proxy Upstream
If you route a public reverse proxy (e.g. Traefik, Nginx Proxy Manager) to this stack, point it at the **frontend** container (port 80). The backend service name `app` stays internal. Ensure forwarded headers are preserved; Spring Boot will already honor them.

### Adjusting API Base For Frontend
The frontend code expects absolute/relative paths that already include `/api` where needed. Leave `VITE_API_BASE` blank (default). Only set a value if you introduce an additional path segment (e.g. `/sms`) at the outer reverse proxy and want the frontend to call `/sms/api/...`.

To customize during build:
```bash
docker compose build --build-arg VITE_API_BASE="/sms" frontend
```
Then update outer proxy to rewrite `/sms` appropriately.

### Production Hardening Checklist
- Remove backend port mapping (`8080:8080`) if not needed externally.
- Add TLS termination at outer proxy; ensure HSTS if appropriate.
- Configure automated Postgres backups (cron + `pg_dump` into mounted volume).
- Consider setting JVM memory limits via `JAVA_OPTS=-Xms512m -Xmx512m`.
- Monitor container health: both containers expose basic health checks.

### Updating Only Frontend
```bash
git pull
# Rebuild only frontend
docker compose build frontend
docker compose up -d frontend
```

### Updating Backend & Running Migrations
```bash
git pull
docker compose build app
docker compose up -d app
```
Flyway migrations run automatically on backend startup.

### Media Paths
Images are requested by the frontend using relative paths (`/media/messages/...`). Nginx proxies these to the backend which serves the file system under the mounted volume.
