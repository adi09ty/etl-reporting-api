# ETL & Reporting API

A Spring Boot service that runs two data pipelines and serves reports on the result:

1. **Orders import:** picks up CSV files from an S3-compatible bucket, validates every row, loads the good rows into MySQL, keeps the bad rows (with the reason) for review, and moves each file to `processed/` or `failed/`.
2. **Exchange-rate refresh:** calls a public REST API for the latest exchange rates and stores them, so reports can convert every order into one currency.

Both jobs run on a schedule or on demand, retry transient failures, record every run, and raise an alert when something fails. Everything uses synthetic sample data and a public API; there is no company data in this project.

## Features
- **S3 ingestion** of CSV files through the AWS SDK for Java v2 (works with AWS S3 or any S3-compatible store; a local folder is used when you run without Docker)
- **REST ingestion** of exchange rates from the public Frankfurter API (European Central Bank reference rates, no API key)
- **Row-level validation** with clear reasons: required fields, positive amounts with at most 2 decimals, real ISO currency codes, dates that are not in the future, allowed statuses, no duplicate order ids within a file
- **Rejected rows are kept**, not dropped: each is stored with its file, row number, raw text and reason, and is available through the API
- **Idempotent and crash-safe loading:** each file is loaded in one transaction and moved only after the commit; re-running or re-delivering a file never duplicates data; orders are matched by `order_id` and updated if they already exist
- **Retries with exponential back-off** for S3 and REST calls
- **Failure alerts** on failed or partial runs: always logged (logger `alerts`), optionally posted to a Slack-style webhook
- **Scheduling** (`@Scheduled`) plus manual triggers; two runs of the same job never overlap
- **Run history:** every execution is stored with status, row counts and error text
- **Reports:** daily sales and sales by category, converted to USD with the stored rates
- Runs with zero setup (in-memory H2 + a local folder) or as a full stack with MySQL and an S3-compatible store via Docker Compose

## Tech stack
Java 21, Spring Boot 4 (Web MVC, Data JPA, Actuator, scheduling, `RestClient`), AWS SDK for Java v2 (S3), Apache Commons CSV, MySQL 8.4 (H2 for quick runs and tests), JUnit 5, Mockito, MockMvc, Maven, Docker.

## Architecture
```
          S3 bucket                               Frankfurter REST API
   incoming/ processed/ failed/                            |
            |                                              |
     OrdersImportJob  ----retry----+            RatesRefreshJob ----retry----+
            |                      |                       |                  |
     OrderFileImporter       ObjectStorage            RatesClient       RatesWriter
   (validate, upsert, one     (S3 or local folder)                     (upsert in one tx)
    transaction per file)
            |                                                          |
            +------------------ MySQL ---------------------------------+
   orders | rejected_rows | processed_files | exchange_rates | etl_runs
                                     |
                              ReportService -> /api/reports/*       EtlController -> /api/etl/*
   Failures -> AlertService -> log + optional webhook
```
- `storage`: `ObjectStorage` interface with `S3ObjectStorage` and `LocalFileStorage`
- `etl`: the orders import (job, per-file importer, row validator), retry helper, scheduler
- `rates`: REST client, rates job and writer
- `alert`: alert channels (log, webhook) behind one service
- `report`: report queries and currency conversion
- `domain`, `repository`, `dto`, `web`, `exception`, `config`: the usual layers

### How a file is processed
1. List `incoming/*.csv` (retried).
2. Skip it if this exact file (key + content hash) was already loaded; only move it.
3. Parse and validate each row. Valid rows are upserted by `order_id` in chunks; invalid rows are stored as rejected rows. The rows, the rejected rows and the "this file was processed" record are saved in **one transaction**.
4. Only after the commit, move the file to `processed/<run id>-<name>`. If the load fails, the transaction rolls back and the file goes to `failed/`.
5. A failed move is only logged: the data is safe, and the next run recognises the file as loaded and moves it.

Run status: `SUCCEEDED` (no file failed, rejected rows are reported but are not a failure), `PARTIAL` (some files failed), `FAILED` (nothing could be loaded). `PARTIAL` and `FAILED` raise an alert.

### Input format
```
order_id,customer_id,category,amount,currency,order_date,status
ORD-1001,C007,Books,499.00,INR,2026-10-01,COMPLETED
```
`status` is `COMPLETED`, `CANCELLED` or `REFUNDED`. Reports count only `COMPLETED` orders. Sample files are in [`sample-data/incoming`](sample-data/incoming): 44 rows, of which 6 are deliberately invalid (negative amount, unknown currency, missing customer, text instead of a number, future date, duplicate id).

## Run it

### Quick start (no database, no S3)
Requires Java 21+.
```bash
mkdir -p data/incoming
cp sample-data/incoming/*.csv data/incoming/
./mvnw spring-boot:run
```
On Windows use `.\mvnw.cmd spring-boot:run` and copy the files with Explorer or `Copy-Item`. The app is at http://localhost:8080. The scheduler starts 15 seconds after startup; you can also trigger the jobs yourself (below). The default profile uses H2 and the folder `./data` as the bucket. The rates job needs internet access to reach the public API.

### Docker Compose (MySQL + S3-compatible store)
1. Create your environment file and set your own values:
   ```bash
   cp .env.example .env
   ```
2. Start the stack:
   ```bash
   docker compose up --build
   ```
This starts MySQL, an S3-compatible store ([Adobe S3Mock](https://github.com/adobe/S3Mock), which accepts any credentials) and a one-off container that uploads the sample files to `s3://etl-data/incoming/`. MySQL is on `127.0.0.1:3310` and the S3 endpoint on `127.0.0.1:9090`.

To use AWS or another store, run with the `mysql` profile and set `DB_URL`, `DB_USER`, `DB_PASSWORD`, `S3_ENDPOINT`, `S3_BUCKET`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` (and optionally `ALERT_WEBHOOK_URL`).

## API
### Run a job now
```bash
curl -X POST http://localhost:8080/api/etl/jobs/orders-import/run
curl -X POST http://localhost:8080/api/etl/jobs/rates-refresh/run
```
Each returns the run summary (a failed run is still HTTP 200 with `"status":"FAILED"`; `409` if that job is already running):
```json
{
  "id": 3, "jobName": "ORDERS_IMPORT", "trigger": "MANUAL", "status": "SUCCEEDED",
  "startedAt": "2026-11-04T09:00:00", "finishedAt": "2026-11-04T09:00:01",
  "filesProcessed": 3, "filesFailed": 0,
  "rowsRead": 44, "rowsInserted": 38, "rowsUpdated": 0, "rowsRejected": 6,
  "errorMessage": null
}
```
### Run history and rejected rows
```bash
curl "http://localhost:8080/api/etl/runs?page=0&size=20"          # newest first
curl http://localhost:8080/api/etl/runs/3
curl "http://localhost:8080/api/etl/runs/3/rejected-rows"
```
### Reports
Dates look like `2026-10-01`. Without parameters, the last 30 days up to today (UTC); at most 366 days.
```bash
curl "http://localhost:8080/api/reports/daily-sales?from=2026-10-01&to=2026-10-03"
curl "http://localhost:8080/api/reports/by-category?from=2026-10-01&to=2026-10-03"
```
```json
{
  "from": "2026-10-01", "to": "2026-10-03", "currency": "USD", "ratesAsOf": "2026-11-03",
  "totalOrders": 31, "total": 1234.56, "ordersWithoutRate": 0,
  "days": [
    { "date": "2026-10-01", "orders": 10, "total": 410.20, "byCurrency": { "INR": 9500.00, "USD": 120.00 } }
  ]
}
```
`total` is converted with the latest stored rates. Orders in a currency that has no stored rate are counted in `ordersWithoutRate` and left out of the totals. `days[].orders` counts all completed orders of the day.

Health check: `GET /actuator/health`.

### Status codes
| Code | When |
|---|---|
| `200` | Success, including job runs that ended `FAILED` (see the body) |
| `400` | Bad date, `from` after `to`, or a range over 366 days |
| `404` | Unknown run id |
| `409` | The job is already running |

## Configuration
| Property | Default | Meaning |
|---|---|---|
| `app.storage.type` | `local` (`s3` in the `mysql` profile) | Where input files live |
| `app.storage.local-dir` | `./data` | Folder used as the bucket when type is `local` |
| `app.rates.base-url` | `https://api.frankfurter.dev` | Exchange-rate API |
| `app.rates.base-currency` | `USD` | Currency reports are converted to |
| `app.etl.retry-attempts` / `retry-initial-delay` | `3` / `500ms` | Retries; the wait doubles each time |
| `app.etl.chunk-size` | `500` | Rows per database lookup when loading |
| `app.etl.scheduling.enabled` | `true` | Turn the timers on or off |
| `app.etl.orders-import-interval` / `rates-refresh-interval` | `PT1M` / `PT6H` | Time between runs (ISO-8601) |
| `app.alerts.webhook-url` | empty | Slack-style webhook for alerts; empty means log only |

## Tests
```bash
./mvnw test
```
79 tests:
- **Unit:** every validation rule, retry and back-off behaviour, run-status logic, the overlap guard (a second run is refused while one is running), the local file store (including path-traversal protection), the S3 store with a mocked client (paging, copy-then-delete), the REST client and the webhook with a mocked HTTP server, and alert-channel isolation
- **Integration (Spring Boot, H2, temporary folder as the bucket):** full imports with rejected rows, upsert instead of duplicates, a broken file next to a good one (partial run + alert), a file delivered twice, quoted CSV fields and Windows line endings, rate refresh with retries, failure, invalid data and wrong base currency, the report endpoints with currency conversion and missing rates, and the run-history endpoints
- **Wiring (nothing mocked):** the application starts with its real beans in local mode (REST client, webhook channel, scheduler) and in S3 mode, so a missing dependency or bean fails the build instead of the Docker start

The real S3 client and the real exchange-rate API are not part of the automated tests (they are mocked there); they are exercised by running the Docker Compose stack.

## Known limitations
- No authentication or authorization; the trigger endpoints are open.
- The overlap guard works inside one app instance. With several instances, use a database lock (for example ShedLock) so only one runs a job.
- A whole file is read into memory before parsing, which is fine for files of a few hundred MB at most; larger files would need streaming.
- Reports convert with the latest rates, not the rate on the order date.
- Alerts go to the log and an optional webhook only; there is no email or paging integration.
- The schema is created with `ddl-auto=update`; a production deployment should use a migration tool such as Flyway.
- Timestamps are stored in UTC without a zone.
