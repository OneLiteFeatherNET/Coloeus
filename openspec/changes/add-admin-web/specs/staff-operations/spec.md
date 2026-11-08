# Spec Delta

## Purpose
The rules every staff write follows, whatever it changes: who is recorded, how a retry is answered, what a write may touch, and that
money writes keep the ledger's invariants. The writes are made by the backend under `/v1/staff/**`; the endpoints and shapes are in
`staff-api`, and the browser path through Nitro does not change any of these rules.

## ADDED Requirements

### Requirement: Attribution of money writes
Every money write of the staff API (`RESET`, `ADJUST`) SHALL be one journal transaction whose `actor` is the `oid` of the delegated token
that made the call, whose `reason` is the request's reason, and whose metadata contains `staff_name`, the `name` claim of that token.
It SHALL NOT have a `client_id`.

#### Scenario: Adjust is attributed
- **GIVEN** a delegated admin token with `oid` `O` and `name` `Alex`
- **WHEN** it credits 10 `COINS` with reason `Event prize`
- **THEN** the transaction has `actor` `O`, `reason` `Event prize`, `kind` `ADJUST`, metadata `staff_name` `Alex`, and no `client_id`

### Requirement: Staff action log
Every staff write (`RESET`, `ADJUST`, `CLIENT_REGISTERED`, `GRANTS_REPLACED`, `CURRENCY_CREATED`, `CURRENCY_DEFAULT_SET`) SHALL write exactly
one row to `staff_action` in the same database transaction as the write. Money writes SHALL set `transaction_id`.

#### Scenario: Reset is logged with its transaction
- **WHEN** an admin resets a player account
- **THEN** one `staff_action` row `RESET` exists with the id of the transaction that was booked

### Requirement: Configuration writes without postings
Configuration writes SHALL NOT set `transaction_id`, because they have no postings. This is a deliberate reading of "every staff write is a
journal transaction": a journal transaction without postings would break the balanced-journal rule. The maintainer confirms it in review.

#### Scenario: Grant change is logged without a transaction
- **WHEN** an admin replaces the grants of a client
- **THEN** one `staff_action` row `GRANTS_REPLACED` exists with `transaction_id` null, and no transaction was written

### Requirement: Insert-only staff action log
`staff_action` SHALL be insert-only: a database trigger SHALL reject `UPDATE` and `DELETE`, as for the journal.

#### Scenario: Insert-only log
- **WHEN** a statement tries to update a `staff_action` row
- **THEN** the database rejects it

### Requirement: Idempotency scope of staff writes
A staff write SHALL be idempotent in the scope `staff:<oid>`, where `oid` is the token's, with its key row written in the same database
transaction as the write. Migration `V2__admin_web.sql` SHALL backfill the scope of existing keys as `client:<client_id>` and SHALL keep their
stored answers.

#### Scenario: Same key, two staff members
- **GIVEN** admin A books an adjust with key `K`
- **WHEN** admin B sends an adjust with key `K` and another body
- **THEN** B's request is booked as a new write and answers 201

#### Scenario: Existing client keys survive the migration
- **GIVEN** an idempotency key of client `C` booked before `V2`
- **WHEN** `V2` runs
- **THEN** the row has scope `client:C` and the same stored answer

#### Scenario: Retry through the browser
- **GIVEN** the dialog of an adjust sent key `K` through Nitro and the request failed on the network
- **WHEN** the dialog sends the same request with the same key `K` again
- **THEN** the backend answers with the stored 201 and no second journal row exists

### Requirement: Booking against admin accounts only
Staff writes SHALL book player balances only against the `ADMIN_SOURCE` and `ADMIN_SINK` accounts of the currency. They SHALL NOT name or
book `SOURCE`, `SINK`, `FEE` or `BANK` accounts, and SHALL NOT need a client grant.

#### Scenario: Staff never books a client account
- **WHEN** a staff write is booked
- **THEN** every posting belongs to the player account or to `ADMIN_SOURCE` or `ADMIN_SINK` of the currency

### Requirement: Player accounts only
A reset or an adjust SHALL apply only to a `PLAYER` account. Any other account SHALL be refused with 400 `REQUEST_INVALID`.

#### Scenario: Reset of a SINK account
- **WHEN** an admin resets a `SINK` account
- **THEN** the answer is 400 `REQUEST_INVALID` and nothing is written

### Requirement: Balances stay non-negative under concurrency
A reset or a debit SHALL lock the player balance rows as the booking of `add-ledger-core` does (ordered `SELECT … FOR UPDATE`), and SHALL read
the balance it books in the same transaction. Concurrent transfers SHALL NOT take a balance below zero.

#### Scenario: Reset races a transfer
- **GIVEN** a player with 10 `COINS`
- **WHEN** a reset of `COINS` and a transfer of 10 `COINS` from the same player run concurrently on two application contexts
- **THEN** exactly one of them is booked, and the balance is never negative

### Requirement: Amount rules
Staff money amounts SHALL be plain decimal strings, positive, with at most the fraction digits of the currency, and SHALL NOT be rounded. A
violation is refused with `AMOUNT_INVALID` or `SCALE_EXCEEDED`.

#### Scenario: Amount with extra digits
- **WHEN** an adjust sends `1.005` in a currency with two fraction digits
- **THEN** the answer is 422 `SCALE_EXCEEDED` and nothing is written

### Requirement: Reset covers positive balances only
A reset SHALL book the whole positive balance of each requested currency, or of every currency with a positive balance when `currency` is
`null`. A reset with nothing to book SHALL be refused with 409 `NOTHING_TO_RESET` and write nothing.

#### Scenario: Only positive balances appear
- **GIVEN** a player with 5 `COINS` and 0 `GEMS`
- **WHEN** an admin resets with `currency` `null`
- **THEN** the postings contain `COINS` only

### Requirement: Logging of staff writes
The backend SHALL log every staff write at INFO with the action, the `oid`, the target and the transaction id. Nitro SHALL log the same
writes at INFO with the `oid` and the action. Neither SHALL log the session cookie, the CSRF token, the session secret, any Entra token,
or the reason text, which may name a player.

#### Scenario: No secrets in the log
- **WHEN** an adjust is booked
- **THEN** the backend log line contains the `oid` and the transaction id and no token, cookie or secret value
