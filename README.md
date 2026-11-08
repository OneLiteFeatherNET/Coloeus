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

## License

AGPL-3.0, see [LICENSE](LICENSE).
