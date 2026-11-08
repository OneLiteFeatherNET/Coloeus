# Spec Delta

## Purpose
Staff sign in with Entra ID through the Nitro server of the staff UI (the BFF). The browser holds a session cookie only; tokens
never reach it. The session lives in Dragonfly, is shared by all replicas, and ends at logout, at Entra's single sign-out, or on
timeout. The backend has no part in the login and keeps no session.

## ADDED Requirements

### Requirement: Staff login start
Nitro SHALL serve `GET /admin/auth/login`, which stores a login state (state, nonce, PKCE verifier, return path) for 10 minutes and
redirects to the tenant's Entra authorization endpoint with the S256 challenge and the web client id. The scope SHALL be
`openid profile offline_access api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/Coloeus.Access`.

#### Scenario: Login starts
- **GIVEN** no session cookie
- **WHEN** a browser requests `GET /admin/auth/login?returnTo=/admin/konten`
- **THEN** the answer is 302 to the Entra authorization endpoint with `code_challenge_method=S256` and a new `state`
- **AND** the redirect URI is `COLOEUS_WEB_BASE_URL` + `/admin/auth/callback`
- **AND** a login state with that `state` exists in Dragonfly with a TTL of 10 minutes

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
Nitro SHALL serve `GET /admin/auth/callback`. It SHALL read and delete the login state of the `state` in one step (`GETDEL`), so that
a state is used once. It SHALL exchange the code with `COLOEUS_WEB_CLIENT_SECRET` and the PKCE verifier. A refused login SHALL redirect
to `/admin/?error=login` without a session, and the reason SHALL be logged without any token.

#### Scenario: Replayed state
- **GIVEN** a callback with a `state` that was already used
- **WHEN** it arrives
- **THEN** the service answers with the `error=login` redirect and creates no session

### Requirement: Token validation at callback
The callback SHALL validate the ID token (signature against the tenant's JWKS, `iss` equal to the tenant issuer, `aud` equal to
`COLOEUS_WEB_CLIENT_ID`, `nonce`, `exp`, and the presence of `sid` and `oid`) and the access token (signature, `iss`, `aud` equal to
`COLOEUS_AUDIENCE`, the scope claim `scp` containing `Coloeus.Access`, and the `roles` claim). Only a login that passes both checks
SHALL create a session.

#### Scenario: Token for another audience
- **GIVEN** an access token whose `aud` is not `COLOEUS_AUDIENCE`
- **WHEN** the callback processes it
- **THEN** the service creates no session

#### Scenario: Application token
- **GIVEN** an access token without the scope claim `scp`, as a client-credential token has
- **WHEN** the callback processes it
- **THEN** the service creates no session

### Requirement: Staff roles gate the session
A login whose access token has neither the app role `Coloeus.Moderator` nor `Coloeus.Admin` SHALL create no session and SHALL
redirect to `/admin/?error=forbidden`. A user with `Coloeus.Admin` SHALL get the admin rights of `staff-api`, and a user with only
`Coloeus.Moderator` the read rights.

#### Scenario: User without a staff role
- **GIVEN** a valid sign-in of a user who has neither staff role
- **WHEN** the callback completes
- **THEN** no session is stored and the browser is redirected to `/admin/?error=forbidden`

#### Scenario: Moderator signs in
- **GIVEN** a valid sign-in of a user with `Coloeus.Moderator` only
- **WHEN** the callback completes
- **THEN** a session with the role `Coloeus.Moderator` is stored and the browser is redirected to the return path

### Requirement: Session cookie and session record
After a login Nitro SHALL set the cookie `__Host-coloeus_staff`, holding at least 256 random bits, with `HttpOnly`, `Secure`,
`SameSite=Lax` and `Path=/`. Dragonfly SHALL store the session under a key derived from the HMAC-SHA256 of the cookie value with
`COLOEUS_WEB_SESSION_SECRET`, never under the cookie value itself. The access, refresh and ID tokens SHALL be stored only in
encrypted form in that record. They SHALL NOT be sent to the browser.

#### Scenario: Cookie attributes
- **WHEN** the callback sets the session cookie
- **THEN** the `Set-Cookie` header contains `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/` and no `Domain`

#### Scenario: Store holds no cookie value
- **WHEN** every key in Dragonfly under `staff:` is listed after a login
- **THEN** no key and no stored value contains the cookie value in clear text

### Requirement: Session expiry
A session SHALL expire 8 hours after login (absolute) and 30 minutes after its last request under `/admin/api` (idle), both computed
with the injected clock of the Nitro server. The Redis TTL of the session record SHALL be the shorter of the two. An expired session
SHALL be answered 401 `SESSION_REQUIRED`. No daily job is needed.

#### Scenario: Idle timeout
- **GIVEN** a session whose last request was 31 minutes ago (clock injected in the test)
- **WHEN** it calls `GET /admin/api/me`
- **THEN** the answer is 401 `SESSION_REQUIRED`

#### Scenario: Absolute lifetime
- **GIVEN** a session created 8 hours and 1 second ago that was used a minute ago
- **WHEN** it calls `GET /admin/api/me`
- **THEN** the answer is 401 `SESSION_REQUIRED`

### Requirement: Token refresh
Before a call is forwarded to the backend, Nitro SHALL refresh the access token with the refresh token when fewer than two minutes of
its life remain, and store the new tokens encrypted. A failed refresh SHALL delete the session and answer 401 `SESSION_REQUIRED`.

#### Scenario: Refresh before forwarding
- **GIVEN** a session whose access token expires in one minute
- **WHEN** it calls `GET /admin/api/accounts/search?name=Steve`
- **THEN** Nitro refreshes the token first and forwards the call with the new token

#### Scenario: Refresh refused
- **GIVEN** a session whose refresh token Entra has revoked
- **WHEN** it calls any `/admin/api` route
- **THEN** the session is deleted and the answer is 401 `SESSION_REQUIRED`

### Requirement: Logout
`POST /admin/auth/logout` SHALL delete the caller's session and expire the cookie, and SHALL pass the CSRF check of this spec. It SHALL
answer 200 with `{"logoutUrl": …}`, the tenant's Entra `end_session` endpoint with the stored ID token as `id_token_hint` and
`post_logout_redirect_uri` `COLOEUS_WEB_BASE_URL` + `/admin/`. A `GET` on the same path SHALL be answered 405.

#### Scenario: Logout
- **GIVEN** a logged-in staff member with a valid CSRF token and a matching `Origin`
- **WHEN** it calls `POST /admin/auth/logout`
- **THEN** the session record is gone, the cookie expires, and `logoutUrl` names the tenant's `end_session` endpoint

#### Scenario: Logout by GET
- **WHEN** a browser sends `GET /admin/auth/logout`
- **THEN** the answer is 405 and nothing is deleted

### Requirement: Front-channel logout
`GET /admin/auth/frontchannel-logout` with `iss` and `sid` SHALL delete every session listed under the `sid` set of that value, when `iss`
is the tenant's issuer. It SHALL need no cookie and SHALL answer 200 with `Cache-Control: no-store`.

#### Scenario: Front-channel logout of a session
- **GIVEN** two sessions of one user with the same `sid` and one session with another `sid`
- **WHEN** Entra calls `GET /admin/auth/frontchannel-logout?iss=<tenant issuer>&sid=<that sid>`
- **THEN** exactly the two sessions with that `sid` are deleted

#### Scenario: Foreign issuer
- **WHEN** the endpoint is called with an `iss` other than the tenant's issuer
- **THEN** no session is deleted and the answer is 400 `REQUEST_INVALID`

### Requirement: CSRF protection of staff writes
Every `POST`, `PUT` and `DELETE` under `/admin/api`, and `POST /admin/auth/logout`, SHALL carry the header `X-CSRF-Token` equal (in
constant time) to the session's CSRF token, and an `Origin` equal to `COLOEUS_WEB_BASE_URL`. Nitro SHALL check both before the body is
read and before anything is forwarded. Otherwise the answer SHALL be 403 `CSRF_INVALID`. `GET /admin/api/me` returns the token.

#### Scenario: Missing token
- **GIVEN** a valid session
- **WHEN** it calls `POST /admin/api/clients` without `X-CSRF-Token`
- **THEN** the answer is 403 `CSRF_INVALID` and the backend receives no request

#### Scenario: Cross-site post
- **GIVEN** a valid session cookie and the correct token value
- **WHEN** a request arrives with `Origin: https://evil.example`
- **THEN** the answer is 403 `CSRF_INVALID`

### Requirement: Staff session and service tokens are separate
A staff session SHALL NOT authorize a `/v1` service call, and a service bearer token SHALL NOT authorize a `/admin/api` call. A
delegated staff token SHALL authorize only `/v1/staff/**` on the backend, as `staff-api` specifies.

#### Scenario: Session cookie on the ledger API
- **GIVEN** a valid staff session cookie and no bearer token
- **WHEN** a client calls `GET /v1/currencies` on the backend
- **THEN** the answer is 401 `UNAUTHORIZED`, because the backend never reads the cookie

#### Scenario: Service token on the staff API
- **GIVEN** a valid `Coloeus.Admin` client-credential token of a service
- **WHEN** it calls `GET /admin/api/accounts/search?name=Steve` on Nitro
- **THEN** the answer is 401 `SESSION_REQUIRED`

### Requirement: Statelessness of the login
The login state, the session and the CSRF token SHALL be kept in Dragonfly only, so any Nitro replica completes a login started on
another replica and serves any session. A replica SHALL keep no session state in memory.

#### Scenario: Callback on another replica
- **GIVEN** a login started on replica A
- **WHEN** the callback reaches replica B
- **THEN** replica B creates the session, and no request needs replica A
