# Tasks

## 1. Dependencies and configuration

- [ ] 1.1 Add Micronaut Data JDBC, Flyway, Postgres driver, Moneta, Micronaut Security JWT, Testcontainers Postgres and
      ArchUnit to the version catalog and `backend`.
- [ ] 1.2 Configuration from environment variables only (`COLOEUS_TENANT_ID`, `COLOEUS_AUDIENCE`,
      `DATASOURCES_DEFAULT_*`); startup fails with the missing setting's name (design decision 10).
- [ ] 1.3 Health endpoints `/health/liveness` and `/health/readiness` (readiness includes the database).

## 2. Schema

- [ ] 2.1 Flyway `V1__ledger.sql`: `currency`, `account`, `balance`, `transaction`, `posting`, `idempotency_key`,
      `client`, `client_grant`, `player_name`; indexes for history (`account_id, created_at, id`) and name lookup
      (`lower(name)`).
- [ ] 2.2 Deferred constraint trigger: postings of a transaction sum to zero per currency.
- [ ] 2.3 Revoke `UPDATE`/`DELETE` on `transaction` and `posting` from the service role; test that an update fails.

## 3. Money (JSR 354)

- [ ] 3.1 `ColoeusCurrencyProvider` (`CurrencyProviderSpi`) backed by `currency`, reloaded on change on every replica.
- [ ] 3.2 Persistence mapper `NUMERIC` ↔ `Money` and JSON serde `{"amount","currency"}` ↔ `Money` (`DECIMAL128`).
- [ ] 3.3 Amount validation: positive, fraction digits ≤ currency scale, never rounded (`AMOUNT_INVALID`,
      `SCALE_EXCEEDED`).
- [ ] 3.4 ArchUnit test: no `BigDecimal` in public signatures outside the two mappers.

## 4. Currencies and accounts

- [ ] 4.1 `POST/GET /v1/currencies`, update without scale change, `PUT /v1/currencies/{code}/default`.
- [ ] 4.2 Automatic player accounts (`INSERT … ON CONFLICT DO NOTHING`), system accounts on first use.
- [ ] 4.3 `GET /v1/accounts/player/{uuid}`, `GET /v1/accounts/{id}/balances`.

## 5. Transactions

- [ ] 5.1 Booking service: ordered `SELECT … FOR UPDATE` on player balances, non-negative check, unlocked increment for
      source/sink accounts, insert transaction and postings, update balances (design decision 2).
- [ ] 5.2 `POST /v1/transactions` for `TRANSFER`, `MINT`, `BURN`, `BATCH` with reason, actor, external reference and up
      to 16 metadata entries.
- [ ] 5.3 `GET /v1/accounts/{id}/postings` with cursor pagination.
- [ ] 5.4 Integration tests (Testcontainers): transfer, insufficient funds, mint/burn, unbalanced batch, one failing batch
      leg, 100 parallel transfers across two application contexts on one database with zero-sum check.

## 6. Idempotency

- [ ] 6.1 `Idempotency-Key` required on writes; key row written in the booking transaction; canonical request hash.
- [ ] 6.2 Replay, conflict (409) and concurrent-duplicate tests; per-client scope test.
- [ ] 6.3 Daily cleanup of expired keys (injected `Clock`, test without waiting).

## 7. Authentication and grants

- [ ] 7.1 JWT validation against the tenant JWKS, issuer, audience and `roles` (like Quelea's `application.yml`).
- [ ] 7.2 Client registry and grants: `POST /v1/clients`, `PUT /v1/clients/{id}/grants` (admin only).
- [ ] 7.3 Authorization by `azp` and grants, `TRANSFER_ON_BEHALF` with actor check.
- [ ] 7.4 Tests with locally signed test tokens: missing/expired/foreign-tenant/wrong-audience token, missing role,
      unregistered client, missing grant, actor mismatch.

## 8. Player names

- [ ] 8.1 `PUT /v1/players/{uuid}/name`, `GET /v1/players/by-name/{name}` with history.
- [ ] 8.2 Mojang profile lookup through a declarative HTTP client with timeout; tests against a local fake server
      (found, not found, timeout).

## 9. Errors and documentation

- [ ] 9.1 Problem Details handler with stable codes; `docs/problems.md`; test that every code is documented.
- [ ] 9.2 OpenAPI document `META-INF/swagger/coloeus.yml` generated at build time.
- [ ] 9.3 README: configuration table for the backend, local run with `docker/docker-compose.yml`.

## 10. Pull request

- [ ] 10.1 Open the code pull request `feat(ledger): add double-entry ledger service`.
