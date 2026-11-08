# Spec Delta

## Purpose
Four codes are added for the staff web interface. Each is documented in `docs/problems.md` in the change that uses it.

## ADDED Requirements

### Requirement: Session and CSRF problem codes
The service SHALL answer `SESSION_REQUIRED` (401, a `/admin/api` call without a valid staff session) and `CSRF_INVALID` (403,
a staff write without a valid `X-CSRF-Token` or with a foreign `Origin`), each as an RFC 9457 problem document.

#### Scenario: Expired session
- **GIVEN** a staff session whose idle time has passed
- **WHEN** the UI calls `GET /admin/api/me`
- **THEN** the answer is 401 with `code` `SESSION_REQUIRED` and `type` `urn:coloeus:problem:SESSION_REQUIRED`

#### Scenario: Missing CSRF token
- **GIVEN** a valid staff session
- **WHEN** it posts without `X-CSRF-Token`
- **THEN** the answer is 403 with `code` `CSRF_INVALID` and nothing is changed

### Requirement: Reset and internal problem codes
The service SHALL answer `NOTHING_TO_RESET` (409) when a reset finds no positive balance, writing nothing. It SHALL answer
`INTERNAL` (500) for unexpected failures, with a `detail` that names no cause, table, host or secret.

#### Scenario: Reset of an empty account
- **GIVEN** a player account without balance
- **WHEN** an admin resets it
- **THEN** the answer is 409 with `code` `NOTHING_TO_RESET`

#### Scenario: Unexpected failure
- **WHEN** the database is unreachable during a staff read
- **THEN** the answer is 500 with `code` `INTERNAL` and a `detail` without host or table names

### Requirement: Documented staff codes
Every staff problem code SHALL appear in `docs/problems.md` with its status and title, in the same change that adds it.

#### Scenario: Documentation test
- **WHEN** the test of `docs/problems.md` runs
- **THEN** `SESSION_REQUIRED`, `CSRF_INVALID`, `NOTHING_TO_RESET` and `INTERNAL` are present with their statuses
