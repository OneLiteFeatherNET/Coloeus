# Spec Delta

## Purpose
Show where money comes from and where it goes: per client and currency, and per staff action. The figures are computed
from the append-only journal; nothing is stored twice.

## ADDED Requirements

### Requirement: Created and destroyed money per client
For a currency and a window, a client's `created` SHALL be minus the sum of the postings of its `SOURCE` account, and its
`destroyed` SHALL be the sum of the postings of its `SINK` account, both by `posting.created_at` in the window. `netInWindow` SHALL
be `created − destroyed`.

#### Scenario: Mint and burn in the window
- **GIVEN** client C mints 50 `COINS` and burns 20 `COINS` today
- **WHEN** the window `7D` is computed
- **THEN** C's `created` is `50`, `destroyed` is `20` and `netInWindow` is `30`

### Requirement: Staff money is a separate bucket
The debits of `ADMIN_SOURCE` and the credits of `ADMIN_SINK` in a window SHALL be reported as `staff.credited` and `staff.debited`.
They SHALL NOT be part of any client's `created` or `destroyed`.

#### Scenario: Staff money is separate
- **GIVEN** an admin credits 10 `COINS` by `ADJUST`
- **WHEN** the window is computed
- **THEN** `staff.credited` for `COINS` is `10` and no client's `created` changes

### Requirement: Circulation
The `circulation` of a currency SHALL be the sum of the balances of all `PLAYER` accounts now. It SHALL NOT depend on the window.

#### Scenario: Circulation does not depend on the window
- **GIVEN** 40 `COINS` held by players and a mint of 40 eight days ago
- **WHEN** the window `7D` and the window `ALL` are computed
- **THEN** `circulation` is `40` in both

### Requirement: Circulation identity
Until `FEE` and `BANK` accounts hold money, the all-time circulation of a currency SHALL equal `created − destroyed +
staff.credited − staff.debited`, summed over all clients and the staff bucket. The tests SHALL check this identity on a fixed set
of transactions.

#### Scenario: Identity holds for mixed traffic
- **GIVEN** mints, burns, a transfer between players, a staff credit of 5 and a staff debit of 2
- **WHEN** the all-time flow is read
- **THEN** circulation equals `created − destroyed + 5 − 2`, and the transfer changes neither side

### Requirement: Windows
The service SHALL support the windows `7D` and `ALL`. `7D` covers postings with `created_at` at or after the request time minus
seven days and not after the request time, at instant precision. `ALL` covers every posting.

#### Scenario: Posting older than seven days
- **GIVEN** a mint eight days ago
- **WHEN** the window `7D` is computed
- **THEN** that mint is not in `created`, and it is in `created` for `ALL`

### Requirement: Daily series in UTC
The daily series SHALL group client postings of `created` and `destroyed` by the UTC date of `posting.created_at` and SHALL return
every day of the range, zero-filled, oldest first.

#### Scenario: Day boundary in UTC
- **GIVEN** a mint at 23:30 UTC on 7 November
- **WHEN** the series for that currency is read
- **THEN** the mint is counted on 7 November and not on 8 November

### Requirement: Money flow is read-only
The money-flow views SHALL NOT write anything and SHALL NOT create accounts. A missing account is absent from the sums.

#### Scenario: Read creates nothing
- **GIVEN** a client without `SOURCE` account
- **WHEN** a moderator reads the flow
- **THEN** the client is listed with zeros and no `SOURCE` account is created

### Requirement: AMW is not computed
The term "AMW" from the requirements is not defined in this change. The service SHALL NOT compute an AMW figure or name an AMW
field. A later change that defines AMW adds it as a new view over the same postings.

#### Scenario: No AMW field
- **WHEN** the money-flow answer is read
- **THEN** it contains no member named `amw`
