# Spec Delta

## Purpose
Retries never move money twice.

## ADDED Requirements

### Requirement: Key required
If a write request has no `Idempotency-Key` header, then the service SHALL reject it with 400 `IDEMPOTENCY_KEY_MISSING`.

#### Scenario: Missing key
- **GIVEN** a valid transfer body
- **WHEN** a client posts it without `Idempotency-Key`
- **THEN** the service answers 400 and books nothing

### Requirement: Replay
When a client repeats a write with the same `Idempotency-Key` and the same body within the retention period, the service SHALL return the original status and body without booking again.

#### Scenario: Retry after timeout
- **GIVEN** client `C` transferred 5.00 with key `K`
- **WHEN** `C` sends the same request with key `K` again
- **THEN** the response equals the first one and only one transaction exists

#### Scenario: Concurrent duplicates
- **GIVEN** no transaction with key `K`
- **WHEN** client `C` sends the same request with key `K` twice at the same moment to different replicas
- **THEN** exactly one transaction exists and both responses are equal

### Requirement: Conflicting reuse
If a client reuses an `Idempotency-Key` with a different body, then the service SHALL answer 409 `IDEMPOTENCY_CONFLICT`.

#### Scenario: Different amount
- **GIVEN** client `C` transferred 5.00 with key `K`
- **WHEN** `C` sends a transfer of 6.00 with key `K`
- **THEN** the service answers 409 and books nothing

### Requirement: Scope and retention
The service SHALL scope keys per client and keep them for at least 7 days.

#### Scenario: Same key, different clients
- **GIVEN** client `C1` used key `K`
- **WHEN** client `C2` sends a different request with key `K`
- **THEN** the request of `C2` is booked normally
