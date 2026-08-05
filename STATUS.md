# FinPilot Backend — Phase 1 & Phase 2 Status

## Phase 1 — Normalized Transaction Contract: DONE

See the schema/dedup design rationale below (unchanged from Phase 1 delivery).

## Phase 2 — CSV Import Engine: DONE

### What this phase delivers
- `importengine/csv/` — dependency-free parsing/normalization layer:
  `CsvParser` (RFC4180-compliant), `DateParser`, `AmountParser`,
  `CsvColumnMapping` (auto-detects both the signed-amount and
  separate-debit/credit CSV conventions), `CsvRowNormalizer` (ties it
  together, one bad row never aborts the whole batch).
- `importengine/service/CsvImportService.java` — Spring `@Service`
  orchestrating batch-level dedup (file hash) → parse → normalize →
  row-level dedup (fingerprint) → persist, wrapped in `@Transactional`.
- `importengine/api/CsvImportController.java` — `POST /api/v1/imports/csv`
  multipart upload endpoint.
- `FinPilotApplication.java` + `health/HealthController.java` — the app is
  now runnable end-to-end (`GET /health`) once `mvn spring-boot:run` works
  in an environment with Maven Central access.

### What's actually been verified vs. what hasn't

**Verified for real, in this environment (50/50 tests passing):**
- `FingerprintServiceTest` (10 tests) — compiled/run standalone with `javac`/`java`.
- `CsvImportPipelineTest` (33 tests) — the entire parse→normalize pipeline,
  compiled/run standalone: quoted-field/BOM/multiline CSV edge cases, both
  amount conventions, date/amount parsing edge cases, partial-batch-failure
  handling.
- `CsvImportIntegrationHarness` (7 tests) — a genuine **end-to-end** run
  against a live Postgres 16 instance, using the *actual* `CsvParser` /
  `CsvRowNormalizer` / `FingerprintService` classes (not a reimplementation)
  via plain JDBC instead of Spring Data JPA. Proves: a real 7-row CSV
  imports 6 valid rows and correctly rejects 1 bad-date row; re-uploading
  the identical file is rejected at the batch level with the transaction
  count unchanged; two genuinely identical same-day/same-amount
  transactions both survive via occurrence-index disambiguation.

**Not verified — same caveat as Phase 1, flagged rather than glossed over:**
- `CsvImportService` and `CsvImportController` themselves (the Spring
  `@Service`/`@RestController` layer, including `@Transactional` behavior
  and multipart file handling) have NOT been compiled or run — this
  sandbox's network allowlist excludes Maven Central. They're written to
  call the exact same, already-verified `CsvParser` / `CsvRowNormalizer` /
  `FingerprintService` classes the integration harness proved correct, so
  the *logic* is proven; the *Spring wiring* (bean injection, transaction
  boundaries, multipart parsing) is not. Run `mvn clean verify` yourself to
  close this gap — see command below.

### How to verify the rest yourself

```bash
# from finpilot-backend/
docker run -d --name finpilot-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=finpilot_dev -p 5432:5432 postgres:16
mvn clean verify
mvn spring-boot:run
# then, in another terminal:
curl http://localhost:8080/health
curl -F "userId=<some-uuid-after-you-insert-a-test-user>" \
     -F "file=@src/test/resources/sample-csv/sample_statement.csv" \
     http://localhost:8080/api/v1/imports/csv
```

## Design decisions this phase adds

- **Two CSV conventions supported**: signed `Amount` column, and separate
  `Debit`/`Credit` columns (very common in Indian bank exports) — column
  mapping is auto-detected from header aliases, with both debit-and-credit
  populated on one row treated as an ambiguous error rather than guessed.
- **Merchant normalization strips trailing reference numbers** (`SWIGGY*ORDER
  88271` → `SWIGGY ORDER`) before it enters the fingerprint's occurrence-grouping
  key, so repeat orders from the same merchant on the same day are
  correctly recognized as "possibly duplicate, disambiguate by occurrence"
  rather than as unrelated rows.
- **Partial failure is a first-class outcome**: a CSV with 1 bad row out of
  500 still imports the other 499 — each row is validated independently
  and failures are collected with row number + reason, never thrown as an
  exception that aborts the batch.
- **CSV import is synchronous** (unlike PDF, which the roadmap correctly
  flags as needing an async job queue) — but `CsvImportService.importCsv`
  is written as a self-contained unit with no HTTP-cycle dependency
  specifically so it can move behind a queue later without restructuring,
  if file sizes ever risk request timeouts.
- **`userId` is a request parameter, not yet from an auth context** — there's
  no auth layer yet (a later phase). Every caller should expect this
  endpoint's signature to change once JWT auth exists.

## Next phase

Phase 3 — Transaction Processing Pipeline (wiring categorization: send
newly-imported, uncategorized transactions to the AI service's
`/api/v1/categorize/batch` endpoint, apply results via
`Transaction.applyCategorization()`, and refresh dashboard cache) — or
Phase 2b — PDF Import Engine (async, OCR-based) if you'd rather finish all
import connectors before moving to categorization wiring. Your call.

