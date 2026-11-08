# Tasks

Prerequisite: `add-ledger-core` is merged to `main`, so that the endpoints used here exist.

## 1. Java client (module `client`)

- [ ] 1.1 Add JSR 354 API and Moneta, and Gson, to the version catalog and to `client`; keep the module free of Paper and
      Vault classes.
- [ ] 1.2 Client configuration value (URL, tenant, client ID, secret, scope) and `TokenProvider` for the Entra
      client-credentials grant: cache until 60 s before expiry, single refresh in flight, injected `java.time.Clock`.
- [ ] 1.3 HTTP transport on `java.net.http.HttpClient`: bearer header, `Idempotency-Key` on writes, per-call timeout
      with default 500 ms, `ColoeusUnavailableException` for timeouts, connection errors and 5xx.
- [ ] 1.4 Problem Details mapping by `code` to `InsufficientFundsException`, `ForbiddenGrantException`,
      `PlayerUnknownException`, `NameLookupUnavailableException`, `AuthenticationException` and
      `ColoeusProblemException`.
- [ ] 1.5 JSON mapper: decimal string ↔ `MonetaryAmount` with `DECIMAL128` (the only JSON amount conversion in the
      client).
- [ ] 1.6 Calls: default currency, by-name lookup, name report, account read, balances, transaction (`MINT`, `BURN`,
      `TRANSFER`) returning the resulting balances; write retry once within the budget with the same key.
- [ ] 1.7 Tests against a local fake HTTP server on the loopback interface: found, not found, timeout, 5xx, Problem
      Details of a known and an unknown code, token reuse, token near expiry, concurrent token refresh, retry within and
      outside the budget.

## 2. Paper plugin skeleton (module `paper`)

- [ ] 2.1 Build: Paper 26.1 and Java 25, Vault API `com.github.MilkBowl:VaultAPI:1.7` from jitpack as `compileOnly`,
      shading of the client and Gson with a relocated package, merged `META-INF/services` for JSR 354, `javax.money` not
      relocated.
- [ ] 2.2 `plugin.yml` with `depend: [Vault]` and the Paper API version; `config.yml` with the five keys and no secret.
- [ ] 2.3 Configuration resolution with environment variables winning; a missing URL, tenant, client ID or secret logs
      one line naming the key, disables the plugin and registers no provider. Unit tests for precedence and the missing
      cases.

## 3. Money at the Vault boundary

- [ ] 3.1 Mapper `double` → `Money` of the default currency: `BigDecimal.valueOf`, `HALF_EVEN` to the fraction digits;
      `Money` → `double` for answers.
- [ ] 3.2 Unit tests: rounding vectors (`1.015` and `1.025` with 2 digits both to `1.02`, `0.001` to zero), negative,
      `NaN` and infinite values rejected, zero after rounding treated as no request.

## 4. Balance cache and name map

- [ ] 4.1 Cache per player UUID (balance and account existence) and name-to-UUID map, filled by join reports and lookups;
      all time reads through an injected `Clock`.
- [ ] 4.2 Refresh of online players every 30 s on an asynchronous scheduler; update of an entry from a transaction
      answer.
- [ ] 4.3 Synchronous fetch for an offline player on a cache miss, with the 500 ms budget; a failed fetch answers 0 or
      false and logs at warning level without storing anything.
- [ ] 4.4 Unit tests with a fake client and a mutable clock: no sleeps, a refresh changes the answer, a miss fetches
      once, a failed fetch stores nothing, a join fetch runs asynchronously.

## 5. Economy provider

- [ ] 5.1 Read the default currency at startup (exactly one `defaultCurrency: true` entry), then register the `Economy`
      implementation at `ServicePriority.Highest`; if no default exists, log and disable without registering.
- [ ] 5.2 Balance and account methods on player, name and offline-player overloads; world overloads ignore the world.
- [ ] 5.3 `depositPlayer` as `MINT` and `withdrawPlayer` as `BURN` with one `Idempotency-Key` per call, the 1 s budget,
      `EconomyResponse` mapping (`SUCCESS`, `FAILURE` for insufficient funds, missing grant, timeout and invalid amount)
      and cache update from the answer.
- [ ] 5.4 `hasBankSupport` false, all bank methods `NOT_IMPLEMENTED`; `format`, `currencyNameSingular`,
      `currencyNamePlural`, `fractionalDigits`, `isEnabled`, `getName`.
- [ ] 5.5 Tests against a fake client (no server): success, insufficient funds, forbidden grant, timeout and retry
      with the same key, negative amount, bank calls, world overloads, name resolution failure, format output.

## 6. Player name register

- [ ] 6.1 `PlayerJoinEvent` listener that reports UUID and name with `PUT /v1/players/{uuid}/name` on an asynchronous
      task with an `Idempotency-Key`; the failure is logged and changes nothing else.
- [ ] 6.2 Test that the join handler returns before the report completes and that a failed report leaves the cache
      unchanged.

## 7. Verification

- [ ] 7.1 Run the client and paper unit tests; check the F.I.R.S.T. rules (no sleeps, no system time, no shared static
      state, no order dependence).
- [ ] 7.2 Acceptance on a Paper 26.1 test server against a local backend (`docker/docker-compose.yml` of `add-ledger-core`)
      with a MINT and BURN grant: join, balance read, deposit, withdrawal, insufficient funds, a second server seeing the
      changed balance within 30 s. Record the p95 latency of deposits and withdrawals in the pull request description.
- [ ] 7.3 Check that the provider is registered at `Highest` on the test server (`/vault` or the ServicesManager output)
      and that a server without configuration keeps its previous economy.

## 8. Pull request

- [ ] 8.1 Open the code pull request `feat(vault): add vault economy bridge for paper` from the implementation only: no
      file under `openspec/`, branched from `main` after the proposal and `add-ledger-core` are merged.
