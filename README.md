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

## License

AGPL-3.0, see [LICENSE](LICENSE).
