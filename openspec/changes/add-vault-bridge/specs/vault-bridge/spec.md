# Spec Delta

## Purpose
The Vault `Economy` provider on Paper: it serves the default currency of Coloeus to every Vault consumer on the server,
keeps reads local, books money synchronously with idempotent writes, and reports a player name register to Coloeus.

## ADDED Requirements

### Requirement: Provider registration
When the plugin starts with a complete configuration and Coloeus returns exactly one default currency, the plugin SHALL
register its `Economy` implementation with the Bukkit ServicesManager at priority `Highest`, and SHALL NOT register it
otherwise.

#### Scenario: Complete configuration
- **GIVEN** the configuration holds URL, tenant, client ID and client secret, and Coloeus marks `COINS` as default
- **WHEN** the plugin enables
- **THEN** the ServicesManager holds the provider for `Economy` at priority `Highest` and Vault consumers get `COINS`

#### Scenario: Missing secret
- **GIVEN** neither `client-secret` in `config.yml` nor `COLOEUS_CLIENT_SECRET` is set
- **WHEN** the plugin enables
- **THEN** the plugin logs one line naming the missing value, disables itself and registers no provider

### Requirement: Configuration precedence
The plugin SHALL read `url`, `tenant-id`, `client-id`, `client-secret` and `auth-scope` from `plugins/Coloeus/config.yml`,
and SHALL use the environment variable of the same name in upper snake case (`COLOEUS_URL`, `COLOEUS_TENANT_ID`,
`COLOEUS_CLIENT_ID`, `COLOEUS_CLIENT_SECRET`, `COLOEUS_AUTH_SCOPE`) instead of the file value when it is set and not
blank.

#### Scenario: Environment wins
- **GIVEN** `config.yml` sets `url` to a test server and `COLOEUS_URL` is set to the production URL
- **WHEN** the plugin enables
- **THEN** the client calls the production URL

#### Scenario: Default scope
- **GIVEN** neither `auth-scope` nor `COLOEUS_AUTH_SCOPE` is set
- **WHEN** the plugin enables
- **THEN** the client requests the scope `api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/.default`

### Requirement: Default currency metadata
While the provider is registered, the plugin SHALL answer `currencyNameSingular`, `currencyNamePlural` and
`fractionalDigits` from the default currency definition, and SHALL answer `format` with the amount rendered in that
currency with its fraction digits and symbol.

#### Scenario: Formatting
- **GIVEN** `COINS` has the symbol `c`, the singular name `coin`, the plural name `coins` and 2 fraction digits
- **WHEN** a consumer calls `format(12.5)`, `currencyNamePlural()` and `fractionalDigits()`
- **THEN** the results are a text with `12.50` and the symbol `c`, `coins` and `2`

### Requirement: Amount conversion at the Vault boundary
When a consumer passes an amount as `double`, the plugin SHALL convert it with `BigDecimal.valueOf(amount)` and round it
with `HALF_EVEN` to the fraction digits of the default currency, and SHALL do so in no other place of the Vault path.

#### Scenario: Rounding half to even
- **GIVEN** the default currency has 2 fraction digits
- **WHEN** a consumer deposits `1.015` and another consumer deposits `1.025`
- **THEN** the first amount is booked as `1.02` and the second as `1.02`

#### Scenario: Amount that rounds to zero
- **GIVEN** the default currency has 2 fraction digits
- **WHEN** a consumer deposits `0.001`
- **THEN** the answer is `SUCCESS` with the cached balance and no request is sent to Coloeus

### Requirement: Invalid amounts
If a consumer passes a negative amount, `NaN` or an infinite value, then the plugin SHALL answer `FAILURE` without a
request to Coloeus and without changing any balance.

#### Scenario: Negative deposit
- **GIVEN** a player holds 10.00 `COINS`
- **WHEN** a consumer deposits `-5.00` for that player
- **THEN** the answer is `FAILURE`, no request is sent and the cached balance stays 10.00

### Requirement: Deposit is minting
When a consumer deposits a positive amount for a player, the plugin SHALL book a `MINT` of that amount in the default
currency through `POST /v1/transactions`, and SHALL answer `SUCCESS` with the resulting balance only when Coloeus answers
the transaction as booked.

#### Scenario: Successful deposit
- **GIVEN** a player holds 10.00 `COINS` and this server holds the `MINT` grant
- **WHEN** a consumer deposits `2.50` for the player
- **THEN** the answer is `SUCCESS` with a balance of 12.50 and the cache holds 12.50

#### Scenario: Deposit without grant
- **GIVEN** this server holds no `MINT` grant for `COINS`
- **WHEN** a consumer deposits `2.50` for a player
- **THEN** the answer is `FAILURE`, the player's balance is unchanged and the cache is unchanged

### Requirement: Withdrawal is burning
When a consumer withdraws a positive amount from a player, the plugin SHALL book a `BURN` of that amount through
`POST /v1/transactions`, and SHALL answer `FAILURE` with the balance unchanged when the player holds less than the
amount.

#### Scenario: Insufficient funds
- **GIVEN** a player holds 3.00 `COINS`
- **WHEN** a consumer withdraws `4.00` from that player
- **THEN** the answer is `FAILURE`, no balance changes and the cache still holds 3.00

#### Scenario: Successful withdrawal
- **GIVEN** a player holds 3.00 `COINS` and this server holds the `BURN` grant
- **WHEN** a consumer withdraws `1.00` from that player
- **THEN** the answer is `SUCCESS` with a balance of 2.00

### Requirement: Synchronous writes within one second
The plugin SHALL send each deposit or withdrawal on the calling thread, SHALL wait at most 500 ms for the first attempt,
and SHALL NOT spend more than 1 s in total on one Vault call, including token acquisition and the retry.

#### Scenario: Slow Coloeus
- **GIVEN** Coloeus does not answer within 500 ms on either attempt
- **WHEN** a consumer deposits `2.50` for a player
- **THEN** the call returns `FAILURE` within 1 s and the cache keeps the last known balance

### Requirement: Idempotent retry
When the first attempt of a write times out, fails with a connection error or fails with a 5xx answer, the plugin SHALL
retry once within the remaining budget with the same `Idempotency-Key` as the first attempt, and SHALL create a new key
for each Vault call.

#### Scenario: Retry after a lost answer
- **GIVEN** the first attempt of a withdrawal of `1.00` times out after Coloeus booked it
- **WHEN** the retry answers with the stored result of the first attempt
- **THEN** the answer is `SUCCESS`, and the player has been debited exactly once

#### Scenario: Client error is final
- **GIVEN** Coloeus answers a withdrawal with 422 `INSUFFICIENT_FUNDS`
- **WHEN** the plugin receives the answer
- **THEN** it sends no retry and answers `FAILURE`

### Requirement: Banks are not implemented
The plugin SHALL report `hasBankSupport()` as `false`, and every bank method of `Economy` SHALL answer an
`EconomyResponse` with `NOT_IMPLEMENTED` without a request to Coloeus.

#### Scenario: Bank call
- **GIVEN** the provider is registered
- **WHEN** a consumer calls `createBank` for a name
- **THEN** the answer has the response type `NOT_IMPLEMENTED` and no request is sent

### Requirement: World arguments are ignored
The plugin SHALL answer every overload that takes a world name as if the world argument were absent.

#### Scenario: Deposit with world
- **GIVEN** a player holds 10.00 `COINS`
- **WHEN** a consumer deposits `1.00` for the player in world `survival`
- **THEN** the player holds 11.00 and the result equals the result of the deposit without world

### Requirement: Balance reads from the local cache
When a consumer reads a balance or checks `has`, the plugin SHALL answer from its balance cache and SHALL NOT wait for the
network when the player is online and the cache holds an entry for the player.

#### Scenario: Online player
- **GIVEN** a player joined and the cache holds 10.00 `COINS` for the player
- **WHEN** a consumer calls `getBalance` for that player
- **THEN** the answer is 10.00 without any request to Coloeus

#### Scenario: Cache miss for an offline player
- **GIVEN** an offline player whose balance is not in the cache
- **WHEN** a consumer calls `getBalance` for that player
- **THEN** the plugin fetches the balance synchronously with the 500 ms budget, stores it and answers it

#### Scenario: Offline player unreachable
- **GIVEN** an offline player whose balance is not in the cache and Coloeus does not answer in time
- **WHEN** a consumer calls `getBalance` for that player
- **THEN** the answer is 0.0, a warning is logged and nothing is stored

### Requirement: Cache refresh
The plugin SHALL fetch a player's balance asynchronously when the player joins, SHALL take the resulting balances from
the answer of each of its own writes, and SHALL refresh the balances of all online players every 30 seconds
asynchronously.

#### Scenario: Refresh of online players
- **GIVEN** a player is online and another server changed the player's balance
- **WHEN** 30 seconds have passed since the last refresh
- **THEN** the cache holds the new balance

#### Scenario: Join fetch
- **GIVEN** a player joins the server
- **WHEN** the join event is handled
- **THEN** the balance fetch runs on an asynchronous task and the join does not wait for it

### Requirement: Name resolution
When a consumer passes a player name instead of a player, the plugin SHALL resolve the name to a UUID from its own
name map first, and otherwise SHALL call `GET /v1/players/by-name/{name}` with the 500 ms budget; if the name cannot be
resolved in time or is unknown, then the plugin SHALL answer `FAILURE` for writes and `false` or `0` for reads.

#### Scenario: Known name
- **GIVEN** the player `Alex` joined the server
- **WHEN** a consumer calls `getBalance("Alex")`
- **THEN** the answer is the cached balance of that player without a request

#### Scenario: Unknown name
- **GIVEN** the name `Nobody123` is neither in the map nor known to Coloeus
- **WHEN** a consumer calls `depositPlayer("Nobody123", 1.00)`
- **THEN** the answer is `FAILURE` and no balance changes

### Requirement: Account existence
The plugin SHALL answer `hasAccount` with `true` when the player's account can be read or created in Coloeus, and with
`false` otherwise, and SHALL answer `createPlayerAccount` with `true` under the same condition without a write.

#### Scenario: Name that does not resolve
- **GIVEN** the name `Nobody123` does not resolve
- **WHEN** a consumer calls `hasAccount("Nobody123")`
- **THEN** the answer is `false`

### Requirement: Player name register
When a player joins, the plugin SHALL report the player's UUID and name to Coloeus with `PUT /v1/players/{uuid}/name`
on an asynchronous task, with an `Idempotency-Key` header, and a failed report SHALL NOT stop the join or change the
balance cache.

#### Scenario: Join report fails
- **GIVEN** Coloeus does not answer the name report of a joining player
- **WHEN** the join completes
- **THEN** the player is in the server, the failure is logged and the balance cache is unchanged
