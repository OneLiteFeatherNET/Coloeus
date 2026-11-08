# Coloeus

Coloeus is the economy service of the OneLiteFeather Minecraft network. One central, stateless service serves Paper (through a Vault bridge), Velocity, Minestom and other services. Money is kept in Postgres as an append-only double-entry journal, and every write is atomic and idempotent.

## Modules

| Module     | Purpose                                                              |
|------------|----------------------------------------------------------------------|
| `backend`  | Micronaut HTTP service: the economy API, ledger and persistence.     |
| `client`   | Java client library for the Coloeus API.                             |
| `paper`    | Paper plugin that bridges Vault economy calls to Coloeus.            |
| `velocity` | Velocity proxy plugin for economy commands and reporting.            |
| `web`      | Staff web interface for moderators and administrators.               |

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

### Staff API

The staff API under `/v1/staff` serves the staff web interface (`web/`). Its Nitro server is the only client: it signs
the staff members in, keeps their sessions and forwards their calls. The backend keeps no session, reads no cookie and
serves no UI file. Each call carries the bearer token of the staff member, a delegated user token of the staff web app
registration with the scope `Coloeus.Access`.

- Client-credential tokens are refused on `/v1/staff` with 401 `UNAUTHORIZED`, as are tokens without `oid`.
- Reads (`GET`) need the app role `Coloeus.Moderator` or `Coloeus.Admin`. Writes (`POST`, `PUT`) need `Coloeus.Admin`;
  a moderator's write is answered 403 `FORBIDDEN_ROLE` before its body is read.
- Every write needs an `Idempotency-Key` header. Keys are scoped per staff member (`staff:<oid>`), so two staff members
  may use the same key.
- The actor of a write is the `oid` of the token and its display name the `name` claim. Money writes are journal
  transactions of the kinds `RESET` and `ADJUST`; every staff write also has a row in `staff_action`.
- The staff web app's client id is never a registered ledger client. Its tokens therefore cannot write on `/v1`
  (`CLIENT_NOT_REGISTERED`). Never register that client id, neither through `/v1/clients` nor through `/v1/staff/clients`.

The endpoints and JSON shapes are specified in `openspec/changes/add-admin-web/specs/staff-api/spec.md`; the paths of
`/v1/staff` are part of the OpenAPI document at `/swagger/coloeus.yml`. The staff API reads no setting of its own.

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
