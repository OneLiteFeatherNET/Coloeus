# Design

## Context

Coloeus is one central, stateless service in the OneLiteFeather Kubernetes cluster with at least two replicas and one
Postgres database (CloudNativePG). Paper (through a Vault bridge), Velocity, Minestom and other services call it over
HTTP. This change builds the ledger core; the clients come later. Requirements: Outline "Anforderungen: Coloeus" and
"User Stories: Stufe 1"; architecture: Outline "Architekturvorschlag: Coloeus".

## Goals / Non-Goals

**Goals:** correct money under concurrency and retries, one amount model end to end, a schema that later changes extend
without rewriting.

**Non-Goals:** fees, limits, overdraft, events, clients, staff UI (later changes).

## Decisions

### 1. Double-entry journal with a balance projection

Tables `transaction` (header) and `posting` (account, currency, signed amount) are insert-only. `balance` holds one row
per account and currency and is updated in the same database transaction as the postings. A deferred constraint trigger
checks that the postings of each transaction sum to zero per currency; the domain checks it first, the trigger is the
safety net. The service's database role has no `UPDATE`/`DELETE` grant on `transaction` and `posting`.

*Rejected:* a mutable balance column without journal (no audit, no correction without data loss); event sourcing with a
separate store (the journal already is the event log; a second store adds consistency problems).

### 2. Concurrency through ordered row locks

Inside one database transaction the service locks the affected `balance` rows with `SELECT … FOR UPDATE` ordered by
`(account_id, currency)`, checks that no player balance goes below zero, then writes. `READ COMMITTED` is enough because
every balance change goes through a locked row. Source and sink accounts may go negative without limit and are updated
with `UPDATE … SET amount = amount + ?` without a prior lock, so they never become a lock hotspot. Locks live in
Postgres, so the scheme is correct across any number of replicas.

*Rejected:* `SERIALIZABLE` with retry (retries under load, harder to reason about); optimistic version checks (retry storms
on hot accounts); an in-memory lock per account (wrong with more than one replica).

### 3. Money with JSR 354 everywhere, converted at two boundaries here

Domain types use `MonetaryAmount`; the implementation is Moneta `Money` with `MathContext.DECIMAL128`. `FastMoney` is not
used: it is limited to five fraction digits and the `long` range. A `CurrencyProviderSpi` (`ColoeusCurrencyProvider`)
serves `CurrencyUnit`s from the `currency` table and reloads on change. Conversions:

- database: `NUMERIC(38, scale)` ↔ `Money` in one persistence mapper;
- JSON: `{"amount": "12.50", "currency": "COINS"}` ↔ `Money` in one serde mapper.

An ArchUnit test fails if `BigDecimal` appears in a public signature outside these mappers. Incoming amounts are never
rounded: more fraction digits than the currency allows yields `SCALE_EXCEEDED`; zero or negative amounts yield
`AMOUNT_INVALID`. (Rounding of computed fees arrives with `add-ledger-rules`; the Vault boundary with `add-vault-bridge`.)

*Rejected:* `BigDecimal` in the backend and JSR 354 only in the client (two amount models, conversions spread through the
backend); amounts as JSON numbers (precision loss in many JSON libraries).

### 4. Accounts and who may create money

`account.kind` is one of `PLAYER`, `BANK` (used later), `SOURCE`, `SINK`, `FEE`, `ADMIN_SOURCE`, `ADMIN_SINK`. A player
account is created on first access to its UUID (`INSERT … ON CONFLICT DO NOTHING`). `MINT` books `SOURCE(client) −x,
PLAYER +x`; `BURN` books `PLAYER −x, SINK(client) +x`. The source balance of a client therefore shows how much money that
client created in total. Minting and burning require the grant `MINT` or `BURN` for that currency.

*Rejected:* one global mint account (no way to see which server created how much); letting any authenticated client
mint (no control, decided against on 04.11.2026).

### 5. Idempotency stored with the transaction

Table `idempotency_key (client_id, key, request_hash, transaction_id, response, expires_at)`, primary key
`(client_id, key)`. The key row is inserted in the same database transaction as the postings. A concurrent duplicate
blocks on the primary key and, after the first commits, reads the stored response. Same hash → stored response with the
original status; different hash → 409 `IDEMPOTENCY_CONFLICT`. `request_hash` is SHA-256 over the canonical JSON body plus
method and path. Expired keys are deleted by a daily job (the only delete in the schema; keys are not journal data).

*Rejected:* a Redis-based key store (a second system, not atomic with the booking).

### 6. Entra ID tokens and a client registry

Like Quelea: Micronaut Security with bearer JWTs validated against the tenant's JWKS, issuer and audience checks, and the
`roles` claim. App roles: `Coloeus.Client` (service-to-service) and `Coloeus.Admin` (management; assignable to
applications and to users/groups, so automation and staff can both manage). Table `client (client_id, display_name)` and
`client_grant (client_id, currency, permission)` hold fine-grained rights, keyed by the token's `azp`. A service with
`Coloeus.Client` but no registry entry may read but not write. Tests sign tokens with a local test key; no real Entra is
needed.

*Rejected:* all rights as Entra app roles (many roles, changes need Entra admins); API keys (own key management;
Quelea already uses Entra).

### 7. Acting on behalf of a player

A `TRANSFER` from a player account requires either that the caller holds `TRANSFER_ON_BEHALF` and sends the player's
UUID as `actor` equal to the source account owner, or the `Coloeus.Admin` role. This lets `/pay` debit only the paying
player.

### 8. Player names

Table `player_name (uuid, name, first_seen, last_seen, source)`. `PUT /v1/players/{uuid}/name` records a join.
`GET /v1/players/by-name/{name}` answers from the register (most recent holder of the name, case-insensitive); on a miss
it calls the Mojang profile API through a Micronaut declarative HTTP client with a short timeout, stores the result with
source `MOJANG` and returns it, or `PLAYER_UNKNOWN`. Mojang failures return 503 `NAME_LOOKUP_UNAVAILABLE`, never a guess.

### 9. API shape

Versioned under `/v1`, OpenAPI generated at build time (`META-INF/swagger/coloeus.yml`) for the later generated client.
Writes: `POST /v1/transactions` with `kind` and `postings` or the shorthand fields `from`, `to`, `amount`. Reads: accounts,
balances, postings (cursor = `(created_at, posting_id)` of the last item). Errors: RFC 9457 Problem Details with `code`,
documented in `docs/problems.md`.

### 10. Operations

Flyway runs at startup (it locks itself against parallel replicas). Configuration comes only from environment variables
(`COLOEUS_TENANT_ID`, `COLOEUS_AUDIENCE`, `DATASOURCES_DEFAULT_URL`, …); missing required values fail the startup.
Health: `/health/liveness`, `/health/readiness` (including the database). OpenTelemetry traces and JSON logs as in
Quelea; metrics arrive with `add-balance-events`.

## Risks / Trade-offs

- **Hot player accounts** (a busy shop owner) serialize on one row lock. Acceptable at network scale; measured in the
  load test of `add-balance-events`.
- **Mojang availability** limits payments to never-seen names; known names keep working.
- **Insert-only grants** depend on the operator creating the database role correctly; the Flyway migration revokes
  `UPDATE`/`DELETE` explicitly, and a test checks that an update on `posting` fails.

## Migration Plan

New service, no data to migrate. Rollback: undeploy; the database stays.
