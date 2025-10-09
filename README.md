# SMS Archive (WIP)

Ingests backup XML files for SMS, MMS, and RCS messages, normalizes them, and stores structured data (and media) in PostgreSQL for querying and future analysis.

## Status
Experimental \- schema, importer, and API surface likely to change.

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
- PostgreSQL 15 (primary DB) + Flyway.
- H2 for tests.
- Node 22.20.0 (frontend: Vue / TypeScript).
- JAXB for XML parsing.
- Lombok 1.18.42 (pending long\-term JDK 25 stability).
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
