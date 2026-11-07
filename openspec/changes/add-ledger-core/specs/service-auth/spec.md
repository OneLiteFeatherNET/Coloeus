# Spec Delta

## Purpose
Only known services and admins reach the ledger, with rights per currency.

## ADDED Requirements

### Requirement: Entra ID tokens
If a request to `/v1/**` carries no bearer token, an expired token, a token from another tenant or issuer, or a token without the Coloeus audience, then the service SHALL answer 401.

#### Scenario: Foreign tenant
- **GIVEN** a token signed for another tenant
- **WHEN** it is used on `GET /v1/currencies`
- **THEN** the service answers 401

### Requirement: App roles
The service SHALL require the app role `Coloeus.Client` or `Coloeus.Admin` for every `/v1/**` write, `Coloeus.Client`, `Coloeus.Moderator` or `Coloeus.Admin` for reads, and `Coloeus.Admin` for currency and client management, and SHALL answer 403 `FORBIDDEN_ROLE` otherwise.

#### Scenario: Client manages currencies
- **GIVEN** a token with only `Coloeus.Client`
- **WHEN** it creates a currency
- **THEN** the service answers 403

#### Scenario: Moderator books
- **GIVEN** a token with only `Coloeus.Moderator`
- **WHEN** it posts a transfer
- **THEN** the service answers 403 `FORBIDDEN_ROLE`

### Requirement: Client registry and grants
When an admin registers a client by its Entra client ID and grants it permissions per currency (`MINT`, `BURN`, `TRANSFER_ON_BEHALF`, `READ`), the service SHALL authorize that client's requests by the token's `azp` against these grants; a client without registry entry SHALL be able to read but not write. Every write, also by an admin, SHALL come from a registered client. `SOURCE` and `SINK` accounts SHALL be usable only through `MINT` and `BURN`.

#### Scenario: Unregistered client writes
- **GIVEN** a valid `Coloeus.Client` token whose `azp` is not registered
- **WHEN** it posts a transfer
- **THEN** the service answers 403 `CLIENT_NOT_REGISTERED`

### Requirement: Acting for a player
If a client debits a player account in a `TRANSFER`, then the service SHALL require either the `TRANSFER_ON_BEHALF` grant with `actor` equal to the debited player or the `Coloeus.Admin` role.

#### Scenario: Paying for someone else
- **GIVEN** client `C` holds `TRANSFER_ON_BEHALF`
- **WHEN** `C` debits player A with `actor` set to player B
- **THEN** the service answers 403 `ACTOR_MISMATCH`

### Requirement: Required configuration
If tenant ID, audience or database URL is missing at startup, then the service SHALL fail to start with a message naming the missing setting.

#### Scenario: Missing audience
- **GIVEN** `COLOEUS_AUDIENCE` is unset
- **WHEN** the service starts
- **THEN** startup fails and the log names `COLOEUS_AUDIENCE`
