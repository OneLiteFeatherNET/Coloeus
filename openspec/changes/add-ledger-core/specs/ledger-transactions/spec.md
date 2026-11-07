# Spec Delta

## Purpose
How money moves: transfer, mint, burn, batch; journal invariants; history.

## ADDED Requirements

### Requirement: Balanced journal
The service SHALL store every money movement as a transaction of at least two postings whose amounts sum to zero per currency, and SHALL never update or delete a stored transaction or posting.

#### Scenario: Unbalanced batch
- **GIVEN** a batch whose postings sum to `+1.00 COINS`
- **WHEN** a client submits it
- **THEN** the service answers 422 with code `UNBALANCED` and stores nothing

#### Scenario: Update attempt in the database
- **GIVEN** a stored posting
- **WHEN** the service's database role executes an `UPDATE` on `posting`
- **THEN** the database rejects it

### Requirement: Transfer
When a client sends a `TRANSFER` of a positive amount from account A to account B, the service SHALL debit A and credit B atomically; if A's balance would fall below zero, then the service SHALL reject the whole transaction with `INSUFFICIENT_FUNDS`.

#### Scenario: Successful transfer
- **GIVEN** A holds 10.00 and B holds 0.00 `COINS`
- **WHEN** a client transfers 4.00 `COINS` from A to B
- **THEN** A holds 6.00, B holds 4.00 and one transaction with two postings exists

#### Scenario: Insufficient funds
- **GIVEN** A holds 3.00 `COINS`
- **WHEN** a client transfers 4.00 `COINS` from A to B
- **THEN** the service answers 422 `INSUFFICIENT_FUNDS` and no balance changes

### Requirement: Mint and burn
When a client holding the `MINT` grant for a currency mints an amount to a player, the service SHALL credit the player and debit the client's source account; when a client holding the `BURN` grant burns an amount from a player, the service SHALL debit the player (rejecting with `INSUFFICIENT_FUNDS` if the balance would fall below zero) and credit the client's sink account.

#### Scenario: Mint without grant
- **GIVEN** client `C` has no `MINT` grant for `COINS`
- **WHEN** `C` mints 5.00 `COINS`
- **THEN** the service answers 403 `FORBIDDEN_GRANT`, books nothing and logs the attempt

### Requirement: Batch
When a client submits a `BATCH` of postings that sum to zero per currency, the service SHALL book all postings or none.

#### Scenario: One leg fails
- **GIVEN** a batch that debits A by more than A holds
- **WHEN** a client submits it
- **THEN** no posting of the batch is stored

### Requirement: Amount validation
If an amount is zero, negative, or has more fraction digits than its currency allows, then the service SHALL reject it with `AMOUNT_INVALID` or `SCALE_EXCEEDED` and SHALL never round it.

#### Scenario: Too many fraction digits
- **GIVEN** `COINS` has 2 fraction digits
- **WHEN** a client transfers `"1.005"` `COINS`
- **THEN** the service answers 422 `SCALE_EXCEEDED`

### Requirement: Concurrency
While many transactions touch the same accounts at the same time, on one or several replicas, the service SHALL apply each exactly once, lose none, and finish without deadlock.

#### Scenario: Parallel transfers
- **GIVEN** A holds 100.00 `COINS`
- **WHEN** 100 transfers of 1.00 from A to B run in parallel across two service instances
- **THEN** A holds 0.00, B holds 100.00, and the sum of all balances in `COINS` is zero

### Requirement: Audit data
The service SHALL store with every transaction the client ID, the acting player or staff ID if given, the kind, the reason, an optional external reference and up to 16 metadata entries, and SHALL return them in history.

#### Scenario: Metadata limit
- **GIVEN** a transfer with 17 metadata entries
- **WHEN** a client submits it
- **THEN** the service answers 422 `METADATA_TOO_LARGE`

### Requirement: History
When a client reads an account's postings, the service SHALL return them newest first with counter account, kind, reason, actor and timestamp, and a cursor that continues exactly after the last returned posting.

#### Scenario: Pagination
- **GIVEN** an account with 30 postings
- **WHEN** a client reads 20 and then follows the cursor
- **THEN** the second page holds the remaining 10 without duplicates
