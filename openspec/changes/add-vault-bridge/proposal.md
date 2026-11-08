# Proposal

**Ships as:** `feat(vault)` — one code pull request `feat(vault): add vault economy bridge for paper`.
It opens only after this proposal is merged and after `add-ledger-core` is merged to `main`; the bridge calls the ledger
API that change defines.

## Why

Paper servers on the network still keep their own economy. Shops, jobs and other plugins talk to Vault, and Vault
answers from whatever economy plugin is installed locally, so a player's coins on one server are unrelated to the coins
on the next. The central ledger (`add-ledger-core`) can hold the money for the whole network, but nothing on Paper
reads or writes it yet.

The Vault API is the interface almost every Paper economy consumer already uses. Implementing it on top of Coloeus
lets those plugins work unchanged while the balance lives in one place. The bridge is the first client of the ledger
and the first real test of its HTTP contract, idempotency and money model from a Minecraft server.

## What Changes

- **A Java client** (`client` module, package `net.onelitefeather.coloeus.client`): a small HTTP client for the
  Coloeus API on plain `java.net.http.HttpClient`. It fetches Entra ID client-credentials tokens and caches them until
  60 seconds before expiry, exposes balances, transactions and player names with `MonetaryAmount` in its public API,
  and maps RFC 9457 Problem Details to typed exceptions by their stable `code`. This client is the one the later
  changes reuse; it takes over the `add-java-client` change named in `add-ledger-core`.
- **A Vault economy provider** (`paper` module): a full implementation of `net.milkbowl.vault.economy.Economy` (Vault
  API 1.7) for the default currency of Coloeus, registered with the Bukkit ServicesManager at priority Highest.
  - `getBalance`, `has` and `hasAccount` answer from a local balance cache. The cache is fed by a join fetch, by the
    transaction responses of this server's own writes and by a 30-second refresh of online players.
  - `depositPlayer` books a `MINT` against this server's source account; `withdrawPlayer` books a `BURN` into its sink
    account. Both are synchronous HTTP writes with an `Idempotency-Key`, a 500 ms timeout and one retry, capped at 1 s in
    total. A timeout or error answers `FAILURE`, never a guessed success.
  - Amounts arrive as `double`; they are converted once, at the Vault boundary, with `BigDecimal.valueOf` and
    `HALF_EVEN` rounding to the currency's fraction digits. Negative amounts and insufficient funds answer `FAILURE`.
  - Name-based overloads resolve the name through `GET /v1/players/by-name/{name}`. World-name overloads ignore the
    world.
  - `format`, `currencyNameSingular`, `currencyNamePlural` and `fractionalDigits` come from the currency definition.
- **Player name register**: on `PlayerJoinEvent` the plugin reports the player's UUID and name with
  `PUT /v1/players/{uuid}/name`, asynchronously, so that name lookups and offline payments can find the player.
- **Configuration**: `plugins/Coloeus/config.yml` with the keys `url`, `tenant-id`, `client-id`, `client-secret` and
  `auth-scope`. Environment variables of the same names as `COLOEUS_URL`, `COLOEUS_TENANT_ID`, `COLOEUS_CLIENT_ID`,
  `COLOEUS_CLIENT_SECRET` and `COLOEUS_AUTH_SCOPE` take precedence. When a required value is missing, the plugin
  disables itself with a log line that names the value and does not register the provider.
- **Dependencies**: Paper 26.1 on Java 25; Vault (`com.github.MilkBowl:VaultAPI:1.7` from jitpack) as `compileOnly`;
  `plugin.yml` declares `depend: [Vault]`. The plugin shades the client, Gson and Moneta.

## Capabilities

### New Capabilities

- `java-client`: the HTTP client for the Coloeus API: configuration, Entra client-credentials tokens, typed calls,
  Problem Details mapping, timeouts.
- `vault-bridge`: the Vault `Economy` provider on Paper: default currency, balance cache, synchronous writes, name
  resolution, player name register.

### Modified Capabilities

None. The ledger, currency and player-name contracts are defined by `add-ledger-core` and are used as they are.

## Impact

- New content in `client` (`net.onelitefeather.coloeus.client`) and `paper` (`net.onelitefeather.coloeus.paper`) only.
  The `backend` module is not changed by this proposal.
- New dependencies: Vault API 1.7 (compile only), Moneta and the JSR 354 API (client), Gson (client, relocated when
  shaded). Paper's own classes are used as they are provided by the server.
- Runtime: one HTTP connection pool per server; every server needs an Entra client registration with the app role
  `Coloeus.Client` and, for minting and burning, the grants `MINT` and `BURN` on the default currency (added by an admin
  with `PUT /v1/clients/{id}/grants`, see `add-ledger-core`).
- Operations: the Coloeus URL and the client secret are per server. The secret is read from the environment in
  production.

## Out of scope

- **Banks.** `hasBankSupport()` returns `false` in this change. Every bank method returns an `EconomyResponse` with
  `NOT_IMPLEMENTED`. Bank accounts, bank members and bank balances come with a later change, which also decides how a
  `BANK` account maps to Vault.
- **Real-time balance updates.** Server-sent events (`add-balance-events`) replace the 30-second polling of this change.
  Until then, a balance changed on another server can be up to 30 seconds old on this server.
- **Velocity `/pay` and cross-server commands.** Commands that move money between players belong to later changes; the
  bridge only exposes what Vault exposes.
- **Offline-mode servers.** Servers that do not authenticate players with Mojang use UUIDs that the Mojang lookup does
  not know. Their accounts and name lookups are not covered by this change.
- **Multiple currencies through Vault.** Vault serves one currency per provider. The bridge serves only the default
  currency; other currencies come with their own change.
- **Changing the default currency at runtime.** The bridge reads the default currency at startup; a change of default
  currency needs a restart of the servers that use the bridge.
