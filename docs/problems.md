# Problem codes

Every error answer of the service is an RFC 9457 problem document (`application/problem+json`) with these members:

| Member   | Meaning                                                             |
|----------|---------------------------------------------------------------------|
| `type`   | `urn:coloeus:problem:<CODE>`, identifies the problem type           |
| `title`  | Short, fixed text for the code                                      |
| `status` | HTTP status of the answer                                           |
| `detail` | Explanation for this request; do not parse it                       |
| `code`   | Stable machine-readable code; clients branch on this               |

The codes below are stable. A new code is added here in the same change that uses it.

## REQUEST_INVALID

- Status: 400
- Title: Request is invalid
- Meaning: A field of the request is missing, blank, too long or outside its allowed range, for example a currency
  code that is not 3 to 8 upper case letters or fraction digits outside 0 to 8.

## AMOUNT_INVALID

- Status: 422
- Title: Amount is invalid
- Meaning: An amount is zero or negative, is not a plain decimal string, or has more significant digits than the
  service holds (34). Amounts are never rounded, so such an amount is rejected instead.

## SCALE_EXCEEDED

- Status: 422
- Title: Amount has too many fraction digits
- Meaning: An amount has more fraction digits than its currency allows (for example `12.505` in a currency with two
  fraction digits). Send the amount with at most the currency's fraction digits.

## CURRENCY_UNKNOWN

- Status: 422
- Title: Currency is unknown
- Meaning: An amount refers to a currency code that the service does not define.

## CURRENCY_NOT_FOUND

- Status: 404
- Title: Currency not found
- Meaning: The currency code in the path does not exist.

## CURRENCY_EXISTS

- Status: 409
- Title: Currency already exists
- Meaning: A currency with this code is already defined. Codes are never reused.

## CURRENCY_SCALE_IMMUTABLE

- Status: 409
- Title: Fraction digits of a currency cannot change
- Meaning: An update asks for other fraction digits than the currency has. Fraction digits are fixed when the
  currency is created, because existing amounts were stored with them.

## ACCOUNT_NOT_FOUND

- Status: 404
- Title: Account not found
- Meaning: The account id in the path or in a transaction does not exist. Player accounts exist for every player
  UUID once they have been read or used, so a player account is never reported missing.

## ACCOUNT_FORBIDDEN

- Status: 403
- Title: Account may not be used in this request
- Meaning: The request names a SOURCE or SINK account directly. These accounts are only used through MINT (SOURCE)
  and BURN (SINK) of the calling client, so a client cannot book outside its own minting and burning.

## INSUFFICIENT_FUNDS

- Status: 422
- Title: Insufficient funds
- Meaning: The transaction would take a player balance below zero. Nothing of the transaction is stored.

## UNBALANCED

- Status: 422
- Title: Postings do not sum to zero
- Meaning: The postings of a transaction do not sum to zero in every currency, for example a batch that debits 1.00
  and credits 2.00. Nothing is stored.

## METADATA_TOO_LARGE

- Status: 422
- Title: Too many metadata entries
- Meaning: A transaction has more than 16 metadata entries. Metadata values must be strings.

## IDEMPOTENCY_KEY_MISSING

- Status: 400
- Title: Idempotency-Key header is missing
- Meaning: A write request has no Idempotency-Key header. Writes are only booked with a key, so that a retry after a
  timeout never moves money twice.

## IDEMPOTENCY_CONFLICT

- Status: 409
- Title: Idempotency key was used with another request
- Meaning: The same Idempotency-Key was sent with a different body than the first request that used it. Nothing is
  booked; use a new key for a new request.

## UNAUTHORIZED

- Status: 401
- Title: Authentication required
- Meaning: The request carries no bearer token, or the token is expired, signed by an unknown key, issued by another
  tenant or issuer, or meant for another audience. The `WWW-Authenticate` header names the reason when a token was sent.

## FORBIDDEN_ROLE

- Status: 403
- Title: Required role is missing
- Meaning: The token is valid, but it lacks the app role the route requires (`Coloeus.Client`, `Coloeus.Admin` or
  `Coloeus.Moderator`, see the route's description in the OpenAPI document).

## FORBIDDEN_GRANT

- Status: 403
- Title: Client has no grant for this permission
- Meaning: The calling client is registered, but it holds no grant for the permission on this currency. An admin adds
  the grant with `PUT /v1/clients/{id}/grants`.

## CLIENT_NOT_REGISTERED

- Status: 403
- Title: Client is not registered
- Meaning: The calling client (the token's `azp`) is not in the client registry. Such a client may read but not write.
  An admin registers it with `POST /v1/clients`.

## ACTOR_MISMATCH

- Status: 403
- Title: Actor is not the debited player
- Meaning: A transfer debits a player account, but the `actor` of the request is not that player, and the caller is not
  an admin. A client may only act for the player who asks it to.

## CLIENT_EXISTS

- Status: 409
- Title: Client already exists
- Meaning: A client with this client ID is already registered. Registration is not an update; change grants instead.

## CLIENT_NOT_FOUND

- Status: 404
- Title: Client not found
- Meaning: No client with this client ID is registered, so its grants cannot be replaced.

## PLAYER_UNKNOWN

- Status: 404
- Title: Player name is unknown
- Meaning: The name is not in the service's register, and the Mojang profile API does not know it either.

## NAME_LOOKUP_UNAVAILABLE

- Status: 503
- Title: Name lookup is unavailable
- Meaning: The name is not in the register and the Mojang profile API failed or did not answer in time. The service
  stores nothing and answers no guess; retry later.

## SESSION_REQUIRED

- Status: 401
- Title: Staff session is required
- Meaning: A request under `/admin/api` has no valid staff session: the cookie is missing, the session expired (8 hours after the login or 30 minutes idle), or it was ended by logout. The staff UI sends the person to the login. Answered by the Nitro server of the staff UI, never by the backend.

## CSRF_INVALID

- Status: 403
- Title: CSRF check failed
- Meaning: A staff write (`POST`, `PUT`, `DELETE` under `/admin/api`, or the logout) lacks the `X-CSRF-Token` header of its session, or its `Origin` header is not the address of the staff UI. Nothing is forwarded to the backend. Answered by the Nitro server of the staff UI.

## NOT_FOUND

- Status: 404
- Title: Endpoint is not found
- Meaning: A path under `/admin/api` names no endpoint of the staff API for this method. Nothing is forwarded to the backend. Answered by the Nitro server of the staff UI.

## INTERNAL

- Status: 500
- Title: Internal error
- Meaning: An unexpected failure happened on the server, or a backend could not be reached or did not answer in time. The `detail` names no cause, table, host or secret; the request may be retried, and a write is safe to retry with the same `Idempotency-Key`.
