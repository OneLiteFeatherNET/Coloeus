# Spec Delta

## Purpose
Five codes are added for the staff web interface. Two are answered by the Nitro server of the UI, two by the backend, and one by Nitro
for unknown routes. Each is documented in `docs/problems.md` in the change that uses it, and the browser sees the same problem format
from both sides.

## ADDED Requirements

### Requirement: Session and CSRF problem codes
Nitro SHALL answer `SESSION_REQUIRED` (401, a `/admin/api` call without a valid staff session) and `CSRF_INVALID` (403, a staff write
without a valid `X-CSRF-Token` or with a foreign `Origin`), each as an RFC 9457 problem document. The backend SHALL NOT answer these codes.

#### Scenario: Expired session
- **GIVEN** a staff session whose idle time has passed
- **WHEN** the UI calls `GET /admin/api/me`
- **THEN** the answer is 401 with `code` `SESSION_REQUIRED` and `type` `urn:coloeus:problem:SESSION_REQUIRED`

#### Scenario: Missing CSRF token
- **GIVEN** a valid staff session
- **WHEN** it posts without `X-CSRF-Token`
- **THEN** the answer is 403 with `code` `CSRF_INVALID` and nothing is forwarded

### Requirement: Backend staff problem codes
The backend SHALL answer `NOTHING_TO_RESET` (409) when a reset finds no positive balance, writing nothing. It SHALL answer `INTERNAL` (500)
for unexpected failures, with a `detail` that names no cause, table, host or secret.

#### Scenario: Reset of an empty account
- **GIVEN** a player account without balance
- **WHEN** an admin resets it through `/v1/staff/accounts/{id}/reset`
- **THEN** the answer is 409 with `code` `NOTHING_TO_RESET`

#### Scenario: Unexpected failure
- **WHEN** the database is unreachable during a staff read on the backend
- **THEN** the answer is 500 with `code` `INTERNAL` and a `detail` without host or table names

### Requirement: Unknown route code
Nitro SHALL answer `NOT_FOUND` (404) for a path under `/admin/api/` that names no endpoint of `staff-api`, as an RFC 9457 problem document,
without forwarding the request.

#### Scenario: Unknown endpoint
- **WHEN** a signed-in admin calls `DELETE /admin/api/clients`
- **THEN** the answer is 404 with `code` `NOT_FOUND`, and the backend receives no request

### Requirement: One problem format on both sides
Nitro SHALL pass the backend's problem document through unchanged, with its status and body. Nitro SHALL answer its own refusals and
unexpected failures in the same format (`type`, `title`, `status`, `detail`, `code`), with `application/problem+json`.

#### Scenario: Backend refusal passed through
- **WHEN** the backend answers 422 `INSUFFICIENT_FUNDS` to a forwarded adjust
- **THEN** the browser receives the same status and the same problem document

### Requirement: Documented staff codes
Every staff problem code (`SESSION_REQUIRED`, `CSRF_INVALID`, `NOT_FOUND`, `NOTHING_TO_RESET`, `INTERNAL`) SHALL appear in `docs/problems.md`
with its status and title, in the same change that adds it. The backend test of `docs/problems.md` covers the backend codes; the UI's test
covers the Nitro codes.

#### Scenario: Documentation test
- **WHEN** the test of `docs/problems.md` runs
- **THEN** `NOTHING_TO_RESET` and `INTERNAL` are present with their statuses, and the Nitro test finds `SESSION_REQUIRED`, `CSRF_INVALID` and `NOT_FOUND` with theirs
