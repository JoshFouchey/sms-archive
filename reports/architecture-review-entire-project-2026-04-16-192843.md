# Architecture Review Plan
Generated: 2026-04-16T19:28:43Z
Scope: Entire Project
Previous Audit: 2026-03-11 (CODEBASE_AUDIT.md)

## Executive Summary

The SMS Archive codebase has improved **significantly** since the March 2026 audit. Of the 28 enhancement items identified, approximately 18 have been fully addressed and 3 partially addressed. The most impactful improvements include: domain exception hierarchy, input validation, auth security hardening, dependency cleanup, HikariCP configuration, lazy-loaded routes, and bounded caches.

However, several structural issues remain open, and the addition of AI features (Text-to-SQL, embeddings, unified search) has introduced new architectural patterns that warrant review. The Knowledge Graph feature was built and then entirely removed (V10–V18 migrations), leaving some residual code.

**Overall Architecture Grade: B+ (up from B-)**

### Audit Delta Summary

| Category | March Grade | Current Grade | Change |
|---|---|---|---|
| Project Structure | B+ | A- | ⬆ Added event/, exception/, util/ packages |
| Controller/Service Layering | B | B | ➡ SearchController still bypasses service layer |
| JPA Entity Design | B- | B | ⬆ Batched bulk loading, light DTOs |
| Transaction Boundaries | B- | B | ⬆ Partial improvements |
| Exception Handling | C | A- | ⬆⬆ Domain exceptions, sanitized errors |
| Security | C+ | B+ | ⬆⬆ Auth hardening, token type validation |
| Input Validation | D | B | ⬆⬆ InputLimits utility, truncation on all controllers |
| Database/Indexing | A- | A- | ➡ Excellent; HNSW tuning added |
| Query Performance | B | B | ➡ 50K limit still present |
| Connection Pool | C | A | ⬆⬆ Proper HikariCP config |
| Frontend Structure | B | B+ | ⬆ Lazy routes, removed scaffolding |
| API Design | C+ | C+ | ➡ Still inconsistent URL patterns |
| Observability | D | D+ | ➡ Minimal improvement |
| Dependencies | B- | A- | ⬆⬆ Dead deps removed, JJWT upgraded |

## Current Architecture

### Overview
- **Architecture style**: Monolithic layered (Spring Boot backend + Vue 3 SPA frontend)
- **Backend**: Spring Boot 3.5.6, Java 25, PostgreSQL 15 with pgvector, Flyway 11
- **Frontend**: Vue 3.5, TypeScript 5.8, Vite 7, PrimeVue 4, Tailwind CSS 4, Pinia
- **AI**: Spring AI with OpenAI-compatible API (llama.cpp), pgvector HNSW for semantic search
- **Deployment**: Docker multi-stage builds, docker-compose, nginx reverse proxy

### Key Components (22 services, 12 controllers, 7 repositories)
- **Import Pipeline**: Streaming XML parser → contact resolution → conversation grouping → batch persistence → event-driven embedding
- **Search System**: Unified search with heuristic intent classifier → keyword (FTS + trigram) / semantic (pgvector) / hybrid (RRF fusion)
- **AI Features**: Embedding service (contextual chunking), Text-to-SQL analytics, Q&A pipeline
- **Auth**: JWT access/refresh tokens, BCrypt, stateless sessions

### Architecture Diagram
```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (Vue 3 SPA)                                       │
│  ┌──────┐ ┌──────────┐ ┌────────┐ ┌───────┐ ┌──────────┐  │
│  │Ask   │ │Messages  │ │Gallery │ │Contacts│ │Admin     │  │
│  │(461) │ │(1,261)⚠️│ │(400)   │ │(398)   │ │(60+sub)  │  │
│  └──┬───┘ └────┬─────┘ └───┬────┘ └───┬───┘ └────┬─────┘  │
│     └──────────┴───────────┴─────┬────┴──────────┘         │
│                            api.ts (471 LOC)                 │
│                        authStore.ts (Pinia)                 │
├─────────────────────────────────────────────────────────────┤
│  nginx (reverse proxy, static assets, CSP)                  │
├─────────────────────────────────────────────────────────────┤
│  Backend (Spring Boot 3.5.6)                                │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Controllers (12)          Security                     │ │
│  │ ┌──────────┐ ┌────────┐  ┌──────────┐ ┌────────────┐ │ │
│  │ │Search(2) │ │Conv    │  │AuthFilter│ │SecurityCfg│ │ │
│  │ │QA       │ │Contact │  │TokenSvc  │ │InputLimits │ │ │
│  │ │Import   │ │Media   │  └──────────┘ └────────────┘ │ │
│  │ │Auth/User│ │Gallery │                               │ │
│  │ └────┬────┘ └───┬────┘                               │ │
│  ├──────┴──────────┴────────────────────────────────────┤ │
│  │ Services (22)                                         │ │
│  │ ┌────────────┐ ┌──────────────┐ ┌──────────────────┐ │ │
│  │ │ImportSvc   │ │ConversationSvc│ │EmbeddingSvc     │ │ │
│  │ │(1,069)⚠️  │ │(561)⚠️       │ │(560)⚠️          │ │ │
│  │ ├────────────┤ ├──────────────┤ ├──────────────────┤ │ │
│  │ │ContactSvc  │ │UnifiedSearch │ │SemanticSearch   │ │ │
│  │ │MediaSvc    │ │TextToSqlSvc  │ │QaService        │ │ │
│  │ │AnalyticsSvc│ │ConvMaintSvc  │ │ImportDirWatcher │ │ │
│  │ └────────────┘ └──────────────┘ └──────────────────┘ │ │
│  ├──────────────────────────────────────────────────────┤ │
│  │ Repositories (7) + Mappers (3) + DTOs (30)           │ │
│  │ Native SQL: FTS, trigram, pgvector cosine, LATERAL   │ │
│  └──────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│  PostgreSQL 15 + pgvector + pg_trgm                         │
│  18 Flyway migrations (V1–V18)                              │
│  HNSW index, GIN FTS + trigram indexes                      │
└─────────────────────────────────────────────────────────────┘
         │
    ┌────┴─────┐
    │llama.cpp │  OpenAI-compatible API
    │(local LLM)│  Embeddings + Text-to-SQL
    └──────────┘
```

## Issues Fixed Since March Audit ✅

### Security (was C+, now B+)
- [x] ✅ `/api/contacts` removed from `permitAll()` — now requires authentication
- [x] ✅ `AuthTokenFilter` validates token type via `isAccessToken()` — refresh tokens rejected as Bearer auth
- [x] ✅ `RestExceptionHandler` completely rewritten — domain-specific exceptions, no more `IllegalStateException→401`, generic handler returns "An unexpected error occurred" instead of `ex.getMessage()`
- [x] ✅ Auth integration tests added (307+ lines), TokenService tests added (163 lines)
- [ ] ⚠️ **Still open**: `/media/**` and `/import/**` remain `permitAll()` (SecurityConfig:66-67)
- [ ] ⚠️ **Still open**: No rate limiting on auth endpoints

### Dependencies (was B-, now A-)
- [x] ✅ `hibernate-types-60` removed — migrated to `hypersistence-utils-hibernate-63`
- [x] ✅ `sqlite-jdbc` removed
- [x] ✅ `libphonenumber` removed
- [x] ✅ `Guava` removed
- [x] ✅ JJWT upgraded 0.11.5 → 0.12.6 with new API (`subject()`, `issuedAt()`)
- [x] ✅ `commons-io` upgraded 2.15.1 → 2.19.0
- [x] ✅ `Caffeine` upgraded 3.1.8 → 3.2.0
- [x] ✅ `Testcontainers` upgraded → 1.21.0
- [x] ✅ `@types/vue-router` removed from frontend

### Code Quality
- [x] ✅ Domain exception hierarchy: `ResourceNotFoundException`, `ResourceOwnershipException`, `UnauthenticatedException`, `JobAlreadyRunningException`
- [x] ✅ Fully-qualified class names replaced with proper imports (ConversationController, ConversationService)
- [x] ✅ Input length validation via `InputLimits.java` utility on all controllers
- [x] ✅ PII removed from DEBUG-level import logs
- [x] ✅ Unbounded `contactCache` → Caffeine (max 1000, 10min TTL)
- [x] ✅ Unbounded `progressMap` → Caffeine (max 50, 1hr TTL)
- [x] ✅ Contact sorting moved from Java to database `ORDER BY`
- [x] ✅ Infinite retry loop in auth interceptor prevented with `_retrying` flag

### Infrastructure
- [x] ✅ HikariCP properly configured (20 max, 5 min idle, leak detection)
- [x] ✅ Vue routes lazy-loaded
- [x] ✅ `HelloWorld.vue` scaffold removed

## Remaining Architectural Issues

### Critical Issues
- [ ] **`ddl-auto: update` in default profile** (application.yml:19) — Hibernate modifies schema alongside Flyway in production. Should be `validate` or `none`. This was flagged in the original audit and remains unfixed.
- [ ] **N+1 on `conversation.participants`** — `MessageMapper.toDto()` (lines 19-21) still triggers lazy loading of `getConversation().getParticipants()`. The `@EntityGraph` on message queries only includes `{"parts", "senderContact"}`, never `"conversation.participants"`. Every message DTO mapping fires an extra query. The `@BatchSize(25)` on the entity reduces this but doesn't eliminate it.

### Design Flaws
- [ ] **`SearchController` bypasses service layer** — directly injects `MessageRepository` (line 25). The `/search/text` and `/search/dates` endpoints should go through a SearchService.
- [ ] **`SearchController.byDateRange()` returns raw JPA entities** — `List<Message>` serialized directly as JSON (line 69-72). Leaks internal fields, no pagination, lazy-loading risks during serialization.
- [ ] **`getAllConversationMessages()` loads 50K entities** — (ConversationService:204). While improved with `toLightDto()` and batched part loading, the fundamental limit is still 50,000 messages in one request. Should be cursor-based or streaming.
- [ ] **`/media/**` and `/import/**` still `permitAll()`** — (SecurityConfig:66-67). Media files are accessible without authentication. Import endpoint is unprotected.
- [ ] **Knowledge Graph residual code** — `KgEntity.java`, `KgTriple.java`, and other KG model classes still exist in the codebase despite V18 migration dropping all KG tables. Dead code.

### Pattern Inconsistencies
- [ ] **Inconsistent URL prefixes** — `/search/text` (no `/api/`), `/import/stream` (no `/api/`), but `/api/conversations`, `/api/search/unified`. Mixed patterns.
- [ ] **`ConversationService.getAllConversationMessages()` throws `RuntimeException`** — line 200: `throw new RuntimeException("Conversation not found")` — should use `ResourceNotFoundException` (the domain exception exists but wasn't applied here).
- [ ] **No `@Valid`/`@Validated`** — Jakarta Bean Validation still not used anywhere. Input validation relies entirely on `InputLimits.truncate()` which silently truncates rather than rejecting.

### New Issues (Introduced Since March Audit)
- [ ] **`RestClientConfig.java`** — adds `SimpleClientHttpRequestFactory` for llama.cpp compatibility but with no timeout configuration. Long-running LLM calls could block indefinitely.
- [ ] **Text-to-SQL prompt injection surface** — `TextToSqlService` generates SQL from natural language. While it uses `EXPLAIN` validation and a read-only approach, the SQL is executed against the production database. The whitelist validation (`SELECT`, no `DELETE/DROP/INSERT/UPDATE`) is string-based and could be bypassed.
- [ ] **Two async task executors** — `importTaskExecutor` (2-4 threads) + `aiTaskExecutor` (1 thread). The AI executor is single-threaded (for VRAM), meaning embedding jobs, Text-to-SQL, and Q&A all queue behind each other. No prioritization.
- [ ] **`chart.js` and `cytoscape` npm packages** appear unused after Knowledge Graph removal — dead frontend dependencies.

## SOLID Principles Assessment

- **Single Responsibility**: 3/5 — `ImportService` (1,069 lines) still handles XML parsing, contact resolution, conversation grouping, media extraction, and batch persistence. `ConversationService` (561 lines) and `EmbeddingService` (560 lines) are also doing too much. However, the new domain exceptions and `InputLimits` show good SRP thinking.
- **Open/Closed**: 3/5 — Adding a new message protocol still requires modifying `ImportService` directly. The new `UnifiedSearchService` with pluggable search modes is a good OCP example.
- **Liskov Substitution**: 4/5 — Clean interface usage. No violations detected.
- **Interface Segregation**: 3/5 — `MessageRepository` has 30+ methods on a single interface. Could benefit from splitting into `MessageSearchRepository`, `MessageTimelineRepository`, etc.
- **Dependency Inversion**: 3/5 — `MediaService` still couples directly to filesystem `Path.of()`. `SearchController` directly uses `MessageRepository`. However, `EmbeddingService` properly abstracts the LLM integration through Spring AI.

## Improvement Plan

### High Priority (Structural Fixes)

1. **Set `ddl-auto: validate`** for default profile — prevents Hibernate from modifying schema in production alongside Flyway. One-line fix in `application.yml:19`.

2. **Fix N+1 on `conversation.participants`** — Add `"conversation.participants"` to `@EntityGraph` on the 7 message query methods in `MessageRepository`, or use a batch fetch join. This fires an extra query per message group on every paginated fetch.

3. **Secure `/media/**` and `/import/**`** — Remove from `permitAll()` in `SecurityConfig`. Media should require authentication (or use signed URLs). Import must require authentication.

4. **Remove Knowledge Graph dead code** — Delete `KgEntity.java`, `KgTriple.java`, `KgEntityAlias.java`, `KgEntityContactLink.java`, `KgExtractionJob.java`, and any remaining KG repository/service classes. Remove `chart.js` and `cytoscape` from `package.json` if unused.

5. **Fix `ConversationService` RuntimeException** — Replace `throw new RuntimeException("Conversation not found")` at line 200 with `throw new ResourceNotFoundException("Conversation not found")`.

### Medium Priority (Design Improvements)

6. **Extract `SearchService`** — Move `SearchController`'s direct `MessageRepository` usage into a proper service. Standardize the date-range endpoint to return DTOs instead of raw entities, with pagination.

7. **Add timeouts to `RestClientConfig`** — Set connection and read timeouts for the LLM API client. Currently, a hung llama.cpp server will block the AI thread pool indefinitely.

8. **Decompose `ImportService`** — Extract into `XmlParsingService`, `ContactResolutionService`, `DuplicateDetectionService`, and `ImportOrchestrator`. This was recommended in the March audit and remains the biggest maintainability risk.

9. **Standardize URL prefixes** — Move `/search/text`, `/search/dates`, and `/import/**` under `/api/` prefix. Add redirects or versioning for any existing clients.

10. **Replace 50K message limit with cursor-based loading** — `getAllConversationMessages()` should use cursor pagination or server-sent events for large conversations.

### Low Priority (Consistency & Polish)

11. **Add Jakarta Bean Validation** — Augment `InputLimits.truncate()` with `@Valid`, `@NotBlank`, `@Size` on request DTOs for proper 400 responses instead of silent truncation.

12. **Add Micrometer metrics** — Expose import throughput, search latency, embedding job duration, cache hit rates via `/actuator/prometheus`.

13. **Split `MessageRepository`** — 30+ methods is unwieldy. Consider `MessageSearchRepository` and `MessageTimelineRepository` as separate interfaces.

14. **Frontend: Decompose `Messages.vue`** — At 1,261 lines with 30+ reactive refs, extract `ConversationList`, `MessageThread`, and `SearchPanel` components. Create composables for shared patterns (`useSearch`, `usePagination`).

15. **Add structured JSON logging** — Replace plain text logs with Logback JSON encoder for production environments.

16. **Investigate `@Transactional` on `MediaService.deleteImage()`** — File deletion before DB deletion is still not transactional (flagged in March, not fixed).

## Migration Strategy

### Phase 1: Quick Wins (Low Risk)
- Fix `ddl-auto: validate` (1 line)
- Fix `RuntimeException` → `ResourceNotFoundException` (1 line)
- Remove KG dead code (delete files)
- Remove unused npm packages
- Add LLM client timeouts

### Phase 2: Security & Data Integrity
- Secure `/media/**` and `/import/**` endpoints
- Fix N+1 on `conversation.participants`
- Add rate limiting to auth endpoints

### Phase 3: Structural Refactoring
- Extract SearchService from SearchController
- Decompose ImportService
- Standardize API URL prefixes
- Replace 50K message limit with cursor pagination

### Phase 4: Polish & Observability
- Jakarta Bean Validation
- Micrometer metrics
- Structured logging
- Frontend component decomposition

## Impact Analysis

- **Development velocity**: Phase 1-2 changes are surgical and low-risk. Phase 3 requires careful refactoring with test coverage. The existing test suite (34 test files with Testcontainers) provides good safety nets.
- **Testing requirements**: The N+1 fix and security changes need integration test coverage. The ImportService decomposition is the highest-risk refactor — ensure the existing import tests (10+ test files) pass before and after.
- **Risk assessment**: The `ddl-auto: update` issue is the most urgent — it can cause schema drift in production. The `/media/**` permitAll is a data exposure risk.

## Recommendations

### Architecture Patterns to Adopt
- **CQRS-lite for search**: The `UnifiedSearchService` already separates read paths — formalize this with distinct query models
- **Event-driven processing**: The `ImportCompletedEvent` pattern is excellent — extend to embedding completion, search index updates
- **Composable frontend**: Extract composables (`useSearch`, `usePagination`, `useInfiniteScroll`) to DRY the view layer

### Tools & Frameworks
- **Spring Boot Actuator + Micrometer** — already on classpath, just needs configuration
- **Bucket4j or Resilience4j** — for rate limiting auth endpoints
- **Vitest** — for frontend unit testing (zero-config with Vite)

### Documentation Needs
- Update `CODEBASE_AUDIT.md` to reflect resolved items or replace with this report
- Add ADR (Architecture Decision Record) for the AI/LLM integration choices
- Document the Text-to-SQL security model and its limitations

## Estimated Effort

- Total items: 16
- High priority (1-5): ~3-5 days — mostly surgical fixes
- Medium priority (6-10): ~5-8 days — requires careful refactoring
- Low priority (11-16): ~5-8 days — polish and consistency
- Full completion: ~3-4 weeks of focused effort
