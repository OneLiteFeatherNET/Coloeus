# Coloeus

Coloeus is the economy service of the OneLiteFeather Minecraft network. One central, stateless service serves Paper (through a Vault bridge), Velocity, Minestom and other services. Money is kept in Postgres as an append-only double-entry journal, and every write is atomic and idempotent.

## Modules

| Module     | Purpose                                                              |
|------------|----------------------------------------------------------------------|
| `backend`  | Micronaut HTTP service: the economy API, ledger and persistence.     |
| `client`   | Java client library for the Coloeus API.                             |
| `paper`    | Paper plugin that bridges Vault economy calls to Coloeus.            |
| `velocity` | Velocity proxy plugin for economy commands and reporting.            |
| `web`      | Staff web UI (Nuxt 4) and its Nitro server, the BFF. Not a Gradle module; see below. |

## Development

Start the local Postgres database:

```shell
docker compose -f docker/docker-compose.yml up -d
```

Build and test all modules:

```shell
./gradlew build
```

Run the backend against the local database. The `dev` environment fills in placeholder values for the Entra settings
(see below), so no real token passes the bearer check there; set `COLOEUS_TENANT_ID` and `COLOEUS_AUDIENCE` to your
Entra dev tenant to call the authenticated routes locally:

```shell
MICRONAUT_ENVIRONMENTS=dev ./gradlew :backend:run
```

The OpenAPI document of the backend is served anonymously at `/swagger/coloeus.yml`.

### Backend configuration

All settings come from environment variables. Settings without a default stop the service at start-up, and the
message names the variable.

| Variable                    | Required | Default                    | Meaning                                                                                  |
|-----------------------------|----------|----------------------------|------------------------------------------------------------------------------------------|
| `COLOEUS_TENANT_ID`         | yes      | none (`dev`: placeholder)  | Entra tenant id. The signing keys (JWKS) and the issuer are derived from it.            |
| `COLOEUS_AUDIENCE`          | yes      | none (`dev`: placeholder)  | Client id of the API app registration; tokens must carry it in `aud`.                   |
| `DATASOURCES_DEFAULT_URL`   | yes      | none (`dev`: localhost)    | JDBC URL of the Postgres database.                                                      |
| `DATASOURCES_DEFAULT_USERNAME` | yes   | none (`dev`: `coloeus`)    | Database user.                                                                          |
| `DATASOURCES_DEFAULT_PASSWORD` | yes   | none                       | Database password (for local runs: the one in `docker/docker-compose.yml`).                                                                      |
| `COLOEUS_MOJANG_URL`        | no       | `https://api.mojang.com`   | Base URL of the Mojang profile API used for name lookups (read timeout 2 s).            |
| `PORT`                      | no       | `8080`                     | HTTP port of the service.                                                               |

### Staff web UI (`web/`)

`web` is a pnpm project, not a Gradle module: the backend builds without Node. The staff UI runs as its own
service, `harbor.onelitefeather.dev/onelitefeather/coloeus-admin`, whose Nitro server logs staff in with Entra
(confidential client, PKCE), keeps the session in Dragonfly and forwards staff calls to the backend's `/v1/staff`.

```shell
cd web
pnpm install --frozen-lockfile
pnpm lint && pnpm typecheck && pnpm test && pnpm build
docker build -t coloeus-admin:dev .
```

The server reads these variables and refuses to start when one is missing or too short (the session secret needs
32 bytes). Development defaults exist only for `COLOEUS_BACKEND_URL` (`http://localhost:8080`) and
`COLOEUS_WEB_STORAGE_URL` (`redis://localhost:6379`):

| Variable                      | Meaning                                                       |
|-------------------------------|---------------------------------------------------------------|
| `COLOEUS_WEB_CLIENT_ID`       | Client id of the Entra app registration "Coloeus Admin Web".  |
| `COLOEUS_WEB_CLIENT_SECRET`   | Client secret of that registration.                           |
| `COLOEUS_WEB_BASE_URL`        | Public origin, for example `https://coloeus.apps.onelite.feather`. |
| `COLOEUS_WEB_SESSION_SECRET`  | At least 32 bytes. Rotating it ends every session.            |
| `COLOEUS_WEB_STORAGE_URL`     | Dragonfly URL; the ACL user is limited to `staff:*`.          |
| `COLOEUS_TENANT_ID`           | Entra tenant of the staff accounts.                           |
| `COLOEUS_AUDIENCE`            | Client id of the API registration that exposes `Coloeus.Access`. |
| `COLOEUS_BACKEND_URL`         | Base URL of the backend.                                      |

The web client must never be registered in the ledger's client registry: a delegated staff token of a registered
client could write around the staff audit log.

## Paper (Vault bridge)

The `paper` module is a Paper plugin that registers the default currency of Coloeus as the Vault economy of a server.
Shops, jobs and other plugins that use Vault then read and write the central balance, and a balance is the same on
every server of the network. The provider registers during plugin load, so shop and other economy plugins find it regardless of their load order.

### Install

1. Use a Paper 26.1 server on Java 25 and install [Vault](https://github.com/MilkBowl/Vault) in `plugins/`.
2. Copy `coloeus-paper-<version>.jar` from the GitHub release into `plugins/`.
3. Start the server once. It creates `plugins/Coloeus/config.yml`. Set `url`, `tenant-id` and `client-id` there, and
   set the secret in the environment variable `COLOEUS_CLIENT_SECRET` (see below).
4. Restart the server. The log line `Vault economy <CODE> registered at Highest priority` confirms the bridge. A missing
   value disables the plugin with a line that names it; the server then keeps its previous economy.

### Configuration

Each key of `plugins/Coloeus/config.yml` has an environment variable of the same meaning. The variable wins when it is
set and not blank.

| Config key      | Environment variable    | Required | Default                                                | Meaning                                              |
|-----------------|-------------------------|----------|--------------------------------------------------------|------------------------------------------------------|
| `url`           | `COLOEUS_URL`           | yes      | none                                                   | Base URL of the Coloeus API                          |
| `tenant-id`     | `COLOEUS_TENANT_ID`     | yes      | none                                                   | Entra tenant of the Coloeus API (GUID or domain)     |
| `client-id`     | `COLOEUS_CLIENT_ID`     | yes      | none                                                   | Client id of this server's Entra app registration    |
| `client-secret` | `COLOEUS_CLIENT_SECRET` | yes      | none                                                   | Secret of that app registration; prefer the variable |
| `auth-scope`    | `COLOEUS_AUTH_SCOPE`    | no       | `api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/.default`  | Scope of the access token                            |

### Register the server in Coloeus

Each server needs an Entra app registration with the app role `Coloeus.Client`. An admin then registers its client id
with Coloeus and grants it the rights of the bridge on the default currency (`COINS` below, use your default code):

```shell
# Register the client (admin token required)
curl -X POST "$COLOEUS_URL/v1/clients" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"clientId": "<client-id>", "displayName": "Paper lobby 1"}'

# Replace the grants of the client: MINT and BURN for deposits and withdrawals, READ for balances
curl -X PUT "$COLOEUS_URL/v1/clients/<client-id>/grants" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '[{"currency": "COINS", "permission": "MINT"},
       {"currency": "COINS", "permission": "BURN"},
       {"currency": "COINS", "permission": "READ"}]'
```

The grants replace all previous grants of the client. Without `MINT` a deposit answers `FAILURE`; without `BURN` a
withdrawal does; without `READ` balances cannot be read.

### Limits

- Banks are not supported: `hasBankSupport()` is `false` and bank calls answer `NOT_IMPLEMENTED`.
- A balance changed on another server can be up to 30 seconds old on this server.
- Deposits and withdrawals wait for Coloeus on the server thread, for at most one second per call.
- Only the default currency is served; the default currency is read at startup, so a change needs a restart.

## License

AGPL-3.0, see [LICENSE](LICENSE).
