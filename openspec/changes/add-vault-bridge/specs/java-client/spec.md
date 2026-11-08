# Spec Delta

## Purpose
The Java client for the Coloeus API in module `client` (package `net.onelitefeather.coloeus.client`): authenticated calls,
money as `MonetaryAmount`, typed errors from Problem Details, and bounded timeouts. The Vault bridge is its first user;
later services reuse it.

## ADDED Requirements

### Requirement: Entra client-credentials token
The client SHALL obtain its bearer token with the OAuth 2.0 client-credentials grant from
`https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token` for the configured scope, and SHALL reuse the token until
60 seconds before its expiry.

#### Scenario: Token reuse
- **GIVEN** a token that expires in 3600 seconds was fetched
- **WHEN** the client makes two calls within the next 10 minutes
- **THEN** the token endpoint is called once

#### Scenario: Token near expiry
- **GIVEN** a cached token that expires in 30 seconds
- **WHEN** the client makes a call
- **THEN** the client fetches a new token before sending the call

### Requirement: Single token refresh
While a token refresh is running, the client SHALL let other calls wait for that refresh instead of starting their own.

#### Scenario: Concurrent calls at expiry
- **GIVEN** 20 threads call the client at the moment the token expires
- **WHEN** the calls run concurrently against a fake token endpoint
- **THEN** the token endpoint receives one request

### Requirement: Bearer authentication and idempotency header
The client SHALL send the bearer token on every call, and SHALL send an `Idempotency-Key` header on every write call with
the key given by the caller.

#### Scenario: Write call
- **GIVEN** the caller gives the key `k-1` for a transaction
- **WHEN** the client posts the transaction
- **THEN** the request carries `Authorization: Bearer <token>` and `Idempotency-Key: k-1`

### Requirement: Money as MonetaryAmount
The client's public API SHALL express every amount as `javax.money.MonetaryAmount` with `MathContext.DECIMAL128`, SHALL
convert amounts to and from JSON decimal strings only inside its JSON mapper, and SHALL NOT expose `double` or
`BigDecimal` amounts in public signatures.

#### Scenario: Balance answer
- **GIVEN** the server answers the balance `{"amount":"12.50","currency":"COINS"}`
- **WHEN** the client reads the balance
- **THEN** the caller receives a `MonetaryAmount` of 12.50 `COINS`

### Requirement: Typed errors from Problem Details
When the server answers with an RFC 9457 problem document, the client SHALL throw a typed exception selected by the
`code` member: `InsufficientFundsException`, `ForbiddenGrantException`, `PlayerUnknownException`,
`NameLookupUnavailableException` or `AuthenticationException` for these codes, and `ColoeusProblemException` carrying
`code`, `status` and `detail` for every other code.

#### Scenario: Known code
- **GIVEN** the server answers 422 with `code` `INSUFFICIENT_FUNDS`
- **WHEN** the client receives the answer
- **THEN** the client throws `InsufficientFundsException`

#### Scenario: Unknown code
- **GIVEN** the server answers 409 with `code` `CURRENCY_EXISTS`
- **WHEN** the client receives the answer
- **THEN** the client throws `ColoeusProblemException` with code `CURRENCY_EXISTS` and status 409

### Requirement: Unavailable server
If a call times out, fails to connect or receives a 5xx answer without a problem document, then the client SHALL throw
`ColoeusUnavailableException`.

#### Scenario: Timeout
- **GIVEN** the server does not answer within the timeout given for the call
- **WHEN** the client calls the server
- **THEN** the client throws `ColoeusUnavailableException` after the timeout and not later

### Requirement: Write retry
When a write call throws `ColoeusUnavailableException` and the time left in the caller's budget allows it, the client
SHALL send the same request once more with the same `Idempotency-Key`, and SHALL NOT retry any other error.

#### Scenario: Retry within budget
- **GIVEN** a write with a budget of 1 s whose first attempt timed out after 500 ms
- **WHEN** the client retries
- **THEN** the retry uses the same `Idempotency-Key` and a timeout of no more than 500 ms

#### Scenario: No retry without budget
- **GIVEN** a write whose first attempt timed out after 900 ms of a 1 s budget
- **WHEN** the client decides on a retry
- **THEN** the client sends no retry and throws `ColoeusUnavailableException`

### Requirement: Per-call timeouts
The client SHALL accept a timeout for every call, and the default timeout of every call SHALL be 500 ms, so that a caller
on a server's main thread can rely on the bound.

#### Scenario: Timeout parameter
- **GIVEN** a balance read with a timeout of 500 ms
- **WHEN** the server answers after 800 ms
- **THEN** the client throws `ColoeusUnavailableException`

### Requirement: Client calls
The client SHALL provide calls for `GET /v1/currencies` (the default currency), `GET /v1/players/by-name/{name}`,
`PUT /v1/players/{uuid}/name`, `GET /v1/accounts/player/{uuid}`, `GET /v1/accounts/{id}/balances` and
`POST /v1/transactions` for the kinds `MINT`, `BURN` and `TRANSFER`, and SHALL return the balances that the
transaction response contains.

#### Scenario: Transaction answer
- **GIVEN** the server answers a `MINT` with the resulting balances of the player and the source account
- **WHEN** the client returns the answer
- **THEN** the caller receives the resulting balance of the player without a further call
