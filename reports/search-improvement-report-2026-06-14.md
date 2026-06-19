# Search Improvement Report - 2026-06-14

## Summary

This report captures findings, completed Phase 0-lite changes, and recommended next steps for improving search in the SMS Archive project. The project has two search surfaces:

- **Message search**: semantic/vector search, keyword search, and hybrid search.
- **Data query search**: natural-language text-to-SQL used by the `Ask.vue` Data Query mode.

The initial direction was to prioritize **accuracy/relevance** and **UI/UX auditability** without requiring a local embeddings model or benchmark setup.

## Key Findings

### Semantic and Hybrid Search

- Semantic search used pgvector embeddings but vector queries did not filter by the active embedding `model_name`. If embeddings from multiple models existed, search could mix incompatible vector spaces.
- Contact-filtered semantic search only matched `sender_contact_id`, which misses outbound messages in conversations with that contact.
- Hybrid search passed contact/conversation filters to the semantic path but not consistently to the keyword path.
- Embedding stats counted embedding rows instead of distinct messages, which can overstate completeness when long messages are chunked.
- There was no lightweight diagnostics payload to show search source mix or ranking context.

### Text-to-SQL Data Query

- SQL was generated and executed, but the frontend only showed a small truncated SQL line.
- The UI did not expose generated vs executed SQL, generation time, execution time, or database errors in a structured way.
- If SQL failed, users had to re-prompt the LLM instead of editing and re-running the query.
- Raw database errors were already extracted in the backend, but not surfaced as rich UI state.
- The schema cheat-sheet was absent, making it harder to phrase reliable data questions.

### Frontend UX

- `Ask.vue` had useful mode tabs and prompt chips, but limited auditability for SQL.
- Result tables lacked sorting, CSV export, sticky headers, and empty-result handling.
- Search result diagnostics were hidden from users.

## Completed Changes

### Backend: SQL Metadata and Error Reporting

Updated `TextToSqlService` and `QaService` to return richer SQL lifecycle metadata:

- `generatedSql`
- `executedSql`
- `generationMs`
- `executionMs`
- `rowCount`
- structured SQL error data
- raw database error detail when available

SQL failures now return `analyticsData.type = "sql_error"` so the frontend can render a proper error state.

### Backend: Safe Edited SQL Re-run

Added a safe manual SQL execution path:

- New request DTO: `SqlRunRequest`
- New endpoint: `POST /api/qa/sql/run`
- New service method: `QaService.runSql(...)`
- New SQL path: `TextToSqlService.executeUserSql(...)`

Edited SQL uses the same validation guardrails:

- SELECT/WITH only
- no semicolon/multiple statements
- dangerous keywords blocked
- current-user scoping injected defensively when missing
- SQL length capped with `SQL_QUERY_MAX`

### Backend: Search Correctness Fixes

Updated semantic and hybrid search behavior:

- Vector queries now filter by active `model_name`.
- Contact semantic search now joins `conversation_contacts`, so outbound and inbound messages are included.
- Hybrid keyword search now respects `conversationId` and `contactId`.
- Semantic fallback to keyword preserves filters.
- Embedding stats now count distinct embedded messages for the current model.

### Backend: Search Diagnostics

Extended `UnifiedSearchResult` with a diagnostics map while preserving backward-compatible construction:

- search mode
- source counts
- dedup enabled flag
- minimum score threshold

### Frontend: SQL Auditability

Updated `frontend/src/views/Ask.vue`:

- SQL details accordion
- copy SQL button
- generated/executed SQL display
- SQL error panel with database details
- SQL edit textarea
- run edited SQL button
- regenerate button on SQL failures
- AI generation and DB execution timings

### Frontend: Table UX

Added Data Query table improvements:

- sticky headers
- fixed layout with truncation
- client-side column sorting
- CSV export
- empty result state
- row count display

### Frontend: Schema Help and Diagnostics

Added:

- Data Query schema cheat-sheet
- search diagnostics source-count badges in search results

### Local Eval Placeholder

Added a safe placeholder for future private/local search checks:

- `search-evals/private-search.example.json`
- `.gitignore` rule for `/search-evals/*.local.json`

This allows private archive prompts and expected IDs to stay out of git.

### Tests

Added deterministic unit tests for SQL validation behavior:

- placeholder user ID replacement
- missing user filter injection
- non-SELECT rejection
- prohibited keyword rejection
- multiple-statement rejection

Test file:

- `src/test/java/com/joshfouchey/smsarchive/service/TextToSqlServiceTest.java`

## Validation Status

Completed checks:

- Backend compile and test classes passed.
- `TextToSqlServiceTest` passed.
- Frontend production build passed.

Known environment limitation:

- Full backend test suite requires Docker/Testcontainers. It could not run in the current environment because Docker was unavailable at `/var/run/docker.sock`.

## Files Changed or Added

Main changed files:

- `.gitignore`
- `frontend/src/services/api.ts`
- `frontend/src/views/Ask.vue`
- `src/main/java/com/joshfouchey/smsarchive/controller/QaController.java`
- `src/main/java/com/joshfouchey/smsarchive/dto/UnifiedSearchResult.java`
- `src/main/java/com/joshfouchey/smsarchive/repository/MessageEmbeddingRepository.java`
- `src/main/java/com/joshfouchey/smsarchive/service/EmbeddingService.java`
- `src/main/java/com/joshfouchey/smsarchive/service/QaService.java`
- `src/main/java/com/joshfouchey/smsarchive/service/SemanticSearchService.java`
- `src/main/java/com/joshfouchey/smsarchive/service/TextToSqlService.java`
- `src/main/java/com/joshfouchey/smsarchive/service/UnifiedSearchService.java`
- `src/main/java/com/joshfouchey/smsarchive/util/InputLimits.java`

New files:

- `src/main/java/com/joshfouchey/smsarchive/dto/SqlRunRequest.java`
- `src/test/java/com/joshfouchey/smsarchive/service/TextToSqlServiceTest.java`
- `search-evals/private-search.example.json`

## Recommended Next Steps

### High-Value, Low-Risk

1. **Query history drawer**
   - Store recent question, mode, generated/executed SQL, row count, and timings in frontend state/local storage.
   - Helps compare repeated query attempts and debug changes.

2. **Saved prompt chips**
   - Let users save successful prompts.
   - Useful for repeated analytics questions.

3. **Feedback buttons on search hits**
   - Add 👍 / 👎 / "wrong result" controls.
   - Initially local-only or lightweight backend event capture.
   - Builds relevance signal without embeddings benchmark setup.

4. **Search diagnostics accordion**
   - Expand source-count badges into a compact diagnostics panel.
   - Show mode, source counts, dedup state, min score, and processing time.

5. **Move schema cheat-sheet to backend**
   - Replace hardcoded frontend schema with `/api/qa/schema`.
   - Prevents frontend drift when schema changes.

### Medium-Term Enhancements

1. **Chart toggle for simple SQL outputs**
   - Detect one categorical/date column plus one numeric column.
   - Render a simple lightweight bar/line chart.

2. **Smarter table formatting**
   - Date formatting for timestamp/date columns.
   - Percent formatting for `percent`, `pct`, `ratio`.
   - Numeric formatting for counts/averages.

3. **Client-side filter within results**
   - Filter SQL rows or search hits without another backend call.

4. **Private local eval runner**
   - Use `search-evals/*.local.json`.
   - SQL eval can run without embeddings.
   - Semantic eval can wait until embeddings are available.

### Relevance Tuning Later

Avoid tuning thresholds, RRF weights, or semantic scoring until there is at least minimal benchmark data or user feedback. Without measurement, ranking changes are guesswork.

Best future relevance workflow:

1. Collect real feedback or local expected results.
2. Run baseline.
3. Tune one variable at a time.
4. Compare Precision@5, Precision@10, MRR, and no-result behavior.

## Issues Found (2026-06-15 Follow-Up Review)

### Bugs

1. **`maxTokens` too low causes SQL truncation** (`TextToSqlService.java:114`) — ✅ **FIXED**
   - `maxTokens(512)` can truncate generated SQL mid-query, causing "Unterminated string literal" PostgreSQL errors.
   - **Fix:** Increased to `maxTokens(1024)`.

2. **Retry path skips FROM-clause validation** (`TextToSqlService.java:84-94`) — ✅ **FIXED**
   - When first SQL execution fails and retries, the regenerated SQL is not checked for the `FROM` clause before being sent to the database.
   - **Fix:** Added FROM-clause check in both initial and retry paths before executing SQL.

3. **`injectUserIdFilter` is fragile** (`TextToSqlService.java:187-208`) — ✅ **FIXED**
   - String-based injection finds `"where"` (lowercase) and inserts after 5 characters. If `WHERE` appears inside a string literal (e.g., `body ILIKE '%where%'`), it injects into the wrong position.
   - **Fix:** Replaced substring matching with `findSqlKeyword()` helper that checks for whitespace boundaries before keywords.

4. **`setQueryTimeout` thread-safety concern** (`TextToSqlService.java:231-247`) — ✅ **FIXED**
   - Mutates a shared `JdbcTemplate`'s query timeout via `setQueryTimeout()` with a `finally` reset. Under concurrent requests on the same thread (e.g., async dispatch), one request's timeout could interfere with another.
   - **Fix:** Refactored `executeSql` to use `JdbcTemplate.execute()` with per-statement `PreparedStatement.setQueryTimeout()` callback instead of mutating shared state.

5. **Empty error bodies on validation failure** (`QaController.java:29-30, 42-46`) — ✅ **FIXED**
   - Returns `ResponseEntity.badRequest().build()` with no body. Client receives a 400 with no explanation.
   - **Fix:** Updated `QaController` to return JSON error bodies for all validation failures (e.g., `{"error": "Question is required"}`).

6. **Silent question truncation** (`QaController.java:34`) — ✅ **FIXED**
   - Questions over `QA_QUESTION_MAX` (1000 chars) are silently truncated, potentially cutting mid-sentence with no indication to the user.
   - **Fix:** `QaController` now rejects questions exceeding `QA_QUESTION_MAX` with a 400 error instead of silently truncating.

7. **Duplicate `generatedSql` field in response** (`QaService.java:116-117`) — ✅ **FIXED**
   - Response data map contains both `"sql"` and `"generatedSql"` pointing to the same value.
   - **Fix:** Removed duplicate `sql` field from `analyticsResponse`, keeping only `generatedSql`.

### Security

8. **No table whitelist for user-supplied SQL** (`TextToSqlService.java:97-102`) — ✅ **FIXED**
   - `executeUserSql` (exposed via `POST /api/qa/sql/run`) allows querying any table including `information_schema`, `pg_catalog`, etc. No check that the query references only the allowed tables.
   - **Fix:** Added `ALLOWED_TABLES` set (`messages`, `contacts`, `conversations`, `conversation_contacts`, `message_parts`) and `validateTableWhitelist()` method. `executeUserSql` now rejects queries referencing disallowed tables.

9. **Regex-based SQL validation is bypassable** (`TextToSqlService.java:25-29`) — ✅ **FIXED**
   - `\b` word boundaries can be defeated with SQL comments (e.g., `DEL/**/ETE`). Does not block `pg_ls_dir`, `pg_read_file`, `COPY TO`, or other data exfiltration functions.
   - **Fix:** Added `stripComments()` method that removes `--`, `/* ... */`, and `#` comments before validation. Normalizes whitespace to prevent multi-line bypass. Expanded `DANGEROUS_SQL` pattern to block `pg_ls_dir`, `pg_read_file`, `pg_read_binary_file`, `pg_listening_channels`, `dblink`, `lo_export`, `lo_import`, `convert_to`, `pg_catalog`.

10. **DB error details leaked to client** (`TextToSqlService.java:238-244`) — ✅ **FIXED**
    - PostgreSQL error messages can reveal table names, column names, constraint names, and schema details. These flow into `sqlErrorData()` and are returned to the client.
    - **Fix:** `TextToSqlService` now passes `null` for `dbError` in `TextToSqlException`, keeping raw PostgreSQL details only in server logs.

### Missing Features

11. **No rate limiting on AI endpoints** (`QaController.java:27-50`) — ✅ **FIXED**
    - LLM calls are expensive and slow. No protection against rapid requests exhausting the LLM server.
    - **Fix:** Added `RateLimiter` utility class (in-memory token bucket). Configured as Spring bean with 10 requests/minute per user. Both `/ask` and `/sql/run` endpoints now check the rate limiter before processing, returning 429 Too Many Requests when exceeded.

12. **No timeout on LLM calls** (`TextToSqlService.java:108-121`) — ✅ **FIXED**
    - `chatModel.call()` has no timeout. If the LLM server hangs, the request blocks indefinitely.
    - **Fix:** LLM calls are now wrapped in `CompletableFuture` with configurable timeout (`smsarchive.ai.llm.timeout-seconds`, default 180s). Timeout throws `TextToSqlException` with clear error message.

13. **Missing user ID in error logs** (`QaService.java:68, 80, 98`) — ✅ **FIXED**
    - SQL error log messages do not include the user ID, making debugging difficult in a multi-tenant system.
    - **Fix:** Added `user.getId()` to all log statements in `QaService` (3 sites) and `TextToSqlService` (2 retry sites) for multi-tenant debugging.

### Code Quality

14. **`formatValue` does not handle Date/BigDecimal** (`TextToSqlService.java:299-308`) — ✅ **FIXED**
    - PostgreSQL can return `BigDecimal`, `java.sql.Timestamp`, `UUID`, etc. Dates produce ISO format strings which may be confusing. BigDecimal may produce scientific notation.
    - **Fix:** Added `BigDecimal` handling (uses `toPlainString()` to avoid scientific notation) and `Date` handling (formats as readable timestamp via `java.sql.Timestamp.valueOf()`).

15. **`DATA_QUESTION_PATTERN` regex may match false positives** (`QaService.java:25-34`) — ✅ **FIXED**
    - e.g., "which month" matches, but so would "which" in any context. Could trigger text-to-SQL for questions meant for simple search.
    - **Fix:** Removed bare `\d{4}` pattern (matched any year in any context). Added `\b` word boundaries to `count`, `total`, `average`. Changed `least` to `least\s+(recent|active)`. Added `\b` boundaries to `since \d{4}` and `in \d{4}`.

## Current Best Next Task

All issues from this report have been addressed (2026-06-18). Next recommended work:

1. **Query history + saved prompts + feedback buttons** — low risk, useful immediately, creates signals for future relevance improvements.
2. **Search diagnostics accordion** — expand source-count badges into a compact diagnostics panel.
3. **Chart toggle for simple SQL outputs** — render bar/line charts for one categorical + one numeric column.

See "Recommended Next Steps" section above for full details.

## Files Changed (2026-06-18 Follow-Up Fixes)

Additional files changed by follow-up fixes:

- `src/main/java/com/joshfouchey/smsarchive/service/TextToSqlService.java` — SQL validation hardening, comment stripping, table whitelist, LLM timeout, Date/BigDecimal formatting, user ID logging
- `src/main/java/com/joshfouchey/smsarchive/service/QaService.java` — user ID logging, refined DATA_QUESTION_PATTERN
- `src/main/java/com/joshfouchey/smsarchive/controller/QaController.java` — JSON error bodies, length validation rejection, rate limiting
- `src/main/java/com/joshfouchey/smsarchive/config/AsyncConfig.java` — aiRateLimiter bean
- `src/main/java/com/joshfouchey/smsarchive/util/RateLimiter.java` — new in-memory token bucket rate limiter (new file)
