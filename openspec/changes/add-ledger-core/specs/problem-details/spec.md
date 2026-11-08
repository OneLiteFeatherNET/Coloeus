# Spec Delta

## Purpose
Errors that clients can turn into player messages.

## ADDED Requirements

### Requirement: Problem Details
When the service rejects a request, it SHALL answer with `application/problem+json` (RFC 9457) containing `type`, `title`, `status` and a stable machine-readable `code`, and SHALL document every code in `docs/problems.md`.

#### Scenario: Insufficient funds
- **GIVEN** a transfer that exceeds the payer's balance
- **WHEN** the service rejects it
- **THEN** the body has `status` 422 and `code` `INSUFFICIENT_FUNDS`

#### Scenario: Undocumented code
- **GIVEN** a code is used in the service
- **WHEN** the test suite runs
- **THEN** a test fails if the code is missing from `docs/problems.md`
