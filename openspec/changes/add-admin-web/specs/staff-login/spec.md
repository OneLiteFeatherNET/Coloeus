# Spec Delta

## Purpose
Staff sign in with Entra ID through the backend. The browser holds a session cookie only; tokens never reach it, and a
session ends at logout, at Entra's single sign-out, or on timeout.

## ADDED Requirements

### Requirement: Staff login start
The service SHALL serve `GET /admin/auth/login`, which stores a login state (state, nonce, PKCE verifier) for 10 minutes and
redirects to the tenant's Entra authorization endpoint with the S256 challenge and the web client id. The scope SHALL be
`openid profile` and `api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/Coloeus.Access`.

#### Scenario: Login starts
- **GIVEN** no session cookie
- **WHEN** a browser requests `GET /admin/auth/login?returnTo=/admin/konten`
- **THEN** the answer is 302 to the Entra authorization endpoint with `code_challenge_method=S256` and a new `state`
- **AND** the redirect URI is `COLOEUS_WEB_BASE_URL` + `/admin/auth/callback`
- **AND** a `staff_login` row with that state exists

#### Scenario: Foreign return path
- **WHEN** a browser requests `GET /admin/auth/login?returnTo=https://example.org/`
- **THEN** the service starts the login and stores `/admin/` as the return path

### Requirement: Return path
The `returnTo` value SHALL be used as the return path only when it is a path under `/admin/`, at most 512 characters long, with no
scheme, no host and no leading `//`. Any other value SHALL be replaced by `/admin/`.

#### Scenario: Protocol-relative path
- **WHEN** a browser requests `GET /admin/auth/login?returnTo=//example.org/admin/`
- **THEN** the stored return path is `/admin/`

### Requirement: Callback validates the login
The service SHALL serve `GET /admin/auth/callback`. It SHALL look up the `staff_login` row of the `state` and delete it in the same
step, so that a state is used once. It SHALL exchange the code with `COLOEUS_WEB_CLIENT_SECRET` and the PKCE verifier. A refused
login SHALL redirect to `/admin/?error=login` without a session, and the reason SHALL be logged without any token.

#### Scenario: Replayed state
- **GIVEN** a callback with a `state` that was already used
- **WHEN** it arrives
- **THEN** the service answers with the `error=login` redirect and creates no session

### Requirement: Token validation at callback
The callback SHALL validate the ID token (signature against the tenant's JWKS, `iss`, `aud` equal to `COLOEUS_WEB_CLIENT_ID`,
`nonce`, `exp`) and the access token (signature, `iss`, `aud` equal to `COLOEUS_AUDIENCE`). Only a login that passes both
checks SHALL create a session.

#### Scenario: Token for another audience
- **GIVEN** an access token whose `aud` is not `COLOEUS_AUDIENCE`
- **WHEN** the callback processes it
- **THEN** the service creates no session

### Requirement: Staff roles gate the session
A login whose access token has neither the app role `Coloeus.Moderator` nor `Coloeus.Admin` SHALL create no session and SHALL
redirect to `/admin/?error=forbidden`. A user with `Coloeus.Admin` SHALL get the admin rights of `staff-api`, and a user with only
`Coloeus.Moderator` the read rights.

#### Scenario: User without a staff role
- **GIVEN** a valid sign-in of a user who has neither staff role
- **WHEN** the callback completes
- **THEN** no `staff_session` row is created and the browser is redirected to `/admin/?error=forbidden`

#### Scenario: Moderator signs in
- **GIVEN** a valid sign-in of a user with `Coloeus.Moderator` only
- **WHEN** the callback completes
- **THEN** a session with the role `Coloeus.Moderator` is created and the browser is redirected to the return path

### Requirement: Session cookie
After a login the service SHALL set the cookie `__Host-coloeus_staff`, holding at least 256 random bits, with `HttpOnly`,
`Secure`, `SameSite=Lax` and `Path=/`. The database SHALL store only the SHA-256 hash of the value. Entra tokens SHALL NOT be
stored in the browser or in the database.

#### Scenario: Cookie attributes
- **WHEN** the callback sets the session cookie
- **THEN** the `Set-Cookie` header contains `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/` and no `Domain`
- **AND** the database contains no value that equals the cookie value

### Requirement: Session expiry
A session SHALL expire 8 hours after login (absolute) and 30 minutes after its last request under `/admin/api` (idle), both
computed with the injected `java.time.Clock`. An expired session SHALL be answered 401 `SESSION_REQUIRED`, and its row SHALL be
deleted. A daily job SHALL delete all expired sessions and login states.

#### Scenario: Idle timeout
- **GIVEN** a session whose last request was 31 minutes ago (clock injected in the test)
- **WHEN** it calls `GET /admin/api/me`
- **THEN** the answer is 401 `SESSION_REQUIRED`

#### Scenario: Absolute lifetime
- **GIVEN** a session created 8 hours and 1 second ago that was used a minute ago
- **WHEN** it calls `GET /admin/api/me`
- **THEN** the answer is 401 `SESSION_REQUIRED`

### Requirement: Logout
`POST /admin/auth/logout` SHALL delete the caller's session, expire the cookie and pass the CSRF check. It SHALL answer 200 with
`{"logoutUrl": …}`, the tenant's Entra `end_session` endpoint with `post_logout_redirect_uri` `COLOEUS_WEB_BASE_URL` + `/admin/`.
A `GET` on the same path SHALL be answered 405.

#### Scenario: Logout
- **GIVEN** a logged-in staff member with a valid CSRF token and a matching `Origin`
- **WHEN** it calls `POST /admin/auth/logout`
- **THEN** the session row is gone, the cookie expires, and `logoutUrl` names the tenant's `end_session` endpoint

#### Scenario: Logout by GET
- **WHEN** a browser sends `GET /admin/auth/logout`
- **THEN** the answer is 405 and nothing is deleted

### Requirement: Front-channel logout
`GET /admin/auth/frontchannel-logout` with `iss` and `sid` SHALL delete every `staff_session` row whose `sid` equals the parameter,
when `iss` is the tenant's issuer. It SHALL need no cookie and SHALL answer 200 with `Cache-Control: no-store`.

#### Scenario: Front-channel logout of a session
- **GIVEN** two sessions of one user with the same `sid` and one session with another `sid`
- **WHEN** Entra calls `GET /admin/auth/frontchannel-logout?iss=<tenant issuer>&sid=<that sid>`
- **THEN** exactly the two sessions with that `sid` are deleted

#### Scenario: Foreign issuer
- **WHEN** the endpoint is called with an `iss` other than the tenant's issuer
- **THEN** no session is deleted and the answer is 400 `REQUEST_INVALID`

### Requirement: CSRF protection of staff writes
Every `POST`, `PUT` and `DELETE` under `/admin/api`, and `POST /admin/auth/logout`, SHALL carry the header `X-CSRF-Token` equal
(in constant time) to the session's token and an `Origin` equal to `COLOEUS_WEB_BASE_URL`. Otherwise the answer SHALL be 403
`CSRF_INVALID`, given before the body is read and before anything changes. `GET /admin/api/me` returns the token.

#### Scenario: Missing token
- **GIVEN** a valid session
- **WHEN** it calls `POST /admin/api/clients` without `X-CSRF-Token`
- **THEN** the answer is 403 `CSRF_INVALID` and nothing is written

#### Scenario: Cross-site post
- **GIVEN** a valid session cookie and the correct token value
- **WHEN** a request arrives with `Origin: https://evil.example`
- **THEN** the answer is 403 `CSRF_INVALID`

### Requirement: Staff session and service tokens are separate
A staff session SHALL NOT authorize any `/v1` request, and a service bearer token SHALL NOT authorize any `/admin/api` request.

#### Scenario: Session cookie on the ledger API
- **GIVEN** a valid staff session cookie and no bearer token
- **WHEN** it calls `GET /v1/currencies`
- **THEN** the answer is 401 `UNAUTHORIZED`

#### Scenario: Bearer token on the staff API
- **GIVEN** a valid `Coloeus.Admin` bearer token of a service
- **WHEN** it calls `GET /admin/api/me`
- **THEN** the answer is 401 `SESSION_REQUIRED`

### Requirement: Statelessness of the login
The login state, the session and the CSRF token SHALL be kept in Postgres only, so any replica completes a login started on
another replica and serves any session.

#### Scenario: Callback on another replica
- **GIVEN** a login started on replica A
- **WHEN** the callback reaches replica B
- **THEN** replica B creates the session and no request needs replica A
