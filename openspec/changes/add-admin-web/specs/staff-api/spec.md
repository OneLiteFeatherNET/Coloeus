# Spec Delta

## Purpose
The contract between the backend (`/admin/api/**`) and the staff web UI. Both sides implement exactly this document: paths,
methods, query parameters, JSON shapes, status codes and problem codes. A field that is not listed does not exist.

## ADDED Requirements

### Requirement: Base path and media type
The staff API SHALL be served under `/admin/api` as JSON, and every error SHALL be an RFC 9457 problem document (see
`problem-details`).

#### Scenario: Unauthenticated call
- **GIVEN** no session cookie
- **WHEN** a client calls `GET /admin/api/me`
- **THEN** the answer is 401 `SESSION_REQUIRED`

### Requirement: Names and money in JSON
The staff API SHALL use camelCase names, lower-case UUIDs, ISO-8601 UTC instants with `Z` and `YYYY-MM-DD` dates. An
`AmountJson` `{"amount", "currency"}` SHALL carry a plain decimal string with exactly the currency's fraction digits;
aggregates SHALL carry a plain decimal string next to a currency code. Amounts SHALL NOT be JSON numbers.

#### Scenario: Money is a decimal string
- **GIVEN** a player balance of 12.5 in `COINS`, which has two fraction digits
- **WHEN** `GET /admin/api/accounts/{id}` is answered
- **THEN** the balance is `{"amount": "12.50", "currency": "COINS"}`

### Requirement: Lists and pagination
Every list answer SHALL be an object `{"items": [...]}`, with `nextCursor` (string or `null`) when paginated. `limit` SHALL be
an integer from 1 to 200, default 50. `cursor` SHALL be opaque: base64url without padding of `<createdAt>|<id>` of the last item,
the format of `/v1/accounts/{id}/postings`.

#### Scenario: Limit out of range
- **WHEN** a client requests `limit=500`
- **THEN** the answer is 400 `REQUEST_INVALID`

### Requirement: Idempotent staff writes
Every `POST` and `PUT` SHALL require the header `Idempotency-Key` (1 to 255 characters) and SHALL be idempotent in the scope
`staff:<oid>`, as `staff-operations` specifies. A missing header SHALL be 400 `IDEMPOTENCY_KEY_MISSING`; the same key with
another body SHALL be 409 `IDEMPOTENCY_CONFLICT`.

#### Scenario: Missing key
- **GIVEN** an admin session
- **WHEN** it calls `POST /admin/api/clients` without `Idempotency-Key`
- **THEN** the answer is 400 `IDEMPOTENCY_KEY_MISSING` and nothing is written

### Requirement: Roles of the staff API
A read SHALL need `Coloeus.Moderator` or `Coloeus.Admin`. A write SHALL need `Coloeus.Admin`, and a moderator's write SHALL be
403 `FORBIDDEN_ROLE`, before the body is read. Every write SHALL also pass the CSRF check of `staff-login`.

#### Scenario: Moderator writes
- **GIVEN** a session with `Coloeus.Moderator`
- **WHEN** it calls `POST /admin/api/clients` with a valid body, key and CSRF token
- **THEN** the answer is 403 `FORBIDDEN_ROLE` and no client exists

### Requirement: Reason of money writes
Every money write SHALL carry `reason`, a string of 1 to 255 characters that is not blank after trimming. Otherwise the answer
SHALL be 400 `REQUEST_INVALID` and nothing SHALL be written.

#### Scenario: Blank reason
- **GIVEN** an admin session
- **WHEN** it resets an account with reason `"   "`
- **THEN** the answer is 400 `REQUEST_INVALID`

### Requirement: Endpoints and roles
The staff API SHALL expose exactly the endpoints of the table below, each with the role named in its row.

```text
| # | Method and path                                     | Role         | Answer                         |
|---|-----------------------------------------------------|--------------|--------------------------------|
| 1 | `GET /admin/api/me`                                 | read         | 200 `Me`                       |
| 2 | `GET /admin/api/accounts/search`                    | read         | 200 `{items: AccountHit[]}`    |
| 3 | `GET /admin/api/accounts/{id}`                      | read         | 200 `AccountDetail`            |
| 4 | `GET /admin/api/accounts/{id}/postings`             | read         | 200 `{items: StaffPosting[], nextCursor}` |
| 5 | `POST /admin/api/accounts/{id}/reset`               | admin write  | 201 `StaffBooking`             |
| 6 | `POST /admin/api/accounts/{id}/adjust`              | admin write  | 201 `StaffBooking`             |
| 7 | `GET /admin/api/money-flow`                         | read         | 200 `MoneyFlow`                |
| 8 | `GET /admin/api/money-flow/series`                  | read         | 200 `MoneySeries`              |
| 9 | `GET /admin/api/clients`                            | read         | 200 `{items: ClientView[]}`    |
| 10 | `POST /admin/api/clients`                          | admin write  | 201 `ClientView`               |
| 11 | `PUT /admin/api/clients/{clientId}/grants`         | admin write  | 200 `ClientView`               |
| 12 | `GET /admin/api/currencies`                        | read         | 200 `{items: Currency[]}`      |
| 13 | `POST /admin/api/currencies`                       | admin write  | 201 `Currency`                 |
| 14 | `PUT /admin/api/currencies/{code}/default`         | admin write  | 200 `Currency`                 |
| 15 | `GET /admin/api/staff-actions`                     | read         | 200 `{items: StaffAction[], nextCursor}` |
```

The session routes (`/admin/auth/login`, `/admin/auth/callback`, `/admin/auth/logout`,
`/admin/auth/frontchannel-logout`) are specified in `staff-login`.

#### Scenario: Moderator writes
- **GIVEN** a session with `Coloeus.Moderator`
- **WHEN** it calls `POST /admin/api/clients` with a valid body, key and CSRF token
- **THEN** the answer is 403 `FORBIDDEN_ROLE` and no client exists

### Requirement: JSON shapes
The staff API SHALL answer and accept exactly the shapes below.

```
Me {
  oid:              string   // Entra object id of the user
  name:             string   // display name
  roles:            string[] // subset of ["Coloeus.Moderator", "Coloeus.Admin"]
  canWrite:         boolean  // true when roles contains "Coloeus.Admin"
  csrfToken:        string   // send as X-CSRF-Token on writes
  sessionExpiresAt: instant  // absolute expiry (login + 8 h)
  idleExpiresAt:    instant  // last request + 30 min, refreshed by every call under /admin/api
}

AccountHit {
  uuid:      string        // player UUID
  name:      string | null // the matched name (search by name) or the latest name (search by uuid)
  lastSeen:  instant | null // last_seen of that register row; null if the UUID has no register row
  accountId: string | null // the PLAYER account of the UUID; null while the player has no account
}

AccountDetail {
  id:                string
  kind:              "PLAYER" | "BANK" | "SOURCE" | "SINK" | "FEE" | "ADMIN_SOURCE" | "ADMIN_SINK"
  owner:             { uuid: string, name: string | null } | null  // PLAYER only
  client:            { clientId: string, displayName: string } | null // SOURCE and SINK only
  currency:          string | null // FEE, ADMIN_SOURCE, ADMIN_SINK only
  balances:          AmountJson[]  // one per currency of the currency table, ordered by code, zero included
  latestPostingAt:   instant | null
}

StaffPosting {
  id:               string
  transactionId:    string
  kind:             "TRANSFER" | "MINT" | "BURN" | "BATCH" | "RESET" | "ADJUST"
  reason:           string | null
  actor:            string | null  // the actor as stored; for staff writes the oid
  actorName:        string | null  // metadata "staff_name" of a staff transaction, else null
  currency:         string
  amount:           string         // signed from this account's view, plain decimal, may be negative
  counterAccounts:  string[]       // other accounts of the same transaction
  createdAt:        instant
}

StaffBooking {
  transactionId: string
  kind:          "RESET" | "ADJUST"
  accountId:     string
  createdAt:     instant
  movements:     AmountJson[]   // one per currency, signed from the PLAYER account's view
}

MoneyFlow {
  window:   "7D" | "ALL"
  from:     instant | null      // now - 7 days for "7D"; null for "ALL"
  to:       instant             // the time of the request
  currencies: [ {
    currency:      string
    created:       string      // all clients, in the window, plain decimal, >= 0
    destroyed:     string      // all clients, in the window, >= 0
    netInWindow:   string      // created - destroyed, may be negative
    circulation:   string      // current sum of PLAYER balances, not depending on window, may be negative
  } ]
  clients:  [ {
    clientId:    string
    displayName: string
    currencies:  [ { currency: string, created: string, destroyed: string, netInWindow: string } ]
  } ]
  staff:    [ {
    currency:    string
    credited:    string      // ADMIN_SOURCE debited in the window, >= 0
    debited:     string      // ADMIN_SINK credited in the window, >= 0
    netInWindow: string      // credited - debited
  } ]
}

MoneySeries {
  currency: string
  days:     [ { date: string, created: string, destroyed: string } ]  // oldest first, every day zero-filled
}

ClientView {
  clientId:    string
  displayName: string
  createdAt:   instant
  grants:      [ { currency: string, permission: "MINT" | "BURN" | "TRANSFER_ON_BEHALF" | "READ" } ]  // [] when none
}

Currency {
  code:            string   // 3 to 8 upper-case letters
  nameSingular:    string
  namePlural:      string
  symbol:          string
  fractionDigits:  integer  // 0 to 8
  defaultCurrency: boolean
  createdAt:       instant
  updatedAt:       instant
}

StaffAction {
  id:            string
  actorOid:      string
  actorName:     string | null
  action:        "RESET" | "ADJUST" | "CLIENT_REGISTERED" | "GRANTS_REPLACED" | "CURRENCY_CREATED" | "CURRENCY_DEFAULT_SET"
  target:        string   // account id, client id or currency code
  transactionId: string | null  // set for RESET and ADJUST
  detail:        object   // action specific, for example {"reason": "...", "currency": "COINS"}
  createdAt:     instant
}
```

#### Scenario: Account detail shape
- **GIVEN** a `SOURCE` account of client `C`
- **WHEN** its detail is read
- **THEN** the answer has `client` `{clientId, displayName}`, `owner` `null` and `currency` `null`

### Requirement: Session identity
`GET /admin/api/me` SHALL return the `Me` shape of the caller's session. `csrfToken` SHALL be the token the writes of
this session require.

#### Scenario: Admin asks for identity
- **GIVEN** a session with `Coloeus.Admin`
- **WHEN** it calls `GET /admin/api/me`
- **THEN** the answer is 200 with `roles` `["Coloeus.Admin"]`, `canWrite` `true` and a non-empty `csrfToken`

### Requirement: Account search by name or UUID
`GET /admin/api/accounts/search` SHALL take exactly one of the query parameters `name` or `uuid` and SHALL return the matching
`AccountHit` items from the player name register, never calling the Mojang profile API. Zero matches SHALL be 200 with empty `items`.

#### Scenario: Name with history
- **GIVEN** UUID A held the name `Steve` and UUID B now holds `steve`
- **WHEN** a moderator calls `GET /admin/api/accounts/search?name=STEVE`
- **THEN** the answer lists both UUIDs, B first (newer `lastSeen`), with at most 20 items in total

#### Scenario: Unknown name
- **WHEN** it calls `GET /admin/api/accounts/search?name=Nobody`
- **THEN** the answer is 200 with `items` `[]`, and no request to Mojang is made

#### Scenario: Both parameters
- **WHEN** it calls `GET /admin/api/accounts/search?name=Steve&uuid=…`
- **THEN** the answer is 400 `REQUEST_INVALID`

#### Scenario: Malformed name
- **WHEN** it calls `GET /admin/api/accounts/search?name=Steve%21` (1 to 16 characters of `A-Z`, `a-z`, `0-9` and `_` are allowed)
- **THEN** the answer is 400 `REQUEST_INVALID`

### Requirement: Account detail
`GET /admin/api/accounts/{id}` SHALL return `AccountDetail`. Its `balances` SHALL contain one `AmountJson` per currency that
exists, zero where the account has no balance row. An unknown id SHALL be 404 `ACCOUNT_NOT_FOUND`. The endpoint SHALL NOT
create an account.

#### Scenario: Balances in every currency
- **GIVEN** a player account with 12.50 `COINS` and no other balance, and currencies `COINS` and `GEMS`
- **WHEN** a moderator calls its detail
- **THEN** `balances` contains `{"amount":"12.50","currency":"COINS"}` and `{"amount":"0","currency":"GEMS"}`

#### Scenario: Unknown account
- **WHEN** it calls the detail of an id that does not exist
- **THEN** the answer is 404 `ACCOUNT_NOT_FOUND`

### Requirement: Account postings
`GET /admin/api/accounts/{id}/postings` SHALL return the postings of the account, newest first (`createdAt` then `id`
descending), as `StaffPosting` items with `limit` and `cursor` as in the conventions. The cursor SHALL be the same format
as `/v1`. An unknown account SHALL be 404 `ACCOUNT_NOT_FOUND`.

#### Scenario: Second page
- **GIVEN** an account with 3 postings and `limit=2`
- **WHEN** the client follows `nextCursor` once
- **THEN** the second page has one item and `nextCursor` `null`

#### Scenario: Staff transaction shows its staff member
- **GIVEN** a `RESET` transaction whose metadata has `staff_name` `Alex`
- **WHEN** the postings of the reset player are listed
- **THEN** that posting has `kind` `RESET`, `actor` set to the oid and `actorName` `Alex`

### Requirement: Reset of a player account
`POST /admin/api/accounts/{id}/reset` (admin) SHALL book, in one transaction, the whole positive balance of a PLAYER account in
the given currency, or in every currency with a positive balance when `currency` is `null`, into the `ADMIN_SINK` of each
currency. Body: `{"currency": "COINS" | null, "reason": string}`. The answer is 201 `StaffBooking`.

#### Scenario: Reset one currency
- **GIVEN** a player with 12.50 `COINS` and 3.00 `GEMS`
- **WHEN** an admin resets `COINS` with reason `Dupe fixed`
- **THEN** the answer is 201 with `movements` `[{"amount":"-12.50","currency":"COINS"}]`, the player has 0 `COINS` and 3.00 `GEMS`, and the `ADMIN_SINK` of `COINS` gained 12.50

#### Scenario: Reset everything
- **GIVEN** the same player with `currency` `null`
- **WHEN** the admin resets with reason `Ban cleanup`
- **THEN** one transaction moves 12.50 `COINS` and 3.00 `GEMS`, and both balances are 0

### Requirement: Reset refusals and replays
The transaction of a reset SHALL have kind `RESET`, the caller's oid as `actor`, the reason and metadata `staff_name`, and SHALL
come with one `staff_action` row. An account that is not `PLAYER` SHALL be 400 `REQUEST_INVALID`, an unknown currency 422
`CURRENCY_UNKNOWN`, and nothing SHALL be written. The same key with the same body SHALL return the stored 201 answer.

#### Scenario: Nothing to reset
- **GIVEN** a player with no balance
- **WHEN** an admin resets with reason `Check`
- **THEN** the answer is 409 `NOTHING_TO_RESET` and no `staff_action` row exists

#### Scenario: Moderator tries to reset
- **WHEN** a moderator calls the reset
- **THEN** the answer is 403 `FORBIDDEN_ROLE` and nothing is written

#### Scenario: Retry after a timeout
- **GIVEN** a reset with key `K` that was booked
- **WHEN** the same request with key `K` is sent again
- **THEN** the answer is the stored 201 and no second transaction exists

### Requirement: Adjust of a player balance
`POST /admin/api/accounts/{id}/adjust` (admin) SHALL book one amount as a transaction of kind `ADJUST` with the caller's oid as
`actor`, and SHALL answer 201 `StaffBooking` with one movement. Body:
`{"currency": string, "direction": "CREDIT" | "DEBIT", "amount": string, "reason": string}`. A `CREDIT` books
`ADMIN_SOURCE` −amount and the player +amount; a `DEBIT` books the player −amount and `ADMIN_SINK` +amount.

#### Scenario: Credit
- **WHEN** an admin credits 10.00 `COINS` with reason `Event prize`
- **THEN** the answer is 201 with `movements` `[{"amount":"10.00","currency":"COINS"}]` and the player balance grew by 10.00

### Requirement: Adjust amounts and refusals
The amount SHALL be a plain decimal string, positive, with at most the fraction digits of the currency, and SHALL NOT be
rounded. A `DEBIT` that would take the player below zero SHALL be 422 `INSUFFICIENT_FUNDS` and write nothing. Amount and
currency errors use the ledger codes `AMOUNT_INVALID`, `SCALE_EXCEEDED` and `CURRENCY_UNKNOWN`.

#### Scenario: Debit beyond the balance
- **GIVEN** a player with 5.00 `COINS`
- **WHEN** an admin debits 6.00 `COINS`
- **THEN** the answer is 422 `INSUFFICIENT_FUNDS` and the balance stays 5.00

#### Scenario: Too many fraction digits
- **WHEN** an admin credits `12.505` in a currency with two fraction digits
- **THEN** the answer is 422 `SCALE_EXCEEDED` and nothing is rounded or written

### Requirement: Money flow
`GET /admin/api/money-flow?window=7D|ALL` (default `7D`) SHALL return `MoneyFlow` with the definitions of `staff-money-flow`:
created, destroyed, staff credits and debits per window, and circulation. Every registered client and every currency SHALL
appear, with zero values where there is no traffic. `from` is `to` minus 7 days for `7D` and `null` for `ALL`.

#### Scenario: Default window
- **GIVEN** a client that created 100 `COINS` eight days ago and 20 `COINS` today
- **WHEN** a moderator calls `GET /admin/api/money-flow`
- **THEN** `window` is `7D` and that client's `created` for `COINS` is `20`

#### Scenario: All time
- **GIVEN** the same postings
- **WHEN** a moderator calls `GET /admin/api/money-flow?window=ALL`
- **THEN** `from` is `null` and `created` for `COINS` is `120`

#### Scenario: Circulation identity
- **GIVEN** only `MINT` and `BURN` transactions and no staff actions
- **WHEN** the all-time flow is read
- **THEN** each currency's `circulation` equals its `created` minus its `destroyed`

#### Scenario: Unknown window
- **WHEN** a request uses `window=30D`
- **THEN** the answer is 400 `REQUEST_INVALID`

### Requirement: Daily series
`GET /admin/api/money-flow/series?currency=<code>&days=<1..366, default 7>` SHALL return `MoneySeries` with one entry per UTC
day from `today − (days − 1)` to today, oldest first, zero-filled. `created` and `destroyed` are computed as in `money-flow`
for the clients only, per day of `posting.created_at`. A missing `currency` or an out-of-range `days` SHALL be 400
`REQUEST_INVALID`; an unknown currency SHALL be 404 `CURRENCY_NOT_FOUND`.

#### Scenario: Zero-filled days
- **GIVEN** no traffic on two of the last seven days
- **WHEN** a moderator reads `days=7`
- **THEN** `days` has seven entries and the two quiet days have `"0"` for both values

### Requirement: Client registry
`GET /admin/api/clients` (read) SHALL return every client as `ClientView`. `POST /admin/api/clients` (admin) SHALL register a
client with body `{"clientId": string (UUID), "displayName": string (1 to 128 characters, not blank)}` and answer 201
`ClientView` without grants.

#### Scenario: Register and grant
- **WHEN** an admin registers `paper-lobby-1` with a UUID and replaces its grants with `MINT` and `READ` on `COINS`
- **THEN** the register answers 201, the grant answer contains both grants, and one `staff_action` row `CLIENT_REGISTERED` and one `GRANTS_REPLACED` exist

#### Scenario: Duplicate client
- **WHEN** a client with the same UUID is registered again
- **THEN** the answer is 409 `CLIENT_EXISTS`

#### Scenario: Moderator lists clients
- **WHEN** a moderator calls `GET /admin/api/clients`
- **THEN** the answer is 200 with `items`

### Requirement: Grant replacement
`PUT /admin/api/clients/{clientId}/grants` (admin) SHALL replace all grants of the client with the body
`{"grants": [{"currency": string, "permission": string}]}` and answer 200 `ClientView`. Permissions are `MINT`, `BURN`,
`TRANSFER_ON_BEHALF` and `READ`; an empty list removes all grants.

#### Scenario: Replace with an empty list
- **GIVEN** a client with `MINT` and `READ` on `COINS`
- **WHEN** an admin replaces its grants with `{"grants": []}`
- **THEN** the answer is 200 with `grants` `[]`

### Requirement: Client refusals
A client already registered SHALL be 409 `CLIENT_EXISTS`. A malformed UUID, an unknown permission, a duplicate pair or a
malformed entry SHALL be 400 `REQUEST_INVALID`. An unknown currency SHALL be 422 `CURRENCY_UNKNOWN`, and an unknown client 404
`CLIENT_NOT_FOUND`.

#### Scenario: Unknown client grants
- **WHEN** an admin replaces the grants of a client id that is not registered
- **THEN** the answer is 404 `CLIENT_NOT_FOUND`

### Requirement: Currencies
`GET /admin/api/currencies` (read) SHALL return every currency as `Currency`. `POST /admin/api/currencies` (admin) SHALL create a
currency with the ledger's rules (body `{"code", "nameSingular", "namePlural", "symbol", "fractionDigits"}`) and answer 201
`Currency`. `PUT /admin/api/currencies/{code}/default` (admin) SHALL make the currency the single default and answer 200 with it.

#### Scenario: Create currency
- **WHEN** an admin creates `GEMS` with two fraction digits
- **THEN** the answer is 201 with `defaultCurrency` `false`, and a `staff_action` row `CURRENCY_CREATED` exists

#### Scenario: Change default
- **GIVEN** `COINS` is the default
- **WHEN** an admin sets `GEMS` as default
- **THEN** the answer shows `GEMS` with `defaultCurrency` `true`, and `COINS` is no longer the default

### Requirement: Currency refusals
A taken code SHALL be 409 `CURRENCY_EXISTS`; invalid values SHALL be 400 `REQUEST_INVALID`; an unknown code in the default
endpoint SHALL be 404 `CURRENCY_NOT_FOUND`. Setting the current default again SHALL be 200. Changing the default SHALL clear
the previous default in the same transaction.

#### Scenario: Taken code
- **GIVEN** currency `GEMS` exists
- **WHEN** an admin creates `GEMS` again
- **THEN** the answer is 409 `CURRENCY_EXISTS`

### Requirement: Staff action log
`GET /admin/api/staff-actions` (read) SHALL return `StaffAction` items newest first (`createdAt` then `id` descending), with the
conventions for `limit` and `cursor`. Every staff write SHALL have exactly one row, written in the same database transaction.

#### Scenario: Log after an adjust
- **WHEN** an admin adjusts a balance and a moderator then reads the log
- **THEN** the first item has `action` `ADJUST`, `actorOid` of the admin, `actorName` and a `transactionId`

#### Scenario: Failed write leaves no row
- **WHEN** an adjust is refused with `INSUFFICIENT_FUNDS`
- **THEN** no `staff_action` row for it exists

### Requirement: Staff errors
Every error of `/admin/api` SHALL be a problem document whose `code` is one of the codes listed below, with the status shown.
Unexpected failures SHALL be 500 `INTERNAL`, with a `detail` that names no cause, table or host.

```text
SESSION_REQUIRED 401    FORBIDDEN_ROLE 403       CSRF_INVALID 403          IDEMPOTENCY_KEY_MISSING 400
IDEMPOTENCY_CONFLICT 409   REQUEST_INVALID 400    ACCOUNT_NOT_FOUND 404     CURRENCY_NOT_FOUND 404
CURRENCY_UNKNOWN 422    CURRENCY_EXISTS 409      CLIENT_EXISTS 409         CLIENT_NOT_FOUND 404
AMOUNT_INVALID 422      SCALE_EXCEEDED 422       INSUFFICIENT_FUNDS 422    NOTHING_TO_RESET 409
INTERNAL 500
```

#### Scenario: Unexpected failure
- **WHEN** the database is unreachable during a detail read
- **THEN** the answer is 500 with `code` `INTERNAL` and a `detail` that names no table or host
