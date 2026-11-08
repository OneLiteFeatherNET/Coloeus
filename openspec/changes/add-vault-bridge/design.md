# Design

## Context

Coloeus is the central economy service (`add-ledger-core`): an HTTP API with Entra ID bearer tokens, idempotent writes,
an append-only journal and JSR 354 amounts. The Vault bridge is its first Minecraft-side client. Paper servers call the
Vault `Economy` interface from the main server thread, often several times per tick (shop GUIs, job payouts, placeholder
expansions). The bridge therefore has two conflicting requirements: money writes must be correct and never guessed,
while reads and writes issued by Vault must not stall the main thread. The decisions below resolve that conflict.

Requirements: Outline "Anforderungen: Coloeus", stage 2 (Vault). Architecture principles are in `openspec/config.yaml`.
This design keeps principle 3 (amounts are converted only at the database, JSON and Vault boundaries) and principle 4
(the backend is stateless). The balance cache lives in the Paper plugin, not in the backend, so it does not break 4.

## Goals / Non-Goals

**Goals:** existing Vault consumers work unchanged; no money is ever created or destroyed by a timeout; a Vault call
blocks the main thread for at most one second; the client module is reusable by later services.

**Non-Goals:** banks, real-time events, multiple currencies through Vault, Velocity commands (see proposal).

## Decisions

### 1. One provider for the default currency, registered at Highest

The plugin implements `net.milkbowl.vault.economy.Economy` for the default currency that `GET /v1/currencies` marks as
`defaultCurrency: true`, read once at startup. It registers the provider with
`Bukkit.getServicesManager().register(Economy.class, provider, plugin, ServicePriority.Highest)`. The `format`,
`currencyNameSingular`, `currencyNamePlural` and `fractionalDigits` methods read the same currency definition.

*Rejected:* registering at Normal and asking admins to set the priority of other economy plugins down (an install-time
task that is easy to forget, and Vault consumers then silently use the old economy); one provider per currency (Vault
has no currency parameter, so it would not be selectable); reading the default currency on every call (a network call
on the main thread for a value that changes rarely).

*Trade-off:* a default-currency change needs a restart of the servers that use the bridge (named in the proposal).

### 2. Amount conversion happens once, at the Vault boundary

Vault passes `double` amounts. The bridge converts them in one mapper: `BigDecimal.valueOf(amount)` (which uses the
shortest decimal representation of the double, so `12.5` stays `12.5`), then `setScale(fractionDigits, HALF_EVEN)`, then
`Money.of(…, currency)`. This is the only place in the Vault path where an amount is rounded. The ledger never rounds
(`add-ledger-core`, decision 3), so the bridge rounds before it sends. Negative amounts, `NaN` and infinite values answer
`FAILURE` without a call. A zero amount (after rounding) answers `SUCCESS` with the cached balance and sends nothing,
because the ledger rejects zero amounts with `AMOUNT_INVALID`.

*Rejected:* `HALF_UP` (systematically inflates amounts over many payouts); rounding in the ledger (contradicts the
ledger's no-rounding rule); doing the arithmetic in `double` (binary floating point cannot represent `0.10`).

### 3. Vault deposits are minting and withdrawals are burning

`depositPlayer` books a `MINT`: the client's source account is debited and the player credited. `withdrawPlayer` books a
`BURN`: the player is debited and the client's sink account credited. Vault has no notion of a transfer between two
players, so none is offered. The server's `MINT` and `BURN` grants for the default currency are required; without them
the ledger answers `FORBIDDEN_GRANT` and the bridge answers `FAILURE`.

*Rejected:* a treasury player account funded once (a second money source that has to be topped up by hand; deposits
would fail when the treasury is empty); booking Vault deposits as transfers from a shared pool (same problem, one pool
for the whole network).

### 4. Banks answer NOT_IMPLEMENTED in this change

`hasBankSupport()` returns `false`, so consumers that check it do not try bank calls. Every bank method returns an
`EconomyResponse` with `ResponseType.NOT_IMPLEMENTED`. The `BANK` account kind exists in the schema of `add-ledger-core`
but has no endpoints yet; the bank change decides the mapping.

*Rejected:* partial bank support on top of player accounts (a bank would look like a player, and consumers would book
bank money that the ledger cannot separate from player money).

### 5. Writes are synchronous with one budget of 1 s

Every `depositPlayer` and `withdrawPlayer` sends `POST /v1/transactions` on the calling thread. The first attempt has a
timeout of 500 ms. One retry follows only for a timeout, a connection error or a 5xx answer, and only while the total
time of the call stays under 1 s. The retry reuses the same `Idempotency-Key` (a random UUID created per Vault call), so
a retry of an already booked transaction returns the stored result instead of booking again. A 4xx answer is final.
The token acquisition (decision 9) counts against the same budget. If the call is not answered in time or the answer
is an error, the response is `FAILURE`.

The blocking is accepted for the MVP because it happens only when a plugin pays or charges money, which is a small
fraction of the Vault calls, and because the only alternative that does not block (asynchronous writes, rejected below)
gives up the correctness the money needs. The latency of the write path is measured on a test server before the change
is merged; the timeout becomes a configuration key only if that measurement proves it necessary.

*Rejected:* asynchronous writes that report `SUCCESS` at once and book later (Vault callers act on the answer, so a later
failure would be a lost payment or a duplicate payout; this is the guessed success the design forbids); queueing writes
on the plugin side (the answer is still needed now, and a crash loses the queue); unlimited retries (a stall on the main
thread of unknown length).

*Risk:* a booked transaction whose answer never reached the bridge is reported as `FAILURE` if both attempts fail. The
player then keeps the money, which is the safe direction for the ledger, and the next cache refresh shows the true
balance. The only case in which money is created twice is a retry with a new key, which the design never does.

### 6. Reads come from a local balance cache, not from the network per call

`getBalance`, `has` and `hasAccount` answer from the cache. The cache holds, per player UUID, the balance in the default
currency and the knowledge that the account exists. It is filled by four sources:

1. an asynchronous fetch on `PlayerJoinEvent` (`GET /v1/accounts/player/{uuid}` and its balances);
2. the balances in the transaction response of each of this server's own writes (the response contains the resulting
   balances, so no extra read is needed);
3. an asynchronous refresh of all online players every 30 seconds;
4. a synchronous fetch with the 500 ms timeout when an offline player's balance is requested and the cache has no
   entry; a failed fetch answers `0` for `getBalance` and `false` for `has` and `hasAccount`, and is logged at warning
   level, since Vault cannot express an error here.

A cached balance can be up to 30 seconds old. `has` can therefore answer `true` while another server spends the
money; the write answer is authoritative (`withdrawPlayer` answers `FAILURE` if the money is gone), and consumers must
treat `has` as a pre-check, which is also how Vault consumers are written.

*Rejected:* a synchronous read per `getBalance` call (every call would wait for the network on the main thread, and
placeholder expansions call it for every visible player per tick); polling every second (load on the backend that grows
with players, not with payments); caching only after writes (online balances would never change without a payment on
this server).

*Replaced by:* SSE from `add-balance-events`. When it lands, the 30-second refresh goes away; the cache and the write path
stay as they are.

*Principle check:* the cache is per plugin instance and is not authoritative. Losing it costs a fetch, not money.

### 7. Name-based overloads resolve names through the register

Vault name overloads (`getBalance(String)`, `has(String, …)`, `withdrawPlayer(String, …)`, `hasAccount(String)`,
`createPlayerAccount(String)`) need a UUID. The bridge first looks in its own name-to-UUID map (filled by join reports and
earlier lookups), then calls `GET /v1/players/by-name/{name}` synchronously with the 500 ms budget. If the name is unknown
to the register, the ledger asks Mojang; that call can be slower than the budget. A name that cannot be resolved in time
answers `FAILURE` for writes and `false` / `0` for reads, the same as decision 6.

*Rejected:* asynchronous name resolution (Vault name overloads must answer in the same call); refusing name overloads
(many plugins only know names, for example from a command argument).

*Risk:* Mojang latency for names that are not yet in the register. Known players, which is nearly everyone who has
joined a server since the bridge went live, are not affected.

### 8. Accounts exist on access; hasAccount is true for any resolvable player

The ledger creates a player account on first access (`add-ledger-core`, decision 4). `hasAccount(OfflinePlayer)` is
therefore true when the account can be read or created, and `hasAccount(String)` is true when the name resolves.
`createPlayerAccount` answers `SUCCESS` under the same condition, without any write, because the account exists after
the read. A name that does not resolve is never reported as an account, so a typo in a command does not pass a check.

*Rejected:* always `true` (a misspelled name would pass a check and fail later); a separate account-creation endpoint
(the ledger does not need one).

### 9. Java client: JDK HttpClient, Gson, Moneta; Entra tokens cached until 60 s before expiry

The client uses `java.net.http.HttpClient` (already in the JDK, no transitive dependency, supports timeouts per request
and HTTP/2 on the cluster ingress) and Gson for JSON. Gson is about 300 KB, needs no reflection configuration for the
plain DTOs used here, and when shaded into the Paper plugin under a relocated package it cannot clash with the Gson the
server provides. Money is `MonetaryAmount` from Moneta in the public API; the JSON mapper converts the decimal string
to `Money` with `MathContext.DECIMAL128`, which is the second of the three conversion boundaries.

The token is requested with the OAuth2 client-credentials grant from
`https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token` with scope `COLOEUS_AUTH_SCOPE` (default
`api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/.default`). It is cached until 60 seconds before its `expires_in`. A refresh
is started asynchronously at 60 seconds before expiry, so the write path normally finds a valid token; a request that
needs a token while none is cached fetches one synchronously, inside the write budget. Concurrent refreshes are merged
into one request.

*Rejected:* Jackson (larger, needs modules for the money and time types, and the client is a library other plugins
may embed); Apache HttpClient or OkHttp (extra dependencies for what the JDK already provides); MSAL (a large library
for one grant); a generated OpenAPI client (the `add-ledger-core` OpenAPI document is not yet published; the client is
small enough to write by hand and is checked against the document in its tests).

*Shading:* the Paper plugin relocates `com.google.gson` and does not relocate `javax.money`, because JSR 354 finds its
implementation through `ServiceLoader` under that name. The shading step merges the `META-INF/services` files.

### 10. Problem Details map to typed exceptions by code

Every error answer of the ledger is an RFC 9457 document with a stable `code` (`docs/problems.md`). The client
branches on the `code`, never on the `status` or the `detail`. Known codes map to typed exceptions that the bridge
catches: `InsufficientFundsException`, `ForbiddenGrantException`, `PlayerUnknownException`,
`NameLookupUnavailableException`, `AuthenticationException`. Other codes map to `ColoeusProblemException`, which carries
`code`, `status` and `detail`. A timeout, a connection error or a 5xx answer maps to `ColoeusUnavailableException`, which
is the only type for which the retry of decision 5 applies.

*Rejected:* parsing `detail` (documented as not machine-readable); one exception type with a status field (every caller
would switch on a string).

### 11. Configuration from `config.yml` with environment variables winning

The plugin reads `plugins/Coloeus/config.yml` with the keys `url`, `tenant-id`, `client-id`, `client-secret` and
`auth-scope`. An environment variable with the same name in upper snake case (`COLOEUS_URL`, `COLOEUS_TENANT_ID`,
`COLOEUS_CLIENT_ID`, `COLOEUS_CLIENT_SECRET`, `COLOEUS_AUTH_SCOPE`) takes precedence, as in the Quelea plugin. The
production URL is `https://coloeus.apps.onelite.feather`. When `url`, `tenant-id`, `client-id` or `client-secret` is
missing after this resolution, the plugin logs one line naming the missing key and disables itself; the Vault provider
is not registered. A missing `auth-scope` falls back to the default in decision 9.

*Rejected:* the secret only in the environment (local development on a test server needs a file); the secret only in
the file (it is then committed by accident in a server template).

*Risk:* the client secret in a plain config file on a server disk. Production uses the environment variable; the file
option exists for test servers.

### 12. Player name register on join, asynchronous

`PlayerJoinEvent` triggers `PUT /v1/players/{uuid}/name` on an asynchronous task. The join does not wait for the answer.
Every write carries an `Idempotency-Key`, because `add-ledger-core` requires one on each write request. A failed report is
logged and retried at the next join of that player; it does not affect the join or the cache.

*Rejected:* a synchronous report on join (delays the player's login by a network round trip); reporting on quit (a crash
loses the name of players who did not quit cleanly); a periodic batch (names are missing for the minutes between).

### 13. Testing without a server and without real network

- Money conversion, rounding and response mapping are plain unit tests (`BigDecimal`, `Money`, `EconomyResponse`
  without a running server).
- The balance cache is a unit test with an injected `java.time.Clock` for the 30-second refresh and the 60-second token
  margin.
- The client is tested against a local fake HTTP server (`com.sun.net.httpserver.HttpServer` on the loopback interface)
  for found, not found, timeout, 5xx, Problem Details and token acquisition. No test reaches Entra, Mojang or the
  cluster.
- The `Economy` implementation is tested against a fake client interface. MockBukkit is not added in this change: its
  support for Paper 26.1 is not verified, and the provider needs only the ServicesManager and the events, which are
  checked on a test server (task 8.2).

Each test follows the F.I.R.S.T. rules of `openspec/config.yaml` and the project tests guideline: no `Thread.sleep`,
fresh fixtures per test, no real network, one behaviour per test.

## Risks / Trade-offs

- **Main-thread blocking.** A payment or charge can block the main thread for up to one second (decision 5). Accepted
  for the MVP and measured on a test server before the merge. Plugins that pay many players in one tick are the case to
  watch; the 1 s cap bounds the stall, and the cure (asynchronous Vault calls) is a separate design.
- **Mojang latency for unknown names** (decision 7). Names not yet in the register can exceed the budget and answer
  `FAILURE` or `0`. Join reports keep this to players who never joined since the bridge went live.
- **Money created by plugins.** Any plugin on a server with a `MINT` grant can call `depositPlayer` and create money, and
  Vault does not say which plugin asked. Mitigations: the grant is given only to servers, not to plugins; every
  transaction is recorded with the server's client ID; the reason text states the Vault method; per-plugin attribution
  needs the plugin name in the request, which Vault does not provide, and is left to a later change.
- **Stale balances** up to 30 seconds (decision 6) until `add-balance-events`.
- **Booked but reported as failed** after two failed attempts (decision 5). The next refresh shows the truth; no money is
  created by the retry.
- **Default-currency change** needs a restart of the servers (proposal, out of scope).

## Migration Plan

New plugin on the Paper servers, activated per server by its configuration. Servers without configuration keep their
current economy plugin: the bridge disables itself and does not register. Rollback: remove the plugin jar; the balances
stay in the ledger, and the previous economy plugin is again the only provider.
