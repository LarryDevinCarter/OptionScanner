# OptionScanner

**Background data engine** for concrete stock and option data (fundamentals, prices, option chains).

It is **not** a trading bot and does **not** require quality filters for downstream consumers.
The sibling `trading-system` may call these data endpoints later without assuming OptionScanner’s
optional filter thresholds are applied.

Wheel strategy work is deferred.

## Role

- Sync the Alpaca US-equity options-capable universe into Postgres
- Pull Alpha Vantage fundamentals (income, earnings, balance sheet, cash flow, dividends) and quotes
- Pull Alpaca option chains
- Persist **per-symbol / incremental** updates so the DB always has last-good rows while refresh runs
- Expose HTTP APIs for market status, candidates, option batches, and refresh progress

## Important: `/api/update-status` is NOT a lock

`GET /api/update-status` returns **informational** progress only:

| Field | Meaning |
|-------|---------|
| `isUpdating` / `refreshInProgress` | A refresh tick is currently running in-process |
| `lastRefreshStarted` | When the current/last tick began |
| `lastRefreshFinished` | When the last tick completed |

**Consumers must NOT block reads on this endpoint.** Stock/option data stays readable during refresh.
Overlapping scheduled ticks are skipped in-process so two refreshes do not run at once; that is an
internal scheduler guard, not an API lockout.

## HTTP endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/update-status` | Informational refresh progress (do not lock on this) |
| `GET` | `/api/market-status` | Alpaca market open/closed status |
| `POST` | `/api/stock-candidates` | Optional quality-filter candidate list (filters are opt-in) |
| `POST` | `/api/option-chains/batch` | Batch option chains for requested tickers |

Default server port: **8081**.

## Rate limits (Alpha Vantage free tier)

Free tier ≈ **25 calls/minute**. Configure delay:

```properties
alphavantage.delay-ms=2500
```

Default is **2500 ms** (≥ 2400 ms required for 25/min). Do not assume 75/min.

Each symbol typically needs several AV calls (statements + quote), so a full universe refresh
takes **many hours** on free tier. Prefer the rolling batch schedule below.

## Refresh schedule (droplet-friendly)

Instead of one nightly mega-job that runs 3–4 hours, OptionScanner refreshes in **batches**:

```properties
# Top of every hour, America/Chicago
optionscanner.refresh.cron=0 0 * * * ?
optionscanner.refresh.zone=America/Chicago
# Symbols per tick, oldest lastUpdated first (round-robin by staleness)
optionscanner.refresh.batch-size=230
optionscanner.refresh.sync-universe=true
```

**Suggested strategy for Larry (free AV tier):**

1. Keep hourly cron + `batch-size` around 220–230.
2. Expect a multi-hour / multi-day rolling refresh to cover the full options-capable universe.
3. Prioritization is automatic: symbols with the oldest `lastUpdated` are refreshed first;
   brand-new symbols start with epoch time so they jump the queue.
4. Raise `batch-size` only if you upgrade AV limits; lower it if you see AV throttling/503s.
5. Reads stay available the whole time — point trading-system (or scripts) at data endpoints
   without waiting for `update-status` to clear.

Universe sync (`sync-universe=true`) upserts Alpaca assets each tick **without** wiping the table
and **without** resetting `lastUpdated` on unchanged symbols (so round-robin keeps working).

## Secrets hygiene

- Copy `src/main/resources/application.properties.example` → `application.properties`
- Fill in real keys locally / on the droplet
- `application.properties` is **gitignored** — never commit secrets
- Do not log API keys or OAuth tokens

## Run locally

Requirements: **Java 21**, **Maven wrapper**, **Postgres** with a database matching
`spring.datasource.url`.

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
# edit application.properties with your keys + DB password

./mvnw -DskipTests package
./mvnw spring-boot:run
# or: java -jar target/option-scanner-0.0.1-SNAPSHOT.jar
```

Schema: `spring.jpa.hibernate.ddl-auto=validate` — apply your existing migrations/schema first.

## Optional filters

Thresholds in `application.properties` (`eps.growth.*`, `roic.*`, etc.) power
`POST /api/stock-candidates` only. They are **not** auto-required for trading strategies that
consume raw fundamentals or option data from this engine.

## License / author

Larry Devin Carter — data engine for personal / family trading tooling.
