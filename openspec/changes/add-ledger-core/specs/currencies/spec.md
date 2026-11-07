# Spec Delta

## Purpose
Which currencies exist, how precise they are, and which one Vault serves.

## ADDED Requirements

### Requirement: Create a currency
When a caller with the app role `Coloeus.Admin` creates a currency with a new code, name singular, name plural, symbol and fraction digits between 0 and 8, the service SHALL store it and list it in `GET /v1/currencies`.

#### Scenario: New currency
- **GIVEN** no currency with code `COINS` exists
- **WHEN** an admin creates `COINS` with 2 fraction digits
- **THEN** `GET /v1/currencies` contains `COINS` with 2 fraction digits

#### Scenario: Duplicate code
- **GIVEN** currency `COINS` exists
- **WHEN** an admin creates `COINS` again
- **THEN** the service answers 409 with code `CURRENCY_EXISTS`

### Requirement: Fixed precision
The service SHALL reject every attempt to change the fraction digits of an existing currency.

#### Scenario: Change fraction digits
- **GIVEN** `COINS` has 2 fraction digits
- **WHEN** an admin updates `COINS` to 3 fraction digits
- **THEN** the service answers 409 with code `CURRENCY_SCALE_IMMUTABLE` and `COINS` keeps 2

### Requirement: Default currency
The service SHALL keep exactly one default currency once at least one currency exists, and marking a currency as default SHALL unmark the previous one.

#### Scenario: Switch default
- **GIVEN** `COINS` is the default and `GEMS` exists
- **WHEN** an admin marks `GEMS` as default
- **THEN** `GEMS` is the only default currency

### Requirement: JSR 354 currency units
The service SHALL expose every stored currency as a `javax.money.CurrencyUnit` through its `CurrencyProviderSpi`, so `Monetary.getCurrency("COINS")` resolves inside the service, including currencies created after startup.

#### Scenario: Currency created at runtime
- **GIVEN** the service is running
- **WHEN** an admin creates `GEMS`
- **THEN** `Monetary.getCurrency("GEMS")` returns a unit with the stored fraction digits on every replica within one second
