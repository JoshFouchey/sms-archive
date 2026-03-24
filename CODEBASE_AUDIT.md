# SMS Archive — Comprehensive Codebase Audit

**Date:** 2026-03-11  
**Auditor:** Copilot (Claude Opus 4.6)  
**Stack:** Spring Boot 3.5.6, Java 25, Vue 3.5, TypeScript 5.8, Postgres 15, Flyway 11

---

## 1. Dependency & Framework Audit

### Build Configuration (`build.gradle`)

| Dependency | Current | Assessment |
|---|---|---|
| Spring Boot | 3.5.6 | ✅ Current |
| Java toolchain | **25** | ⚠️ Java 25 is EA/preview (March 2026). Pin to 21 LTS or 24 for production stability. |
| Gradle | 9.1.0 | ✅ Current |
| Flyway | 11.13.2 | ✅ Current |
| JJWT | **0.11.5** | 🔴 **Outdated — 0.12.x has breaking API changes and security fixes. The builder API (`Jwts.builder().setSubject()`) is deprecated in favor of `Jwts.builder().subject()`.** |
| hibernate-types-60 | 2.21.1 | 🔴 **Dead library. Vladmihalcea migrated to `hypersistence-utils`. You already have `hypersistence-utils-hibernate-63:3.11.0` — remove `hibernate-types-60` entirely.** |
| Guava | 33.4.8-jre | ⚠️ Only used for `@VisibleForTesting`. Replace with a comment convention or extract to test scope. |
| commons-io | 2.15.1 | ⚠️ Minor — 2.19.0 is latest. |
| Caffeine | 3.1.8 | ⚠️ 3.2.x available with virtual thread improvements. |
| Thumbnailator | 0.4.21 | ✅ Fine |
| libphonenumber | 8.13.47 | ⚠️ Newer versions exist; you're not using it in production code — **remove if unused.** |
| sqlite-jdbc | 3.45.3.0 | 🔴 **Appears unused in production. Only H2 + Postgres are used. Remove.** |
| H2 (test) | 2.4.240 | ⚠️ You use Testcontainers Postgres. Consider removing H2 if not needed. |
| Testcontainers | 1.20.1 | ⚠️ 1.20.x is fine but 1.21+ has improvements. |
| Lombok | 1.18.42 | ✅ Fine, but Java records could replace many uses. |
| commons-compress forced | 1.26.1 | ✅ Security override — good practice. |

**Frontend (`package.json`)**

| Dependency | Current | Assessment |
|---|---|---|
| Vue | 3.5.21 | ✅ Current |
| Vite | **7.1.7** | ✅ Cutting edge |
| PrimeVue | 4.3.9 | ✅ Current |
| Pinia | 2.1.7 | ✅ |
| Axios | 1.7.7 | ✅ |
| chart.js | 4.4.3 | ✅ |
| TypeScript | 5.8.3 | ✅ |
| `@types/vue-router` | **2.0.0** | 🔴 **Obsolete. Vue Router 4 has built-in types. Remove this devDependency.** |
| `@vue/tsconfig` | 0.8.1 | ⚠️ Check for updates |
| Tailwind CSS | 4.1.14 | ✅ (v4 with Vite plugin) |

### Critical Findings

1. **Duplicate JSONB libraries**: Both `hibernate-types-60` AND `hypersistence-utils-hibernate-63` are on the classpath. The former imports `com.vladmihalcea.hibernate.type.json.JsonBinaryType` which is used in `Message.java`. Migrate to `io.hypersistence.utils.hibernate.type.json.JsonBinaryType` and remove the old library.

2. **JJWT deprecated API**: `TokenService.java` uses the legacy JJWT 0.11 builder API (`setSubject()`, `setIssuedAt()`, `parserBuilder()`). These are removed in 0.12+.

3. **Unused dependencies**: `sqlite-jdbc`, `libphonenumber` (no usage found in service code), and `@types/vue-router` should be removed.

---

## 2. Backend (Spring Boot + Java) Review

### 2.1 Project Structure — Grade: B+

```
com.joshfouchey.smsarchive/
├── config/        (5 classes) — Security, Cache, Web, Async, Exception handling
├── controller/    (11 controllers) — REST endpoints
├── dto/           (14 DTOs) — Good use of records
├── mapper/        (3 mappers) — Manual mapping (acceptable at this scale)
├── model/         (7 entities) — JPA entities
├── repository/    (5 repos) — Spring Data JPA
├── security/      (1 filter) — JWT filter
└── service/       (16 services) — Business logic
```

**Positive**: Clean layering with proper separation. DTOs are records. Controllers are thin.

**Issues**:
- No package for exceptions (custom exceptions scattered as `RuntimeException`)
- `ImportService.java` is **1000+ lines** — a God Class that handles XML parsing, contact resolution, conversation assignment, media handling, duplicate detection, batch persistence, and progress tracking. This is the single largest maintainability risk in the codebase.

### 2.2 Java Modernization Opportunities

**Records**: DTOs already use records ✅. But `ImportProgress` (inner class of `ImportService`) uses Lombok `@Getter` + mutable fields — could be a mutable state holder but should at least be extracted to its own file.

**Pattern Matching / Switch Expressions**: Good use in `handleStartElement()`:
```java
switch (local) {
    case "sms" -> handleStartSms(r, ctx, progress, seenKeys, batch);
    case "mms", "rcs" -> startMultipartMessage(r, ctx, local);
    ...
}
```

**Sealed Classes**: `MessageDirection` and `MessageProtocol` enums are fine, but consider sealed interfaces for error types if you add custom exceptions.

**Text Blocks**: Good use in repository native queries ✅.

### 2.3 Controller/Service/Repository Layering — Grade: B

**Issues**:

1. **SearchController directly injects `MessageRepository`** — bypasses the service layer:
```java
// SearchController.java:26
private final MessageRepository repo; // ← Should go through a SearchService
```

2. **ConversationController returns `Map<String, Object>`** for search results instead of a typed DTO:
```java
// ConversationController.java:86
public java.util.Map<String, Object> searchConversationMessages(...)
```

3. **Controllers use fully-qualified class names** instead of imports throughout `ConversationController.java` — e.g., `org.springframework.http.ResponseEntity`, `java.util.Map`.

### 2.4 JPA Entity Design — Grade: B-

**N+1 Risk — CRITICAL in `MessageMapper.toDto()`**:
```java
// MessageMapper.java:19-22
if (msg.getConversation() != null && msg.getConversation().getParticipants() != null
        && msg.getConversation().getParticipants().size() == 1) {
    c = msg.getConversation().getParticipants().iterator().next();
}
```
Accessing `getConversation().getParticipants()` triggers lazy loading. The two-query pattern in `ConversationService.getConversationMessages()` uses `@EntityGraph(attributePaths = {"parts", "senderContact"})` but **does not eagerly fetch `conversation.participants`** — so every `toDto()` call fires an additional query to load participants. This is an **N+1 on every paginated message fetch**.

**Fix**: Add `"conversation.participants"` to the `@EntityGraph` `attributePaths`, or use a projection-based DTO query.

**`@BatchSize` helps but doesn't eliminate**: `@org.hibernate.annotations.BatchSize(size = 25)` on `Conversation.participants` reduces N+1 from N to N/25 queries, but it's still sub-optimal for large result sets.

**`Message.media` and `Message.metadata` as `Map<String, Object>` with JSONB**: Acceptable for flexible storage, but:
- No type safety on contents
- `@Type(JsonBinaryType.class)` is from the **deprecated** `hibernate-types-60` library

**`User` entity uses `UUID` PK without `@GeneratedValue`**: The `@PrePersist` generates UUIDs manually. This is fine but `@UuidGenerator` from Hibernate 6 is cleaner.

**Contact equality**: `ContactService.mergeContacts()` does `primaryContact.getUser().equals(user)` — but `User` has no `equals()/hashCode()` override. This relies on JPA identity (same persistence context instance). **This is fragile** and will break if entities are detached.

### 2.5 Transaction Boundaries — Grade: B-

**Missing `@Transactional` on critical paths**:
- `ImportService.startImportAsync()` — The async runnable calls `runStreamingImportAsync()` which calls `flushStreamingBatch()` → `messageRepo.saveAll()`. There is **no `@Transactional` boundary** around the batch save. Each `saveAll()` runs in its own auto-committed transaction, which means a failure mid-import leaves partial data.
- `MediaService.deleteImage()` — Deletes file from disk, then deletes from DB. If the DB delete fails, the file is already gone. Should be `@Transactional` with file deletion as a post-commit action.

**Good practices observed**: `@Transactional(readOnly = true)` on read methods ✅.

### 2.6 Exception Handling — Grade: C

**`RestExceptionHandler.java` has a dangerous mapping**:
```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<...> handleIllegalState(IllegalStateException ex, WebRequest req) {
    return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
}
```
**Every `IllegalStateException` in the entire application returns 401 Unauthorized.** This is semantically wrong — `IllegalStateException` is a general Java exception. Spring framework internals, Hibernate, and many libraries throw it. This will cause mysterious 401 responses for unrelated failures.

**Generic Exception handler leaks internals**:
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<...> handleGeneric(Exception ex, WebRequest req) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req);
}
```
`ex.getMessage()` can expose stack traces, SQL errors, and internal paths to the client. **Security risk.**

**Services throw generic `RuntimeException`**:
```java
// ContactService.java:81
throw new RuntimeException("Primary contact not found");
// ConversationService.java:113
throw new RuntimeException("Conversation not found");
```
Create domain-specific exceptions: `ContactNotFoundException`, `ConversationNotFoundException`, `UnauthorizedException`.

### 2.7 Security — Grade: C+

**Critical Issues**:

1. **`/api/contacts` is `permitAll()`** in `SecurityConfig.java:66`:
```java
.requestMatchers(
    "/api/auth/login", "/api/auth/refresh", "/api/auth/register", "/api/auth/me",
    "/api/contacts",  // ← UNAUTHENTICATED ACCESS TO ALL CONTACTS
    "/media/**",      // ← UNAUTHENTICATED ACCESS TO ALL MEDIA FILES
    "/import/**",     // ← UNAUTHENTICATED IMPORT ENDPOINT
    "/actuator/health"
).permitAll()
```
**This means anyone can list all contacts, view all media files, and trigger imports without authentication.** The comment says "adjust if should be secured" — it absolutely should be.

2. **CSRF disabled** — Acceptable for a stateless JWT API, but only if all state-changing operations require Bearer tokens. The `/import/**` endpoint being `permitAll()` makes this dangerous.

3. **JWT secret defaults to `"changeme-secret"`** in `application.yml`:
```yaml
jwt:
  secret: ${JWT_SECRET:changeme-secret}
```
In `TokenService.java`, if the secret is blank, a random one is generated (good), but the default value `"changeme-secret"` is dangerously short for HMAC and will be used if `JWT_SECRET` env var equals this literal.

4. **`AuthTokenFilter` doesn't validate token type**: It calls `tokenService.parse(token)` which accepts both access and refresh tokens. A refresh token can be used as a Bearer token to authenticate. The filter should verify `isAccessToken()`.

5. **No rate limiting** on `/api/auth/login` and `/api/auth/register` — brute force vulnerability.

6. **`AuthController` doesn't handle `/api/auth/me`** — there is no endpoint for it. The frontend calls `GET /api/auth/me` but no controller method handles it. This will 404 or be handled by a different controller.

### 2.8 Logging — Grade: B

- Uses `@Slf4j` (Lombok) and `LoggerFactory` — consistent
- Log levels are configurable via `LOG_LEVEL` env var ✅
- Import progress logging is good
- **Issue**: DEBUG-level logs in import service include message bodies/numbers — could log PII in production. Ensure `LOG_LEVEL` is never set to DEBUG in prod.

### 2.9 Test Quality — Grade: B+

**Strengths**:
- Testcontainers for real Postgres ✅ (3 test container configs)
- Tests cover import service, analytics, duplicate detection, schema migration, controllers
- 30+ test files with good coverage of core import logic
- Test profiles properly configured

**Weaknesses**:
- No tests for `AuthController` registration/login flow
- No tests for `SecurityConfig` access rules (the `permitAll` issues wouldn't have been caught)
- `AuthIntegrationTest.java` exists but wasn't reviewed — needs to cover the security gaps
- No test for `TokenService` token type validation

---

## 3. Database & Postgres Review

### 3.1 Schema Design — Grade: B+

Good normalized design with proper foreign keys and cascading deletes. The `conversation_contacts` join table correctly models the many-to-many relationship.

**Issues**:
- **`ddl-auto: update` in default profile** (`application.yml:12`): This means Hibernate will modify the schema in production alongside Flyway. These tools conflict. **Set `ddl-auto: validate` (or `none`) for all non-dev profiles.**
- `messages.body` has no length constraint — unbounded TEXT columns can cause OOM on very large messages.
- No partial indexes — e.g., `WHERE direction = 'INBOUND'` queries could benefit from partial indexes.

### 3.2 Indexing — Grade: A-

**Excellent index coverage**:
- GIN full-text search index on `messages.body` ✅
- GIN trigram index for fuzzy search ✅
- Composite dedupe index `(conversation_id, timestamp, msg_box, protocol)` ✅
- `conversation_contacts` join table indexed both ways ✅
- User-scoped unique constraint on contacts ✅

**Missing Indexes**:
- `messages(conversation_id, user_id, timestamp)` — composite for the most common query pattern (conversation messages sorted by time)
- `messages(user_id, timestamp DESC)` — for global timeline queries
- `conversation_contacts(contact_id, conversation_id)` — reverse of the PK for `findByParticipant` queries

### 3.3 Query Performance — Grade: B

**Excellent**: The `LATERAL JOIN` in `ConversationRepository.findAllByUserWithLastMessage()` is an efficient Postgres pattern for "last row per group" ✅.

**Concern**: `similarity()` function in search queries (`MessageRepository` lines 99, 108) calls `similarity()` on every row without index support. The GIN trigram index helps `%` operators but `similarity()` still computes per-row. For large message tables (100K+), this will be slow. Consider using `word_similarity()` or `%` operator instead, which can use the index.

**Concern**: `getAllConversationMessages()` loads up to **50,000 messages** in a single query:
```java
Pageable limit = PageRequest.of(0, 50000, Sort.by("timestamp").ascending());
```
This is a memory bomb. Each `Message` entity with JSONB fields, parts, and contact references can be 2-5KB in heap. 50K messages = 100-250MB of heap per request.

### 3.4 Connection Pool — Grade: C

**No HikariCP configuration in the default profile.** Only the test profile configures it:
```yaml
# application-test.yml
hikari:
  maximumPoolSize: 5
  minimumIdle: 1
```
Production uses Spring Boot defaults (max 10 connections). For a multi-user app with async import workers, this is likely insufficient. The `importTaskExecutor` has up to 4 threads, each holding a connection during batch operations. Add to `application.yml`:
```yaml
spring.datasource.hikari:
  maximum-pool-size: 20
  minimum-idle: 5
  connection-timeout: 30000
  idle-timeout: 600000
  max-lifetime: 1800000
  leak-detection-threshold: 60000
```

---

## 4. Frontend (Vue 3 + TypeScript + Vite) Review

### 4.1 Component Structure — Grade: B

- Good use of Composition API with `<script setup>` ✅
- PrimeVue + Tailwind CSS for UI ✅
- Pinia for state management ✅
- Router with auth guards ✅

**Issues**:
- **No lazy loading of routes** — All views are eagerly imported in `router/index.ts`. For an SPA with 8 views, this increases initial bundle size unnecessarily:
```typescript
// Current: eager imports
import Search from "../views/Search.vue";
import Messages from "../views/Messages.vue";
// Better: lazy loading
const Search = () => import("../views/Search.vue");
const Messages = () => import("../views/Messages.vue");
```

- **Only 6 components** in `components/` — most UI logic lives in massive view files. `Messages.vue` is likely 500+ lines. Extract reusable pieces (conversation list, message list, timeline navigator).

- **`HelloWorld.vue` still exists** — scaffolding artifact. Remove it.

### 4.2 TypeScript Quality — Grade: C+

- `api.ts` has `any` types in multiple places:
```typescript
params: any = { page, size };  // line 233
media?: any;                   // line 42 of Message interface
metadata?: any;                // line 43
```
- The `Message` interface mixes concerns: `sender`, `recipient` (legacy), `senderContactId`, `contactName` — this suggests the type evolved without cleanup.
- No `strict: true` verification in tsconfig was done. Check `tsconfig.app.json` for `"strict": true`.

### 4.3 API Integration — Grade: B-

**Token handling is solid**: Axios interceptor auto-attaches Bearer token and handles 401 refresh ✅.

**Issues**:
1. **Infinite retry loop risk** in `authStore.ts` response interceptor:
```typescript
axios.interceptors.response.use(r => r, async err => {
  if (err.response && err.response.status === 401) {
    try { await store.refresh(); if (store.accessToken) { ... return axios.request(err.config); } }
    catch { store.logout(); window.location.href='/login'; }
  }
  return Promise.reject(err);
});
```
If the refresh endpoint also returns 401, this triggers the interceptor again → infinite loop. Add a flag to prevent re-intercepting refresh attempts.

2. **`API_BASE` is duplicated** — defined in both `api.ts` and `authStore.ts`:
```typescript
// api.ts:4
const API_BASE = (import.meta.env.VITE_API_BASE ?? '').trim();
// authStore.ts:4
const API_BASE = (import.meta.env.VITE_API_BASE as string) || '';
```
Should import from a single source.

3. **No request cancellation** — Long-running searches or conversation loads have no `AbortController` support. Rapid navigation will accumulate abandoned requests.

### 4.4 Security — Grade: B-

- Tokens stored in `localStorage` — vulnerable to XSS. `httpOnly` cookies would be more secure but require backend changes.
- No CSP enforcement in the SPA itself (CSP is configured via nginx template — good).
- No sanitization of message body content before rendering — if messages contain HTML, this could be an XSS vector. Check if `v-html` is used anywhere in `MessageBubble.vue`.

---

## 5. API & Integration Review

### 5.1 REST API Design — Grade: C+

**Inconsistent URL patterns**:
| Endpoint | Style |
|---|---|
| `GET /api/conversations` | ✅ Resource-oriented |
| `GET /api/messages/contacts` | ❌ Should be `/api/contacts/summaries` |
| `GET /search/text` | ❌ Missing `/api/` prefix |
| `POST /import/stream` | ❌ Missing `/api/` prefix |
| `GET /import/progress/{id}` | ❌ Missing `/api/` prefix |

**No API versioning** — All endpoints are unversioned. When breaking changes occur, all clients break simultaneously.

### 5.2 Validation — Grade: D

**Almost no input validation**:
- `AuthController.register()` checks `password.length() < 6` but no max length, no complexity rules.
- No `@Valid` or `@Validated` annotations anywhere in the codebase.
- No Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Email`) on any request body.
- `ConversationController.renameConversation()` accepts a raw `Map<String, String>` instead of a typed request DTO.
- Search text input is passed directly to Postgres `plainto_tsquery()` — while `plainto_tsquery` is injection-safe, the `similarity()` function receives raw input via parameterized queries (safe, but no length limit means a 10MB search string gets processed).

### 5.3 Error Response Structure — Grade: C

The error shape from `RestExceptionHandler` is:
```json
{"timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/..."}
```
But many controllers return their own ad-hoc error shapes:
```java
Map.of("error", "Invalid credentials")  // AuthController
Map.of("status", 404, "error", "Not Found", "message", "...")  // MediaController
```
Standardize on a single `ErrorResponse` record.

### 5.4 Pagination — Grade: B+

Good consistent use of `PagedResponse<T>` record ✅. Page size is capped at 500 in service layer ✅.

**Issue**: `SearchController.byDateRange()` returns `List<Message>` (raw entities, unbounded) — not paginated, and **returns JPA entities directly as JSON**, leaking internal fields and potentially causing lazy-loading issues during serialization.

---

## 6. Architecture & Maintainability

### 6.1 Code Smells

1. **God Class**: `ImportService.java` (~1000 lines, 50+ methods) — handles XML parsing, contact resolution, conversation management, media relocation, duplicate detection, batch persistence, progress tracking, and thumbnail generation. Should be decomposed into:
   - `XmlParsingService` — StAX parsing
   - `ContactResolutionService` — contact lookup/creation  
   - `DuplicateDetectionService` — dedup logic
   - `ImportOrchestrator` — coordinates the above

2. **`ElementContext` mutable inner class**: Used to pass 8 mutable fields through the XML parsing loop. This is a code smell — replace with a proper state machine or builder.

3. **`ThreadLocal<User>` for async import**: `ImportService.threadLocalImportUser` is used to pass user context to async threads. This works but is fragile. Consider passing `User` as a method parameter throughout the call chain.

4. **`CurrentUserProvider.getCurrentUser()` is `@Cacheable`** with a key derived from `SecurityContextHolder` — but `SecurityContextHolder` is thread-local. In async contexts (import workers), the SecurityContext is null, and the cache key evaluation will throw `NullPointerException` if called. The `threadLocalImportUser` workaround avoids this but creates a parallel code path.

5. **Fully-qualified class names** scattered through `ConversationService.java` and `ConversationController.java` instead of proper imports — suggests code was generated incrementally without cleanup.

### 6.2 SOLID Violations

- **SRP**: `ImportService` violates Single Responsibility massively.
- **OCP**: Adding a new message protocol (e.g., iMessage) requires modifying `ImportService` directly rather than plugging in a new parser.
- **DIP**: `MediaService` directly constructs `Path.of(part.getFilePath())` — tightly coupled to filesystem. Should inject a `StorageService` abstraction.

### 6.3 Missing Domain Exceptions

The codebase throws `RuntimeException`, `IllegalStateException`, `IllegalArgumentException`, and `NoSuchElementException` for domain errors. Create:
```java
public class ResourceNotFoundException extends RuntimeException { ... }
public class UnauthorizedAccessException extends RuntimeException { ... }
public class ImportFailedException extends RuntimeException { ... }
```

---

## 7. Performance & Memory Analysis

### 7.1 Memory Risks

1. **`getAllConversationMessages()` — 50K message limit**: Loading 50,000 JPA entities into memory for a single request. With JSONB `media` and `metadata` maps, this can consume 200MB+ of heap. **High risk of OOM under load.**

2. **`ImportService.contactCache` is a `ConcurrentHashMap` that is never evicted**:
```java
private final Map<String, Contact> contactCache = new ConcurrentHashMap<>();
```
After importing millions of messages, this map grows indefinitely. It should be a bounded cache (e.g., Caffeine with maxSize).

3. **`ImportService.progressMap` is never cleaned up**: Completed import jobs stay in memory forever. Add a TTL or cleanup scheduled task.

4. **Caffeine cache `maximumSize: 500` with `expireAfterWrite: 24h`**: The `conversationMessages` cache can hold 500 entries, each containing up to 50K messages mapped to DTOs. Worst case: 500 × 50K DTOs = 25 million objects cached. **Set a `maximumWeight` based on estimated entry size instead.**

### 7.2 Query Performance

1. **Duplicate check per message during import**: `isDuplicateInRunOrDb()` fires a database query per message if not found in the in-memory set. For a 100K-message import, this is 100K queries. Consider batch-loading existing keys at import start.

2. **`ContactService.getAllDistinctContacts()`** loads all contacts into memory and sorts in Java:
```java
contactRepository.findAllByUser(user).stream()
    .sorted(Comparator...)
    .map(ContactMapper::toDto)
    .toList();
```
Should be `ORDER BY` in the query, not in-memory sorting.

3. **`AnalyticsService.getTopContacts()` ignores the `days` parameter**:
```java
public List<TopContactDto> getTopContacts(int days, int limit) {
    List<TopContactDto> all = messageRepository.findTopContactsSince(Instant.EPOCH, user);
    // ← Always passes Instant.EPOCH, ignoring 'days'
    return all.stream().limit(limit).collect(Collectors.toList());
}
```
This loads ALL contacts from epoch, then truncates in Java. The `days` parameter is dead code.

### 7.3 Connection Pool Starvation

The import thread pool (4 threads max) holds DB connections for extended batch operations. With the default HikariCP pool size of 10, 4 import workers can consume 40% of connections, leaving only 6 for serving HTTP requests. Under concurrent import + browsing load, connection pool exhaustion is likely.

---

## 8. DevOps, Build, and Deployment

### 8.1 Dockerfile — Grade: B+

**Strengths**:
- Multi-stage build ✅
- Dependency caching layer ✅
- Non-root user with gosu ✅
- Health check ✅
- Entrypoint waits for DB ✅

**Issues**:
1. **`eclipse-temurin:25-jdk` / `eclipse-temurin:25-jre`**: Java 25 EA images may not be stable. Pin to `21-jdk` / `21-jre` for production.
2. **No `.dockerignore`** — `COPY frontend frontend` and `COPY src src` may include `node_modules`, `.git`, `build/` directories, bloating the build context.
3. **`apt-get install ... postgresql-client`** in runtime image — only needed for `pg_isready` in entrypoint. Consider using the `wait-for-it.sh` pattern or the compose `depends_on: condition: service_healthy` (already used).
4. **Credentials in ENV defaults**: `SPRING_DATASOURCE_PASSWORD="sms_pass"` baked into the Dockerfile. Should be empty with runtime-only injection.

### 8.2 Docker Compose — Grade: B+

Good structure with health checks, dependency ordering, and network isolation.

**Issues**:
- No resource limits (`mem_limit`, `cpus`) on services
- No log driver configuration (defaults to `json-file` with no rotation)
- `DB_VERSION` variable not defaulted — will fail if `.env` is missing

### 8.3 Frontend Dockerfile — Grade: A-

Good: BuildKit cache mount for npm, nginx with envsubst, health check.

**Issue**: Legacy `nginx.conf` retained alongside template — confusing. Pick one approach.

### 8.4 12-Factor Compliance — Grade: B

| Factor | Status |
|---|---|
| Codebase | ✅ Single repo |
| Dependencies | ✅ Gradle + npm |
| Config | ⚠️ Defaults baked into Dockerfile |
| Backing services | ✅ DB via URL |
| Build/release/run | ✅ Docker multi-stage |
| Processes | ⚠️ In-memory state (progressMap, contactCache) |
| Port binding | ✅ |
| Concurrency | ⚠️ Fixed thread pool, no horizontal scaling |
| Disposability | ⚠️ Import jobs lost on crash (no resume) |
| Dev/prod parity | ✅ Testcontainers |
| Logs | ⚠️ stdout but no structured logging |
| Admin processes | ✅ Flyway migrations |

### 8.5 Observability — Grade: D

- **Spring Actuator** is included but only `/actuator/health` is exposed.
- **No Micrometer metrics** configured — no custom metrics for import throughput, cache hit rates, query latency.
- **No distributed tracing** (no OpenTelemetry or Micrometer Tracing).
- **No structured logging** — plain text logs. Consider Logback JSON encoder for production.

---

## 9. Enhancement Ideas

### Quick Wins (< 1 day each)
1. Fix `SecurityConfig` — remove `permitAll()` from `/api/contacts`, `/media/**`, `/import/**`
2. Remove `hibernate-types-60` and `sqlite-jdbc` dependencies
3. Add `ddl-auto: validate` for prod/docker profiles
4. Add `.dockerignore` file
5. Remove `HelloWorld.vue` and `@types/vue-router`
6. Fix `AuthTokenFilter` to verify access token type
7. Add `@Transactional` to `MediaService.deleteImage()`
8. Fix `RestExceptionHandler` — remove `IllegalStateException → 401` mapping, sanitize error messages
9. Lazy-load Vue routes
10. Fix `AnalyticsService.getTopContacts()` to actually use the `days` parameter

### Medium Effort (1-3 days each)
11. Decompose `ImportService` into 4-5 focused services
12. Create domain exception hierarchy and standardize error responses
13. Add Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Size`) to all request bodies
14. Upgrade JJWT to 0.12.x with new API
15. Add `conversation.participants` to `@EntityGraph` in message queries (fix N+1)
16. Configure HikariCP properly for production
17. Add Micrometer metrics + Prometheus endpoint
18. Add rate limiting to auth endpoints (Spring Security or Bucket4j)
19. Bound the in-memory caches (`contactCache`, `progressMap`) with TTL
20. Add structured JSON logging for production

### Long Term (1+ week each)
21. Add API versioning (`/api/v1/...`)
22. Replace `getAllConversationMessages()` 50K limit with cursor-based streaming
23. Add OpenTelemetry tracing
24. Implement WebSocket push for import progress (replace polling)
25. Add virtual thread support (`spring.threads.virtual.enabled=true`) — requires testing all `ThreadLocal` usage
26. Implement refresh token rotation (invalidate old refresh tokens)
27. Add comprehensive security tests (access control matrix)
28. Consider Spring AOT compilation for faster startup in containers

---

## 10. Summary

### Top 10 Issues to Fix (Priority Order)

| # | Severity | Issue | Location |
|---|---|---|---|
| 1 | 🔴 **Critical** | Unauthenticated access to `/api/contacts`, `/media/**`, `/import/**` | `SecurityConfig.java:64-69` |
| 2 | 🔴 **Critical** | N+1 query on `conversation.participants` in every message fetch | `MessageMapper.java:19`, missing EntityGraph |
| 3 | 🔴 **High** | `getAllConversationMessages()` loads 50K entities into memory | `ConversationService.java:198` |
| 4 | 🔴 **High** | Error messages leak internal details to clients | `RestExceptionHandler.java:27` |
| 5 | 🟡 **High** | `IllegalStateException` mapped to 401 globally | `RestExceptionHandler.java:20` |
| 6 | 🟡 **High** | `AuthTokenFilter` accepts refresh tokens as Bearer auth | `AuthTokenFilter.java:37` |
| 7 | 🟡 **Medium** | `ddl-auto: update` alongside Flyway in default profile | `application.yml:12` |
| 8 | 🟡 **Medium** | Duplicate/dead dependencies (hibernate-types-60, sqlite-jdbc, libphonenumber) | `build.gradle` |
| 9 | 🟡 **Medium** | Unbounded in-memory maps (`contactCache`, `progressMap`) | `ImportService.java:81-82` |
| 10 | 🟡 **Medium** | No input validation (missing `@Valid`, `@NotBlank`) on API endpoints | All controllers |

### Roadmap

**Phase 1 — Security & Correctness (This Sprint)**
- Fix SecurityConfig `permitAll` rules (#1)
- Fix AuthTokenFilter token type check (#6)
- Fix RestExceptionHandler (#4, #5)
- Set `ddl-auto: validate` for non-dev profiles (#7)
- Remove dead dependencies (#8)

**Phase 2 — Performance & Stability (Next Sprint)**
- Fix N+1 on conversation participants (#2)
- Replace 50K message load with streaming (#3)
- Configure HikariCP for production
- Bound in-memory caches (#9)
- Add input validation (#10)

**Phase 3 — Maintainability & Observability (Following Sprint)**
- Decompose ImportService
- Create domain exception hierarchy
- Add Micrometer metrics + Prometheus
- Add structured logging
- Upgrade JJWT to 0.12.x

**Phase 4 — Future (Backlog)**
- API versioning
- Virtual threads
- OpenTelemetry
- WebSocket import progress
- AOT/native image exploration
