# Performance Review Plan
Generated: 2026-06-27T10:45:47-04:00
Scope: Entire Project
Context: Single-user app on the owner's local network. "Scale" here means *one user with a large personal archive* (potentially hundreds of thousands of messages, multi-GB XML imports), not many concurrent users. Findings are weighted accordingly: big-thread rendering, import throughput, and search latency matter a lot; multi-tenant concurrency/throughput matters little.

## Executive Summary

Static analysis (no runtime profiling) across backend persistence, the AI/search subsystem, and the Vue frontend. The codebase is well-indexed and already uses sensible batching defaults, but there are a handful of **high-impact, mostly low-risk** performance issues concentrated in three hot paths:

1. **Large-thread handling (frontend + backend).** Opening a conversation can auto-load **up to ~5,000 message components with no virtualization** (`Messages.vue`), and the backend `getAllConversationMessages` materializes **up to 50,000 fully-hydrated entities in memory** with a 50K-parameter `IN (...)` load. This is the dominant user-facing latency/memory risk for a personal archive.
2. **Import throughput.** Inserts can't be JDBC-batched because all entities use `GenerationType.IDENTITY` (confirmed on Message, MessagePart, Conversation, Contact), duplicate detection does a DB `exists` query **per message**, and the Hibernate session is **never cleared** during a streaming import → first-level-cache growth on multi-GB files.
3. **Search & AI latency.** The fuzzy `similarity()` predicate is **not backed by the trigram GIN index** (so it can force sequential scans), HNSW `ef_search` is left at the default 40 while the code fetches 60 candidates (silent recall loss), the query-embedding call has **no timeout** (up to ~241s hang on a request thread), and a single 1-thread AI executor makes interactive Ask queue behind background embedding jobs.

None of these require architectural change. The biggest wins are: virtualize the message list, paginate/stream the 50K load, fix the trigram predicate, tune `ef_search`, split the AI executor, and make imports batch + clear the session.

**Already done this session:** `default_batch_fetch_size` 10→50 (batches lazy participant/conversation loads), and `show-sql` turned off in the base profile (removes per-statement logging overhead).

## Performance Bottlenecks

### Critical Issues (Severe Impact)

- [ ] **Frontend: message thread renders the entire array, no virtualization** — `Messages.vue:396-406` (`v-for` over all `messages`). `loadAllMessagesInBackground()` (`Messages.vue:1120-1159`) auto-loads the *full* thread for anything 200–5,000 messages, mounting thousands of `MessageBubble` components (~50k–100k DOM nodes for a 5K thread). No windowing library present. **Single largest perf liability.**
- [ ] **Backend: `getAllConversationMessages` loads up to 50,000 entities all-in-memory** — `ConversationService.java:195-230`. 50K `PageRequest` (line 205), then a `findByMessageIds` `IN (...)` with up to **50,000 bind parameters** (risks Postgres' 65,535 param limit and terrible planning), then a 50K-entry map mutation. Result is `@Cacheable` (line 195) into an 800-weight Caffeine cache — one large thread can dominate heap. Also runs **without an explicit transaction** (the `@Transactional(readOnly=true)` at line 165 belongs to a different method), mutating `parts` on effectively detached entities.
- [ ] **Import: JDBC insert batching is silently disabled** — all entities use `@GeneratedValue(strategy = GenerationType.IDENTITY)` (`Message.java:29`, `MessagePart.java:18`, `Conversation.java:20`, `Contact.java:16`). Hibernate **cannot batch IDENTITY inserts** (needs the generated key per row), so `spring.jpa.properties.hibernate.jdbc.batch_size: 20` (`application.yml:29`) is dead on the hot insert path — every insert is its own round-trip.
- [ ] **Import: Hibernate session never cleared during streaming import** — `ImportService.flushStreamingBatch` (`ImportService.java:363-368`) does `saveAll(batch); batch.clear()` but never `entityManager.flush()/clear()`. Every Message/Part/Contact/Conversation accumulates in the persistence context for the whole import; dirty-checking re-scans an ever-growing context → memory + GC thrash on large (multi-GB) files.

### High Priority Issues

- [ ] **Fuzzy `similarity()` search predicate is not index-backed** — trigram GIN exists (`messages_body_trgm_idx ON messages USING gin (body gin_trgm_ops)`, `V8:5`), but the search queries filter `similarity(COALESCE(m.body,''), :text) > 0.25` (`MessageRepository.java:99,119,135,171`). `similarity(col,const) > k` as a function call is **not** an indexable operator, and the `COALESCE` wrapper further defeats the index (index is on raw `body`). The `OR similarity(...)` forces evaluation across candidate rows, undermining the FTS GIN index too. To use the index: `m.body % :text` (and index `coalesce(body,'')` or drop the COALESCE). The `ORDER BY` also recomputes two `to_tsvector`s + `ts_rank` + `similarity` per row (lines 101-111).
- [ ] **HNSW `ef_search` never tuned (recall cliff)** — index built `m=24, ef_construction=256` (`V12:11`, `V13:22`) but `ef_search` stays at pgvector default **40**, while unified search fetches `k*3 = 60` candidates (`UnifiedSearchService.java:67`). `ef_search (40) < fetchK (60)` silently degrades recall. No `SET LOCAL hnsw.ef_search` anywhere. Fix: set `ef_search >= fetchK` per query.
- [ ] **Query-embedding call has no timeout** — `EmbeddingService.embedQuery` (`:396-402`) calls the model with no timeout wrapper; only bound is the RestClient **120s** read timeout (`RestClientConfig.java:21`). With `embedQueryWithRetry` (2 attempts + 1s backoff, `SemanticSearchService.java:139-156`) a hung embedding server blocks a **Tomcat request thread up to ~241s**. 120s is far too long for a single short-string embed (<1s expected).
- [ ] **Single 1-thread AI executor serializes batch + interactive AI** — `aiTaskExecutor` core=1/max=1/queue=5 (`AsyncConfig.java:23-32`) is shared by batch embedding (`EmbeddingService.java:152`) and interactive Text-to-SQL (`TextToSqlService.java:167`). During a batch embed, interactive Ask queues (6th request rejected) and usually hits its 180s timeout — even though the **chat server (:8080) is idle and independent of the embedding server (:8081)**. Split into interactive vs batch executors.
- [ ] **Import: duplicate detection is per-message + O(n²) in-batch** — `DuplicateDetector.isDuplicateInBatch` linearly scans the whole batch per message (`DuplicateDetector.java:39`; ~125K comparisons per 500-msg window), and `isDuplicate` issues a DB `exists` query per message (`:86,95` → `MessageRepository.java:237`), whose `lower(trim(body))` predicate isn't index-covered.
- [ ] **Embedding persistence + context building are per-row** — upserts run one native `@Modifying` query per row in a loop (`EmbeddingService.java:271-284`), not `jdbcTemplate.batchUpdate`; and `buildContextualEmbeddingText` does a `queryForList` **per chunk per message** (`EmbeddingService.java:297-316,249`) — an N+1 inside the embedding batch.
- [ ] **Frontend: no request cancellation; stale responses clobber state** — no `AbortController`/axios `signal` anywhere. A slow `/messages/all` for a previous conversation can resolve after a newer selection and overwrite `messages.value` with the wrong thread (`Messages.vue:1146`).

### Medium Priority Issues

- [ ] **`parseReaction` re-runs per bubble per render** — `MessageBubble.vue:6,197-199`: `isReactionMessage` is a plain template function call (not `computed`), so 4 regex replaces + a match run on every re-render of every bubble; with the full-thread render (C1) that's thousands of regex passes per render cycle. Reaction parsing is also duplicated in the parent (`Messages.vue:687-732`).
- [ ] **Conversation opens with 3 sequential round-trips + double download** — count, then 200-msg page, then full `/messages/all` that re-downloads the first 200 (`Messages.vue:1094-1143`).
- [ ] **chart.js + marked eager in the default route** — statically imported in `Ask.vue:499-510`; Ask is the default route (`/`), so cold start parses chart.js even though charts appear only after a 2-column DATA result. Use `defineAsyncComponent`.
- [ ] **Analytics ignores its date window** — `getTopContacts`/`getMessagesPerDay` pass `Instant.EPOCH` (`AnalyticsService.java:47-55`), aggregating the **entire** table (joined through participants) and applying `limit` in Java; masked by `@Cacheable` but recomputed cold after every import (cache evicted on import).
- [ ] **Semantic search double-loads messages** — vector query already does `SELECT m.*` (`MessageEmbeddingRepository.java:16`) but the result is discarded and `messageRepository.findAllById(messageIds)` re-loads the same rows (`SemanticSearchService.java:104`). Over-fetches 3× (`k*3`) then returns `k`.
- [ ] **No query-embedding cache** — repeated/paginated/identical queries re-embed every time (`SemanticSearchService.java:67`, re-embedded again in hybrid path `:281`).
- [ ] **`searchWithinConversation` returns full entities (incl. JSONB) only to extract IDs** — `ConversationService.java:170-193`, unbounded (no SQL LIMIT, `MessageRepository.java:165-187`).
- [ ] **JSONB over-fetch** — native search `SELECT m.*` deserializes `media`+`metadata` per row (`MessageRepository.java:84,93,128,166`); conversation-list lateral pulls full `media` JSONB only to compute an `isEmpty()` boolean (`ConversationService.java:89-91`).
- [ ] **`deleteConversationById` loads all messages+parts to delete them** — `ConversationService.java:548-562` hydrates everything via EntityGraph then per-entity deletes; DB already has `ON DELETE CASCADE` (`V1:70`), so a bulk `DELETE FROM messages WHERE conversation_id=?` would be 1–2 statements. (Now `@Transactional` as of this session.)

### Low Priority Issues

- [ ] **`(conversation_id, timestamp)` ordering** — the hot "messages for a conversation, ordered by timestamp" pattern **is already served** by the leading columns of `idx_messages_dedupe_prefix (conversation_id, timestamp, ...)` (`V1:103`). A dedicated narrower 2-column index would be marginally smaller / enable index-only scans, but this is a minor optimization, **not** a missing-index gap. (Corrected down from an initial "High".)
- [ ] **`messageImageCache` never cleared on conversation switch** — slow memory growth across conversations (`Messages.vue:568`, not cleared in `selectConversation` `:1045`).
- [ ] **Coarse cache eviction (`allEntries=true`)** — every mutation flushes whole caches for all data (`ConversationService.java:547`, `ImportService.java:117,150`, `ContactService.java:54,73`), forcing cold rebuilds (incl. the full analytics aggregates).
- [ ] **Contacts re-fetched per view** — Gallery and Contacts each call `getDistinctContacts()` independently (`Gallery.vue:363`, `Contacts.vue:283`); Pinia exists but only holds auth.
- [ ] **Client-side filters recompute per keystroke** — `filteredConversations`/`filteredContacts` (`Messages.vue:739-759`, `Contacts.vue:291-297`); fine for hundreds, degrades for large lists; cheap to debounce.
- [ ] **Embedding `model_name` config fragility** — code default `qwen3-embedding:0.6b` (colon, `EmbeddingService.java:52`) vs yaml `qwen3-embedding-0.6b` (dash); a mismatch makes searches silently return zero rows (queries filter `me.model_name = :modelName`).

## Performance Metrics

### Current State
- **Not measured** — this review is static analysis. No profiler, APM, or load test was run; `/actuator/prometheus` metrics are not currently exposed (observability gap noted in the architecture review).
- Qualitative hot spots (estimated): opening a large thread (multi-hundred-ms to seconds, render-bound on the client + 50K hydration on the server); search latency dominated by non-indexed `similarity()` scans on large message tables; import throughput bounded by per-row inserts + per-message dup queries; interactive Ask latency spikes to timeout when a background embed is running.

### Target State
- Large-thread open: bounded, constant-ish regardless of thread size (windowed render + paginated/streamed fetch).
- Search: index-backed (trigram + FTS + HNSW with `ef_search>=fetchK`); sub-second on a personal-scale archive.
- Import: JDBC-batched inserts + periodic session clear → linear, bounded-memory throughput on multi-GB files.
- Interactive Ask: unaffected by background embedding (separate executor).
- Establish a baseline by capturing a few timings before/after each change (see Testing Plan).

## Optimization Plan

### Quick Wins (1–3 days)
1. **Tune HNSW `ef_search` per query** — `SET LOCAL hnsw.ef_search = <fetchK>` before the vector query. One statement; fixes silent recall loss. (Low risk)
2. **Add a timeout to `embedQuery`** and drop the search-path RestClient read timeout from 120s to ~5–10s. Prevents request-thread hangs. (Low risk)
3. **Lazy-load chart.js/marked** in `Ask.vue` via `defineAsyncComponent` so the default route's cold start drops them. (Low risk)
4. **Memoize `isReactionMessage`** (make it `computed`/precomputed) to stop per-render regex on every bubble. (Low risk)
5. **Fix the trigram predicate** — switch `similarity(coalesce(body,''),:t) > k` to `body % :t` and add an index on `coalesce(body,'')` (or drop COALESCE). Makes fuzzy search index-backed. (Low–medium risk; verify with `EXPLAIN`)
6. **Honor the analytics date window** — pass the real `since` instead of `Instant.EPOCH`; push `LIMIT` into SQL. (Low risk)
7. **Add a small LRU cache for query embeddings** keyed on normalized query string. (Low risk)

### Major Optimizations (1–2 weeks)
1. **Virtualize the message thread** (`vue-virtual-scroller` or `@tanstack/vue-virtual`) and stop auto-loading the whole thread; this is the top user-facing win. (Medium risk — touches the most complex view)
2. **Paginate/stream `getAllConversationMessages`** — remove the 50K all-in-memory load; chunk `findByMessageIds` IN-lists (e.g. 1–2K per batch); reconsider caching whole large conversations. (Medium risk)
3. **Split the AI executor** into `aiInteractiveExecutor` + `aiBatchExecutor` so Ask doesn't queue behind embeds. **Caveat:** only if the chat/embedding servers don't contend for the same GPU VRAM — owner's hardware call. (Medium risk)
4. **Batch the embedding writes + context fetch** — `jdbcTemplate.batchUpdate` for upserts; pre-fetch contextual rows for the whole batch instead of per-chunk. (Medium risk)
5. **Add `AbortController`** to conversation/message/search fetches to kill stale-response clobbering. (Low–medium risk)

### Architectural Changes (2–4 weeks)
1. **Switch IDs from `IDENTITY` to a pooled `SEQUENCE`** generator so `jdbc.batch_size` actually batches inserts — large import-throughput win, but it's a schema + entity change requiring a Flyway migration and careful testing. (Higher risk)
2. **Bulk DML for delete/merge** — replace entity-by-entity delete in `deleteConversationById` and `ContactService.mergeContacts` with set-based `UPDATE`/`DELETE` (DB cascade already exists). (Medium risk)
3. **Expose Micrometer/Prometheus metrics** to replace guesswork with real numbers (already on the classpath). (Low risk, enables everything else)

## Implementation Strategy

### Phase 1: Low-hanging Fruit (search + AI correctness)
- `ef_search` tuning, `embedQuery` timeout, query-embedding cache, analytics date window, trigram predicate fix. Mostly isolated, verifiable with `EXPLAIN` and a few timings.

### Phase 2: The big-thread path
- Frontend virtualization + drop auto-full-load; backend paginate/stream the 50K load and chunk IN-lists; add `AbortController`. This is the highest-value user-facing phase.

### Phase 3: Import throughput
- Session flush/clear per batch (quick), then `IDENTITY`→`SEQUENCE` migration and batched embedding writes. Validate against the existing import integration tests.

### Phase 4: Observability + polish
- Micrometer metrics, per-key cache eviction, executor split, JSONB over-fetch trimming, shared contacts cache via Pinia.

## Performance Testing Plan
- **Baseline first:** with the `local` profile (`show-sql` on), capture query counts + timings for: open a large conversation, run a keyword search, run a semantic/hybrid Ask, import a representative XML. Save the numbers.
- **Per change:** re-capture the same scenario; for DB changes run `EXPLAIN (ANALYZE, BUFFERS)` on the affected query before/after to confirm index usage.
- **Frontend:** Chrome DevTools Performance trace on opening the largest thread (node count, scripting time) before/after virtualization.
- **Import:** time a fixed large file and watch heap (the session-clear + batching changes should flatten memory and cut wall-clock).
- Optionally enable `/actuator/prometheus` to track search latency and embedding job duration over time.

## Risk Assessment

### Safe Optimizations
- `ef_search` tuning, `embedQuery` timeout, query-embedding cache, analytics date window, chart.js lazy-load, `isReactionMessage` memoization, Micrometer metrics, shared contacts cache.

### Moderate Risk
- Trigram predicate rewrite (verify with EXPLAIN), message virtualization (complex view), paginate/stream 50K load, executor split (GPU contention caveat), batched embedding writes, AbortController, bulk delete/merge.

### High Risk
- `IDENTITY`→`SEQUENCE` ID migration (schema + entity change; needs a Flyway migration and full import-test pass).

## Recommendations
- **Profiling:** enable Spring Boot Actuator + Micrometer (already on classpath); use `EXPLAIN (ANALYZE, BUFFERS)` for query work and Chrome DevTools Performance for the frontend.
- **Tools:** `vue-virtual-scroller`/`@tanstack/vue-virtual` for the list; `pg_stat_statements` to find the real top queries empirically.
- **Process:** measure before/after each change — several findings here (e.g. the composite-index correction) show static analysis can over- or under-state impact; let `EXPLAIN` and timings decide.

## Estimated Impact
- **Quick wins:** noticeable search-latency and AI-responsiveness improvement; eliminates request-thread hangs and silent recall loss. (Rough order: 20–40% on affected paths.)
- **Major optimizations:** large-thread open becomes bounded/constant instead of scaling with thread size — the biggest perceived speedup; AI interactivity no longer blocked by background jobs.
- **Full plan:** import throughput and memory materially better on big files; search reliably index-backed. (These are estimates — establish real baselines to confirm.)

## Effort Estimate
- Quick wins: ~2–3 days
- Major optimizations: ~1.5–2 weeks
- Architectural (ID migration, bulk DML, observability): ~1–2 weeks
- Total: ~4–6 weeks of focused effort, fully parallelizable by phase. The first two phases deliver the bulk of the perceived performance gain.
