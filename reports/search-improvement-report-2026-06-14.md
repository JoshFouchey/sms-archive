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

## Current Best Next Task

Implement **query history + saved prompts + feedback buttons**. This is low risk, useful immediately, and creates signals for future relevance improvements.
