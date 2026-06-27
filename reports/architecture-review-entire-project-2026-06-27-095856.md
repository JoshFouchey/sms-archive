# Architecture Review Plan
Generated: 2026-06-27T09:58:56-04:00
Scope: Entire Project
Previous Reviews: 2026-04-16 (architecture-review), 2026-03-11 (CODEBASE_AUDIT.md)

## Executive Summary

The SMS Archive codebase continues to mature. Since the **April 2026** review, several flagged items have been resolved and the largest refactor recommendation was actually executed: **`ImportService` was decomposed from 1,069 → 417 lines**, with a new `service/importpipeline/` subpackage (`XmlMessageParser`, `ContactResolver`, `ConversationAssigner`, `DuplicateDetector`, `MediaHandler`). The Knowledge Graph dead code (`Kg*.java` model classes) has been **removed**, LLM client **timeouts are now configured**, `ddl-auto` is safely `validate`/`none` across all profiles, and `ResourceNotFoundException` replaced the raw `RuntimeException` in `ConversationService`.

However, the addition of the **Text-to-SQL / "Ask" feature** (interactive natural-language → SQL → execution against the production database) has introduced the single most significant architectural and security risk in the codebase. Generated SQL runs on a **fully-privileged DB connection**, guarded only by **bypassable regex validation** and **string-based user-isolation heuristics**. This is the top priority of this review.

The frontend remains structurally thin: **zero tests, zero composables, one Pinia store**, and a **1,261-line `Messages.vue`** plus a new **920-line `Ask.vue`** — with significant copy-paste duplication (media-path logic ×3, reaction parsing ×2). Several persistent backend issues from April are still open: `SearchController` bypasses the service layer and returns raw entities, `/media/**` is public, no rate limiting on auth, and no Jakarta Bean Validation.

**Overall Architecture Grade: B (was B+ in April)** — the grade dips slightly because the new AI feature added a Critical-severity security surface faster than the backlog of structural issues was retired. The core domain (import pipeline, persistence, search) is genuinely solid; the AI execution path is not yet production-safe.

### Audit Delta Summary

| Category | Mar Grade | Apr Grade | Jun Grade | Change |
|---|---|---|---|---|
| Project Structure | B+ | A- | A- | ➡ |
| Import Pipeline (SRP) | B | C (1069 LOC) | A- | ⬆⬆ Decomposed into importpipeline/ |
| Controller/Service Layering | B | B | B | ➡ SearchController still bypasses service |
| Exception Handling | C | A- | A- | ➡ |
| Security (core auth) | C+ | B+ | B+ | ➡ Token type validation good |
| **AI / Text-to-SQL Security** | n/a | C+ (new) | **D** | ⬇ Privileged execution, regex-only guards |
| Input Validation | D | B | B | ➡ Still no Bean Validation |
| Database/Indexing | A- | A- | A- | ➡ Excellent |
| Connection Pool | C | A | A | ➡ |
| Caching Correctness | n/a | B | B- | ⬇ conversation caches not user-keyed |
| Concurrency (AI executor) | n/a | C+ | C | ⬇ Batch + interactive share 1 thread |
| Frontend Structure | B | B+ | B- | ⬇ Ask.vue 920 LOC, no composables/tests |
| API Design | C+ | C+ | C+ | ➡ URL prefixes still inconsistent |
| Observability | D | D+ | D+ | ➡ |
| Dependencies | B- | A- | A- | ➡ Mostly clean |
| Test Coverage (backend) | B | B+ | B+ | ➡ 34 test files w/ Testcontainers |
| Test Coverage (frontend) | F | F | F | ➡ Still zero |

## Current Architecture

### Overview
- **Architecture style**: Monolithic layered (Spring Boot backend + Vue 3 SPA), single-tenant-per-user
- **Backend**: Spring Boot 3.5.6, Java 25, PostgreSQL 15 + pgvector + pg_trgm, Flyway 11 (V1–V18)
- **Frontend**: Vue 3.5, TypeScript 5.8 (strict), Vite 7, PrimeVue 4, Tailwind 4, Pinia 2
- **AI**: Spring AI (OpenAI-compatible → llama.cpp) for embeddings, Text-to-SQL, Q&A; pgvector HNSW
- **Deployment**: Docker multi-stage, docker-compose, nginx reverse proxy
- **Size**: ~110 backend Java classes (~8.5K LOC), 18 Vue/TS frontend files (~5.2K LOC)

### Key Components (24 services, 13 controllers, 7 repositories, 3 mappers)
- **Import Pipeline** (now properly decomposed): streaming XML parse → contact resolution → conversation assignment → duplicate detection → media handling → batch persist → `ImportCompletedEvent` → async embedding
- **Search System**: `UnifiedSearchService` (intent classify → keyword FTS/trigram / semantic pgvector / hybrid RRF fusion), with graceful semantic→keyword fallback
- **AI / Ask**: `TextToSqlService` (NL→SQL→execute), `QaService` (router), `EmbeddingService` (contextual chunking + job lifecycle)
- **Auth**: JWT access/refresh, BCrypt, stateless, token-type validation in `AuthTokenFilter`

### Architecture Diagram
```
┌──────────────────────────────────────────────────────────────────┐
│  Frontend (Vue 3 SPA)                                             │
│  Messages.vue(1261)⚠️  Ask.vue(920)⚠️  Gallery(400)  Contacts(398)│
│  Admin(60→tabs: AiSettings,Import)   Login/Register                │
│        │  NO composables · 1 Pinia store (auth) · NO tests        │
│        ▼                                                           │
│  api.ts(495)  +  authStore.ts(70, holds axios interceptors)       │
├──────────────────────────────────────────────────────────────────┤
│  nginx (reverse proxy, static assets, CSP)                        │
├──────────────────────────────────────────────────────────────────┤
│  Backend (Spring Boot 3.5.6 / Java 25)                            │
│  Controllers (13)                          Security               │
│   /api/* (most)   ⚠️/search ⚠️/import       AuthTokenFilter         │
│   QaController(rate-limited)                SecurityConfig         │
│         │  SearchController ──direct──► MessageRepository ⚠️       │
│         ▼                                                          │
│  Services (24)                                                     │
│   ImportService(417) ─► importpipeline/{Parser,Resolver,Assigner, │
│                          DuplicateDetector,MediaHandler} ✅        │
│   ConversationSvc(562)  EmbeddingSvc(560)  TextToSqlSvc(502)⚠️SEC  │
│   UnifiedSearchSvc(358) SemanticSearchSvc  QaService  AnalyticsSvc │
│   MediaService(FS-coupled)  CurrentUserProvider(@Cacheable user)   │
│         │                          │                               │
│  Repositories (7) + Mappers (3) + DTOs (~50)                       │
├──────────────────────────────────────────────────────────────────┤
│  PostgreSQL 15 + pgvector + pg_trgm                               │
│  single role: sms_user (read+WRITE) ⚠️ used for Text-to-SQL exec  │
│  HNSW index · GIN FTS + trigram · Flyway V1–V18                   │
└──────────────────────────────────────────────────────────────────┘
         │ aiTaskExecutor: 1 thread, queue=5  ⚠️ batch+interactive share
    ┌────┴─────┐
    │llama.cpp │  OpenAI-compatible (embeddings, chat/SQL)
    └──────────┘
```

## Issues Fixed Since April Review ✅

- [x] **`ImportService` decomposed** 1,069 → 417 LOC; logic moved to `service/importpipeline/` (the single biggest April recommendation — done).
- [x] **Knowledge Graph dead code removed** — no `Kg*.java` model classes remain.
- [x] **`ddl-auto` safe** — `application.yml:19` = `validate`; `application-test.yml` = `none`; prod/docker inherit `validate`. No `update` anywhere.
- [x] **LLM client timeouts added** — `RestClientConfig.java:20-21` connect=10s, read=120s.
- [x] **`RuntimeException` → `ResourceNotFoundException`** — `ConversationService.java:201`.
- [x] **`/import/**` secured** — no longer in `permitAll()`; `ImportController` requires auth.
- [x] **Analytics dashboard cache is user-scoped** — key includes the authenticated username (`AnalyticsService.java:90`); analytics service methods all resolve `currentUserProvider.getCurrentUser()` and filter `m.user_id`.

## Architectural Issues

### Critical Issues
- [ ] **Text-to-SQL executes generated SQL on a fully-privileged DB connection.** The app connects as `sms_user` (read+write) — `application.yml:4-5` — and `TextToSqlService.executeSql()` (`TextToSqlService.java:321-340`) runs LLM-generated SQL through the same `JdbcTemplate`. There is no read-only role, no `SET TRANSACTION READ ONLY`, no DB-level restriction. The regex validation is the *only* barrier between model output and a writable connection.
- [ ] **SQL safety is regex/string-based and bypassable.** `DANGEROUS_SQL` blacklist (`TextToSqlService.java:42-51`) blocks e.g. `DELETE\s+FROM` but blacklists are inherently incomplete (writable CTEs, functions, unenumerated keywords). `executeUserSql()` (`:118`, exposed via `POST /api/qa/sql/run`, `QaController.java:59-71`) lets a user submit arbitrary SQL validated by the same bypassable regex.
- [ ] **User-isolation in generated SQL relies on brittle string heuristics.** `prepareSql` does a literal `safeSql.contains(userId.toString())` substring check (`TextToSqlService.java:190`); `injectUserIdFilter` assumes the messages table is aliased `m.` (`:247`); `validateSql` only checks the literal string `"user_id"` appears somewhere (`:299`). None of this verifies the predicate is correctly bound to the current user on every scoped table — cross-user data exposure is not robustly prevented for arbitrary generated/edited SQL.

### Design Flaws
- [ ] **`SearchController` bypasses the service layer** — injects `MessageRepository` directly (`SearchController.java:25,28`); no `SearchService`. (Open since March.)
- [ ] **`SearchController.byDateRange()` returns raw JPA entities** — `List<Message>` serialized directly (`SearchController.java:69-72`), leaking the entity graph and risking lazy-load serialization. The sibling `byText()` correctly returns DTOs — inconsistent within one controller.
- [ ] **`deleteConversationById()` has no `@Transactional`** — multi-step `deleteAll` + `flush` + `delete` with `@CacheEvict` but no transaction (`ConversationService.java:546-561`); a mid-way failure orphans messages/parts.
- [ ] **`MediaService` couples directly to the filesystem and mixes FS + DB writes non-transactionally** — `Path.of(part.getFilePath())`, thumbnail name string-munging, `Files.deleteIfExists` (`MediaService.java:51-66`); `deleteImage()` also deletes the entire parent message as a side effect (`:65`). No storage abstraction, FS/DB can diverge on failure.
- [ ] **`getAllConversationMessages()` still has a 50,000-row hard cap** (`ConversationService.java:205`) — silently truncates larger conversations; `@Cacheable` without `@Transactional` while it lazily mutates `parts` collections (`:218-224`).
- [ ] **`/media/**` is still `permitAll()`** — `SecurityConfig.java:66` carries a `TODO: secure with token-based access`; media files served from disk (`WebConfig.java:19`) are publicly readable without auth.
- [ ] **No rate limiting on auth endpoints** — `RateLimiter` exists but is wired only to `QaController` (`AsyncConfig.java:36-37`, `QaController.java:34`); `AuthController` login/register/refresh have none → brute-force / credential-stuffing exposure.

### Concurrency Issues
- [ ] **Single shared single-threaded `aiTaskExecutor` couples batch and interactive work** — `corePoolSize=1, queueCapacity=5` (`AsyncConfig.java:23-32`); injected into both `EmbeddingService` (long batch jobs) and `TextToSqlService` (interactive). A running batch embed starves interactive Ask requests, and only 5 may queue before `RejectedExecutionException` (default `AbortPolicy`). No prioritization, no interactive/batch separation.
- [ ] **Timeout cannot interrupt the blocking LLM HTTP call** — `future.cancel(true)` is best-effort (`TextToSqlService.java:174-177`); the worker stays occupied until llama.cpp responds. `EmbeddingService.embedQuery()` (`:396-402`) has **no timeout at all** and retries with a blocking `Thread.sleep` on the request thread.

### Caching Correctness
- [ ] **`conversationMessages` / `conversationMessageCount` / `conversationTimeline` caches are keyed by `conversationId` only, not by user** (`ConversationService.java:195,236,248`). Conversation IDs are globally unique so there is no cross-user *collision*, but the cache provides zero defense against an IDOR if ownership isn't enforced on the endpoint — verify the controller checks ownership before caching.
- [ ] **`currentUser` cached 15 min keyed by username with no `@CacheEvict` on user change** (`CurrentUserProvider.java:24`, `CacheConfig.java:30,36`) — role/disable/password changes don't take effect for up to 15 minutes; caching a mutable security principal is risky.

### Pattern Inconsistencies
- [ ] **Inconsistent URL prefixes** — `/search` (`SearchController.java:22`) and `/import` (`ImportController.java:15`) lack `/api`, while most controllers use `/api/...`. Two search namespaces coexist: `/search` vs `/api/search` (`SemanticSearchController.java:21`). `UserController` has no class-level `@RequestMapping`.
- [ ] **No Jakarta Bean Validation** — zero `@Valid`/`@Validated`/`@NotBlank` hits; no `spring-boot-starter-validation`. All input checks are hand-rolled against `InputLimits` (`AuthController.java:33-37`), which truncates silently rather than returning 400.
- [ ] **Prompt injection surface** — raw user question interpolated into the SQL-gen prompt with no delimiting/guarding (`TextToSqlService.java:141-143`); `normalizeQuestion` only rewrites "vs" (`:231-234`).
- [ ] **Generic catch-all → 500** — `RestExceptionHandler.java:48-51` collapses every uncaught exception to opaque 500; no `MethodArgumentNotValidException` handler (consistent with validation absence).

### Frontend Issues
- [ ] **No test infrastructure at all** — no vitest/jest, no `*.spec.ts`/`*.test.ts`. ~5.2K LOC (reaction parsing, media-path derivation, SQL/CSV, auth refresh) entirely unverified.
- [ ] **`Messages.vue` 1,261 LOC; `Ask.vue` 920 LOC** — monolithic SFCs owning many concerns each (`Messages.vue` has 3 separate `onMounted`/`onUnmounted` blocks).
- [ ] **Zero composables** — no `composables/`, no `use*.ts`. Cross-cutting logic is inlined per view, causing duplication: `normalizePath`/`extractRelativeMediaPath`/`buildMediaUrl` copy-pasted ×3 (`Messages.vue:541-565`, `MessageBubble.vue:225-249`, `Gallery.vue:282-335`); `parseReaction` ×2; body-scroll-lock ×2.
- [ ] **Auth/API logic duplicated and fragile** — `API_BASE` defined in both `api.ts:4` and `authStore.ts:4`; axios 401-refresh interceptor lives inside the store (`authStore.ts:48-70`), does a hard `window.location.href='/login'` bypassing the router (`:67`), and has no in-flight refresh dedupe (concurrent 401s each refresh).
- [ ] **No shared server-state layer** — `Contacts.vue`, `Gallery.vue`, `Messages.vue` each independently fetch/cache contacts/conversations in local refs; only one Pinia store (auth).
- [ ] **`SqlAnalyticsData` defined twice and diverged** — `api.ts:464-476` vs a local redefinition in `Ask.vue:584-597` that adds `suggestedChart`; `Ask.vue` relies on its shadow copy.
- [ ] **Dead nav / unrouted views** — `App.vue:130` "Explore" nav points at `/explore` which the router redirects back to `/` (`router/index.ts:24`); `AiSettings.vue`/`Import.vue` are now only Admin tabs.
- [ ] **`any` in API response types** — `Message.media`/`metadata` (`api.ts:41-42`), `QaResponse.analyticsData` (`:482`); native `alert()`/`confirm()` mixed with PrimeVue Toast/ConfirmDialog.

### Configuration Issues
- [ ] **Docker CORS config is malformed and silently ignored** — `application-docker.yml:3` uses the wrong key (`app.cors` vs the code-read `cors.allowed-origins`, `SecurityConfig.java:24`), a malformed comma-joined single string, and a typo `localhostL8070`. The docker profile falls back to the localhost defaults (`SecurityConfig.java:36-41`), so production-via-docker origins are not honored.

## SOLID Principles Assessment

- **Single Responsibility: 3.5/5** — Big win: `ImportService` decomposed into `importpipeline/`. But `TextToSqlService` (502 LOC) mixes prompt construction, LLM invocation, output repair, security validation, user-id injection, execution, and answer/chart formatting (`TextToSqlService.java:53-456`); `EmbeddingService` (560 LOC) mixes job lifecycle, chunking, context-building, raw JDBC persistence, retry, and stats.
- **Open/Closed: 3/5** — `UnifiedSearchService` pluggable modes are good OCP; adding a message protocol still touches the import pipeline.
- **Liskov Substitution: 4/5** — No violations detected.
- **Interface Segregation: 3/5** — `MessageRepository` (328 LOC, 30+ methods) is a fat interface.
- **Dependency Inversion: 2.5/5** — `SearchController`→`MessageRepository` (no service); `MediaService`→filesystem `Path.of`; `EmbeddingService` binds to **concrete** `OpenAiEmbeddingModel`/`OpenAiEmbeddingOptions` (`EmbeddingService.java:17-18,374-378`) instead of the vendor-neutral `EmbeddingModel` interface, undermining Spring AI's portability layer.

## Improvement Plan

### High Priority (Critical Security + Structural)
1. **Run Text-to-SQL on a dedicated read-only DB role** — create a `sms_readonly` Postgres role with `SELECT`-only grants and a separate `DataSource`/`JdbcTemplate` used exclusively by `TextToSqlService.executeSql()`. This is defense-in-depth so a regex bypass cannot write. (`TextToSqlService.java:321-340`, `application.yml`)
2. **Replace string-based SQL validation + user isolation with a parsed approach** — use a real SQL parser (e.g. JSqlParser) to (a) confirm a single read-only statement, (b) confirm only whitelisted tables, and (c) enforce a mandatory `user_id = :currentUserId` predicate on every user-scoped table rather than the `m.`-alias / substring heuristics (`TextToSqlService.java:127-308`).
3. **Reconsider / lock down `POST /api/qa/sql/run`** — allowing arbitrary user-submitted SQL is the highest-risk endpoint; gate behind the read-only role (#1) at minimum, or restrict to admins (`QaController.java:59-71`).
4. **Secure `/media/**`** — replace `permitAll()` with authenticated/signed-URL access (`SecurityConfig.java:66`, `WebConfig.java:19`).
5. **Add rate limiting to auth endpoints** — extend the existing `RateLimiter` (or Bucket4j/Resilience4j) to `AuthController` login/register/refresh.
6. **Extract `SearchService` and return DTOs** — move `MessageRepository` usage out of `SearchController`; make `byDateRange()` return paginated DTOs, not entities (`SearchController.java:25-72`).

### Medium Priority (Design + Concurrency)
7. **Split the AI executor** — separate `aiInteractiveExecutor` (low-latency, for Text-to-SQL/Q&A) from `aiBatchExecutor` (embeddings), or add priority so batch embeds don't starve interactive Ask requests (`AsyncConfig.java:23-32`). Add an explicit rejection policy and a timeout on `EmbeddingService.embedQuery()`.
8. **Add `@Transactional` to multi-step mutators** — `deleteConversationById()` (`ConversationService.java:546`) and `MediaService.deleteImage()` (`MediaService.java:51-66`); order FS deletes after the DB commit or use a compensating action.
9. **Key conversation caches by user (or assert ownership)** — add the username to `conversationMessages`/`Count`/`Timeline` keys, and verify the endpoints check ownership before serving (`ConversationService.java:195-248`).
10. **Evict / shorten `currentUser` cache on user mutation** — add `@CacheEvict` on user update/role/enable changes (`CurrentUserProvider.java:24`).
11. **Abstract the LLM behind Spring AI interfaces** — depend on `EmbeddingModel`/`ChatModel` interfaces, not the OpenAI concrete classes (`EmbeddingService.java:17-18`).
12. **Replace the 50K message cap with cursor/streaming pagination** (`ConversationService.java:205`).
13. **Standardize URL prefixes** under `/api/` and consolidate the two search namespaces (`SearchController.java:22`, `ImportController.java:15`).

### Low Priority (Consistency, Frontend, Observability)
14. **Stand up Vitest** and cover the parsing/SQL utilities first (highest unverified-risk frontend code).
15. **Extract frontend composables** — `useMediaPath`, `useReactions`, `useImageViewer`, `usePagination`; decompose `Messages.vue` and `Ask.vue` into child components.
16. **Consolidate the API/auth layer** — single `API_BASE`, axios interceptors in `api.ts` (not the store), in-flight refresh dedupe, router-based redirect instead of `window.location.href`.
17. **Add Jakarta Bean Validation** — `spring-boot-starter-validation` + `@Valid`/`@NotBlank`/`@Size` on request DTOs and a `MethodArgumentNotValidException` handler for proper 400s.
18. **Fix docker CORS config** — correct key, list syntax, and the `localhostL8070` typo (`application-docker.yml:3`).
19. **Add Micrometer metrics + structured logging** — import throughput, search latency, embedding job duration, AI queue depth, cache hit rates via `/actuator/prometheus`.
20. **Remove dead frontend nav** — drop the `/explore` "Explore" item (`App.vue:130`); clarify `chart.js` is a `vue-chartjs` peer.

## Migration Strategy

### Phase 1: Critical AI Security (do first, ~3-5 days)
- Dedicated read-only DB role for Text-to-SQL (#1)
- Parser-based SQL validation + mandatory user predicate (#2)
- Lock down `/api/qa/sql/run` (#3)
- Secure `/media/**` (#4); auth rate limiting (#5)

### Phase 2: Structural & Data Integrity (~5-8 days)
- Extract `SearchService`, DTO-ify date-range (#6)
- `@Transactional` on delete paths (#8)
- User-keyed conversation caches + ownership checks (#9, #10)
- Split AI executor + embedding timeout (#7)

### Phase 3: Consistency & Portability (~3-5 days)
- Spring AI interface abstraction (#11)
- Cursor pagination for large conversations (#12)
- URL prefix standardization (#13)
- Bean Validation (#17); docker CORS fix (#18)

### Phase 4: Frontend Health & Observability (~1-2 weeks)
- Vitest + utility coverage (#14)
- Composables + view decomposition (#15)
- API/auth consolidation (#16)
- Micrometer + structured logging (#19); dead nav cleanup (#20)

## Impact Analysis
- **Development velocity**: Phases 1–2 are mostly additive (new role, new validation layer, annotations) and low-blast-radius given the 34-file Testcontainers backend suite. Frontend refactors (Phase 4) are higher-churn but currently unguarded by tests — write tests *before* decomposing.
- **Testing requirements**: SQL-validation parser changes and user-isolation enforcement **must** get integration tests (cross-user attempts, injection payloads, write attempts). The cache-keying change needs a multi-user cache test.
- **Risk assessment**: The Text-to-SQL privileged-execution + bypassable-guard combination is the highest risk — it permits potential data exfiltration/mutation across tenants if a single regex is defeated. `/media/**` public access is a data-exposure risk. Both should be treated as security incidents-in-waiting, not backlog polish.

## Recommendations
- **Defense-in-depth for AI SQL**: read-only role (DB layer) + parsed validation (app layer) + mandatory user predicate (query layer). Never rely on a single string check.
- **CQRS-lite for search/analytics**: the read paths are already separate — formalize distinct query models and keep them off the writable connection.
- **Event-driven processing**: `ImportCompletedEvent` is a good pattern — extend to embedding/search-index completion.
- **Composable frontend + server-state cache**: introduce composables and a lightweight server-state cache to kill the ×3 duplication.
- **Tools**: JSqlParser (SQL validation), Bucket4j/Resilience4j (rate limiting), Micrometer (already on classpath), Vitest (frontend), spring-boot-starter-validation.
- **Docs**: write an ADR for the Text-to-SQL security model and its limitations; update `CODEBASE_AUDIT.md` or supersede it with this report.

## Estimated Effort
- Total items: 20
- Critical security (1–5/6): ~3-5 days — must-do, security-gating
- Medium structural (7–13): ~8-13 days — careful refactor with tests
- Low / frontend / observability (14–20): ~2-3 weeks
- Full completion: ~5-6 weeks of focused effort; the **Critical AI security subset is ~1 week and should not wait**.
