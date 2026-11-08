# Spec Delta

## Purpose
One network-wide account per player, system accounts, and balances.

## ADDED Requirements

### Requirement: Automatic player account
When a caller accesses a player UUID that has no account, the service SHALL create a player account with balance zero in every currency, whether or not the player was ever online.

#### Scenario: Never-seen player
- **GIVEN** UUID `U` has no account
- **WHEN** a client requests `GET /v1/accounts/player/U`
- **THEN** an account for `U` exists and all its balances are `0` with the currency's fraction digits

#### Scenario: Concurrent first access
- **GIVEN** UUID `U` has no account
- **WHEN** two requests for `U` arrive at the same time on different replicas
- **THEN** exactly one account for `U` exists

### Requirement: Balances
When a client reads an account's balances, the service SHALL return every currency's balance as a decimal string with exactly the currency's fraction digits, together with the account's latest journal position.

#### Scenario: Read balances
- **GIVEN** player `U` holds 12.5 `COINS` (2 fraction digits)
- **WHEN** a client requests `GET /v1/accounts/player/U/balances`
- **THEN** the response contains `{"amount": "12.50", "currency": "COINS"}`

### Requirement: System accounts
The service SHALL create the source and sink account of a client and the fee and admin accounts of a currency on first use, and SHALL never let a client book directly to another client's source or sink account.

#### Scenario: First mint of a client
- **GIVEN** client `C` has never minted
- **WHEN** `C` mints 10 `COINS` to a player
- **THEN** `SOURCE(C)` exists with balance `-10.00 COINS`
