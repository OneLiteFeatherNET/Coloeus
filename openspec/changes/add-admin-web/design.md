# Design

## Context

Coloeus is one central, stateless service in the OneLiteFeather Kubernetes cluster with at least two replicas and one
Postgres database (CloudNativePG). `add-ledger-core` builds the journal, accounts, currencies, clients and grants behind
`/v1`, which services call with Entra ID bearer tokens. Staff need the same data and the same actions through a browser,
with a named person behind every write. Requirements: Outline "Anforderungen: Coloeus"; the principles in
`openspec/config.yaml` (Postgres as the only store of money, idempotent writes, JSR 354 amounts, stateless replicas,
Entra for staff, OpenTelemetry) apply unchanged.

The staff UI is a separate service: a Nuxt 4 application whose Nitro server (Node) is the backend-for-frontend (BFF). It has
its own container image and its own deployment. The backend is a bearer-token API and serves no UI file. The maintainer
decided this split; the rest of the design follows from it.

## Goals / Non-Goals

**Goals:** staff log in with their Entra account and nothing else; the browser never holds a token; a stolen cookie is
useless after logout or after the idle timeout; two Nitro replicas share sessions; every staff write is attributed and
idempotent; the backend exposes staff resources only to a delegated staff token and never to an app token; the UI is built
from reusable components; the contract is complete enough for two teams to build in parallel.

**Non-Goals:** a player UI, real-time updates, currency editing, changes to ledger rules (see `proposal.md`).

## Decisions

### 1. Nitro is the BFF, the backend is a token API

The Nitro server of the `coloeus-admin` service is the OAuth client. It runs the login, holds the server-side session,
checks CSRF and roles, refreshes tokens and forwards staff calls to the backend with the staff member's delegated access
token. The browser sees one origin: `https://coloeus.apps.onelite.feather/admin/…`. The HTTPRoute sends `/admin` to Nitro
and every other path to the backend.

*Rejected: the backend as BFF (maintainer decision).* The backend would then own the Entra redirects, the cookie, the
session store, the CSRF rules and the UI files, and every UI release would touch the service that holds the money. Keeping
the backend a pure token API keeps its rules to one question: is this bearer token allowed on this route.
*Rejected: the public-client SPA login used by Apus and Corvus (`oidc-client-ts` in the browser, tokens in `localStorage`).*
The requirement asks for a confidential client, and a token in browser storage is readable by any script on the page.
*Rejected: Nitro with Postgres as session store.* It would give the UI runtime a credential for the money database for no
gain; the session data is not money.

This decision supersedes the first revision, which put the BFF into the backend and rejected a Nitro BFF for its second
runtime. The second runtime is now the point of the split.

### 2. Sessions in Dragonfly through Nitro storage

Sessions live in the cluster's Dragonfly (Redis protocol), accessed through Nitro storage with the unstorage redis driver.
Two replicas share them, and front-channel logout can find them by `sid`.

- The cookie `__Host-coloeus_staff` carries 256 bits of randomness. Nitro stores the session under the key
  `staff:session:<HMAC-SHA256(COLOEUS_WEB_SESSION_SECRET, cookie value)>`, so a copy of the store does not reveal a live cookie.
- The session record holds `oid`, display name, roles, `sid`, the CSRF token, `createdAt`, `lastSeenAt`, the absolute expiry
  and the idle expiry. It also holds the access, refresh and ID tokens, each encrypted with AES-256-GCM under a key derived
  from the session secret. Tokens are never sent to the browser and never written unencrypted.
- The record's Redis TTL is the smaller of the idle expiry and the absolute expiry, and every `/admin/api` call moves the
  idle expiry forward (absolute 8 hours from login, idle 30 minutes). Expired sessions disappear by TTL; no daily job is
  needed.
- Front-channel logout needs the sessions of a `sid`. Nitro keeps a set `staff:sid:<sid>` of session keys, written with each
  session and expired with the latest of them. Unstorage has no set operations, so the set is written through the redis
  client that the driver exposes.
- Login states are `staff:login:<state>` with a 10-minute TTL, read and deleted in one command (`GETDEL`) so a state is used once.

Cookie attributes: `HttpOnly; Secure; SameSite=Lax; Path=/`, no `Domain` (`__Host-`). `SameSite=Lax` lets the cookie travel
on the top-level redirect back from Entra and blocks it on cross-site `POST`s.

*Rejected: sessions in Postgres (the first revision).* Nitro would need a database credential (see decision 1). *Rejected: a
signed, stateless cookie.* Logout and front-channel logout could not end a session. *Rejected: in-memory sessions.* They
break with two replicas.

### 3. Login and token handling

`GET /admin/auth/login` writes a login state (state, nonce, PKCE verifier, return path, 10-minute TTL), and redirects to the
tenant's authorization endpoint with `response_type=code`, `code_challenge_method=S256`, the web client id and the scope
`openid profile offline_access api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/Coloeus.Access`. The return path is accepted only
as a path under `/admin/`, at most 512 characters, with no scheme, host or leading `//`.

`GET /admin/auth/callback` reads and deletes the login state by `state`, exchanges the code with
`COLOEUS_WEB_CLIENT_SECRET` and the PKCE verifier, and validates:

- the ID token: signature against the tenant JWKS, `iss` = tenant issuer, `aud` = `COLOEUS_WEB_CLIENT_ID`, `nonce`, `exp`,
  and the presence of `sid` and `oid`;
- the access token: signature, `iss`, `aud` = `COLOEUS_AUDIENCE`, the scope claim `scp` containing `Coloeus.Access`, and the
  `roles` claim.

A user with neither `Coloeus.Moderator` nor `Coloeus.Admin` gets no session and is redirected to `/admin/?error=forbidden`.
A refused login is redirected to `/admin/?error=login`. Otherwise the session is created and the cookie set.

Before a call is forwarded, Nitro refreshes the access token when fewer than two minutes remain, with the refresh token. If the
refresh fails, the session is deleted and the answer is 401 `SESSION_REQUIRED`. The roles are read from the access token at
login and kept for the session's life (see risks).

*Why `offline_access`:* the staff session lasts up to 8 hours, longer than an access token. The refresh token stays on the
server, encrypted, which is why decision 2 stores tokens at all. The first revision did not store tokens because the backend
made the calls in process; the BFF now calls the backend with the token, so it must hold it.

### 4. Single sign-out

- `POST /admin/auth/logout` (CSRF-protected, decision 5) deletes the caller's session and the cookie, and answers
  `{"logoutUrl": …}`: the tenant's `end_session` endpoint with `id_token_hint` (the stored ID token) and
  `post_logout_redirect_uri` = `COLOEUS_WEB_BASE_URL` + `/admin/`. The SPA navigates there.
- `GET /admin/auth/frontchannel-logout?iss=…&sid=…` is called by Entra in an iframe when the user signs out elsewhere. It
  checks that `iss` is the tenant issuer, deletes every session listed under `staff:sid:<sid>`, and answers 200 with
  `Cache-Control: no-store`. It needs no cookie, since a third-party iframe would not send it. The `sid` is the only key, so
  the endpoint cannot end sessions of other users.
- `GET /admin/auth/logout` answers 405.

The front-channel URL is registered on the app as `https://coloeus.apps.onelite.feather/admin/auth/frontchannel-logout`.

### 5. CSRF protection: synchronizer token in the session plus an Origin check

Every `POST`, `PUT` and `DELETE` under `/admin/api` and `POST /admin/auth/logout` is checked by Nitro before the body is read.
It must carry the header `X-CSRF-Token`, equal (constant-time comparison) to the session's CSRF token, and an `Origin` equal
to `COLOEUS_WEB_BASE_URL`. A missing or foreign value of either is refused with 403 `CSRF_INVALID`, and nothing is forwarded.
`GET /admin/api/me` returns the token.

*Why this over a double-submit cookie:* the session is already server-side, so the token lives there. *Why the Origin check
too:* it does not depend on the page's JavaScript and blocks cross-site form posts in browsers that do not send
`Sec-Fetch-Site`.

### 6. Authorization at the BFF

Nitro gates `/admin/api/**` by the session's roles. Reads need `Coloeus.Moderator` or `Coloeus.Admin`; writes need
`Coloeus.Admin`. A moderator's write is answered 403 `FORBIDDEN_ROLE` by Nitro before the body is read, and the backend
checks the same role on the token again (decision 9).

| Route group                                              | Moderator | Admin |
|----------------------------------------------------------|-----------|-------|
| `me`, account search and detail, postings                | read      | read  |
| money flow, money series, staff action log               | read      | read  |
| clients and currencies (list)                            | read      | read  |
| reset, adjust                                            | 403       | write |
| clients (register, grants), currencies (create, default) | 403       | write |

`/admin/auth/**` is anonymous except `logout`, which needs the session. Nitro answers a request under `/admin/api/**` without
a session cookie with 401 `SESSION_REQUIRED`.

### 7. Forwarding from Nitro to the backend

`/admin/api/<rest>` is forwarded to `COLOEUS_BACKEND_URL` + `/v1/staff/<rest>`, with the same method and query string. Nitro
sets `Authorization: Bearer <delegated access token>` from the session and forwards `Idempotency-Key`, `Content-Type` and
`Accept`. It drops the cookie and `X-CSRF-Token`, which the backend must not see. The backend's status and problem document
are returned unchanged. `me` is answered by Nitro from the session and is not forwarded. An unknown path under
`/admin/api/**` is answered 404 `NOT_FOUND`. A backend that cannot be reached or does not answer in time is answered 500
`INTERNAL`, with a `detail` that names no host.

### 8. Backend staff path: `/v1/staff/**`

The backend staff resources are served under `/v1/staff/**`. The reasons:

- It stays under the versioned API the services already use, so there is one bearer chain, one OpenAPI document (`/v1` and
  `/v1/staff` in one file) and one problem catalogue.
- The `staff` segment gives the staff-only rules (decision 9) one prefix to attach to. Every other `/v1` route refuses
  delegated tokens, so the prefix is the only door for a staff token.
- Nitro's mapping is mechanical: `/admin/api/x` to `/v1/staff/x`.

*Rejected: `/admin/api/**` on the backend.* The HTTPRoute sends `/admin` to Nitro, so the backend could not be reached on that
path from the browser, and the name would suggest the UI is served there again. *Rejected: `/staff/v1/**`.* It would open a
second version root beside `/v1`, with its own bearer rules and OpenAPI document.

### 9. Bearer authorization of the staff API

The backend's existing bearer chain validates signature, issuer, audience and expiry. On `/v1/staff/**` it also requires:

- the scope claim `scp` containing `Coloeus.Access`, and an `oid`. Client-credential tokens have no `scp`, so an app token
  is refused with 401 `UNAUTHORIZED` (no new code). An `idtyp` claim, where present, must be `user`;
- the app role `Coloeus.Moderator` for reads and `Coloeus.Admin` for writes, from the `roles` claim. A moderator's write is
  403 `FORBIDDEN_ROLE` before the body is read;
- the actor of a write is the token's `oid`, and the display name for `staff_name` and `staff_action.actor_name` is the
  token's `name`.

Every other `/v1` route keeps the rules of `service-auth` from `add-ledger-core`. Delegated staff tokens are not refused
there: administrators already call the ledger's admin endpoints with them (for example from the Azure CLI), and a refusal
would break that path. Ledger writes still need a registered client, which the web client never is (below). Refusing
delegated tokens on `/v1` was considered and rejected for that reason.

The web client itself must not be registered in the ledger's client registry. A `Coloeus.Admin` staff token whose `azp` is
a registered client could write through `/v1` (the ledger's rule that every write comes from a registered client) and bypass
`staff_action`. The rule above removes the path; the registry rule is a second, deliberate check.

### 10. Staff writes: idempotency scope and the booking

Principle 2 requires an `Idempotency-Key` on every mutating request. The existing `idempotency_key` table is keyed by
`client_id` with a foreign key to `client`, and a staff member has no client row. Migration `V2__admin_web.sql` therefore adds
`scope VARCHAR(128)` to `idempotency_key`, backfills it as `client:<uuid>`, keys the table by `(scope, key)`, and keeps the
client foreign key for client scopes only. Staff writes use the scope `staff:<oid>`, with `oid` from the token. Nitro passes
the browser's `Idempotency-Key` through unchanged; the backend answers 400 `IDEMPOTENCY_KEY_MISSING` when it is absent. The key
row is inserted in the same database transaction as the booking, exactly as for `/v1` (design decision 5 of `add-ledger-core`).

Money writes call the existing `BookingService` with a staff caller that has no client. Reset and adjust use the system
accounts `ADMIN_SOURCE` and `ADMIN_SINK` of the currency, so no grant is needed and no `SOURCE` or `SINK` account is touched.
The journal kinds `RESET` and `ADJUST` are added to the `transaction.kind` check in `V2`. `transaction.client_id` stays `NULL`
for staff transactions.

The first revision's `staff_session` and `staff_login` tables are dropped from `V2`: sessions are in Dragonfly (decision 2),
and the backend holds no session.

### 11. Attribution

- Money writes: `transaction.actor` = the staff member's `oid`; `transaction.reason` = the mandatory reason;
  `transaction.metadata` contains `staff_name` (the token's `name` at the time of the write).
- Every staff write, money or configuration: one row in `staff_action (id, actor_oid, actor_name, action, target,
  transaction_id, detail, created_at)` in the same database transaction. `transaction_id` is set for money writes. The table
  is insert-only (the same trigger pattern as the journal).

*Decision taken here, not in the requirement:* the requirement says "every staff write is a journal transaction". Client
registration, grant replacement and currency creation move no money and would have no postings, so they cannot be journal
transactions without inventing fake postings. They are therefore recorded in `staff_action` only. The money writes are both.
This needs the maintainer's confirmation.

### 12. Money flow from the postings

Created money of a client in a window is `-SUM(posting.amount)` over postings of its `SOURCE` account with
`posting.created_at` in the window; destroyed money is `SUM(posting.amount)` over its `SINK` account. Staff credits and debits
(`ADMIN_SOURCE`, `ADMIN_SINK`) are reported as their own bucket, not as client traffic. Circulation is the sum of `PLAYER`
balances now. Circulation does not depend on the window. The windows are `7D` (default) and `ALL`; the daily series is computed
in UTC with zero-filled days. Window aggregates use a new index on `posting (created_at)` in `V2`.

### 13. API shape and errors

The browser contract is served by Nitro under `/admin/api`; the bearer contract is served by the backend under `/v1/staff`.
Both have the same resources and JSON shapes, which `specs/staff-api` defines once. The only resource that exists only on
Nitro is `me`. Under both, list answers are `{"items": [...]}`, paginated lists add `nextCursor`, and errors are RFC 9457
Problem Details. Amounts in JSON are `{"amount": "12.50", "currency": "COINS"}` or, in aggregates, a plain decimal string next
to the currency code (principle 3).

Problem codes are owned by the side that can produce them:

| Code                                                                       | Answered by |
|----------------------------------------------------------------------------|-------------|
| `SESSION_REQUIRED` (401), `CSRF_INVALID` (403), `NOT_FOUND` (404)          | Nitro       |
| `NOTHING_TO_RESET` (409), `INTERNAL` (500)                                 | backend     |
| `FORBIDDEN_ROLE`, `IDEMPOTENCY_KEY_MISSING`, `REQUEST_INVALID`, and the ledger codes | both, the first answer wins: Nitro answers its own refusals and passes the backend's through |

The cursor of postings is the existing `PostingCursor` (base64url of `createdAt|postingId`), so `/v1` and `/v1/staff` paginate
the same way.

### 14. Frontend stack

The UI is a Nuxt 4 application with `ssr: false` for its pages and the Nitro server for the BFF routes, Vue 3, Nuxt UI 4 on
Tailwind 4, VueUse, `jose` for JWT validation, the unstorage redis driver for sessions, ESLint with
`eslint-plugin-vuejs-accessibility`, Vitest with `@vue/test-utils` on happy-dom, `vue-tsc` for types, pnpm with a lockfile,
and Node 24. The exact versions follow `Apus/ui/package.json` where Apus has them, and the lockfile records the rest. Renovate
keeps them current.

Evidence from the sibling projects (read-only, 2026-11-08):

- `Apus/ui/package.json`: Nuxt 4.5.2, `@nuxt/ui` 4.10.0, Tailwind 4.3.3, Vue 3.5.41, VueUse 14.4.0, ESLint 10 with
  `eslint-plugin-vuejs-accessibility`, Vitest 4.1.10, happy-dom, `vue-tsc`, pnpm 11.20.0. `nuxt.config.ts` sets `ssr: false`.
- `Corvus/ui/package.json`: Nuxt 4.6, Tailwind 4 through `@tailwindcss/vite`, `@nuxtjs/i18n`, `oidc-client-ts`, Vitest,
  `openapi-typescript`. Its UI is static and served by nginx, which does not fit here: the BFF needs a Node runtime.

What is taken and what is not:

- Taken: Nuxt 4 with `ssr: false`, Nuxt UI and Tailwind (Apus), Vitest and `vue-tsc`, the accessibility ESLint plugin (Apus),
  the Node 24 and pnpm pins, `openapi-typescript` (Corvus).
- Not taken: `oidc-client-ts` (the BFF keeps tokens out of the browser), `@nuxtjs/i18n` (the UI is German only), nginx (Nitro
  serves the files and the BFF routes).

*Rejected:* a Vue SPA with Vite and a separate Express or Hono server (two build pipelines and a hand-written static and SPA
layer where Nuxt and Nitro provide one). *Rejected:* React (no sibling uses it). *Rejected:* a component library other than
Nuxt UI (none is used in the network).

Charts are inline SVG components with a data table alternative, not a chart library: the views need three chart types (bars per
day, a stacked bar per currency, a sparkline), and a library adds a dependency for those alone.

### 15. Reusable building blocks

The UI is composed from a small set of generic components in `web/app/components/ui/`: `AppButton`, `AppDialog`, `AppTable`,
`AppFormField`, `AppMoney` (a decimal string and a currency, formatted), `AppStatus` (the live-region message), `AppEmptyState`
and `AppPageHeader`. Rules, enforced by review and by a lint rule:

- A building block takes props, slots and emits events. It knows no route, no API resource, no staff role, no German sentence of
  its own (texts come in as props or slots).
- A building block imports nothing from `pages/`, `features/`, `api/` or `server/`. Pages import the blocks, never the reverse.
- Page logic (what an account page shows, which fields a reset dialog asks for) lives in the page or in a feature module.

This keeps the blocks movable into a shared package later with no change to their code. The move itself is not part of this
change.

### 16. Generated contract types

The contract is written here as a spec. The backend generates OpenAPI from its annotations (as `/v1` does), and the document
includes `/v1/staff`. The frontend types are generated from that document with `openapi-typescript`, so a shape mismatch fails
`vue-tsc` in the frontend build instead of at run time. The generated file is committed under `web/app/types/` and regenerated by
a script; a CI check fails if it is stale. The one browser-only shape, `Me`, is defined in `web/shared/` and tested against the
spec.

### 17. Serving, headers and the SPA fallback in Nitro

Nitro serves the built pages under `/admin/` from `.output/public`, with `_nuxt/` assets cached `immutable` for a year and the
application shell `index.html` sent with `Cache-Control: no-store`. Routes of the UI (`/admin/konten/…`) fall back to the shell.
The fallback applies only to `GET` requests that are neither under `/admin/api/` nor under `/admin/auth/`, and match no file;
an unknown path under `/admin/api/` is answered 404 `NOT_FOUND` (decision 7).

Response headers on `/admin/**` (set by Nitro route rules): `Content-Security-Policy: default-src 'self'; script-src 'self';
style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none';
form-action 'self'` (Nuxt UI injects style attributes, so `unsafe-inline` is limited to styles), `X-Content-Type-Options: nosniff`,
`Referrer-Policy: same-origin`. The build emits no inline script.

`GET /admin/healthz` answers 200 without a session and without touching Dragonfly, for the readiness probe.

### 18. Container image and deployment

The UI is built in a Dockerfile of the `web/` directory: a Node 24 stage runs `pnpm install --frozen-lockfile` and the build, and
the runtime stage (Node 24) copies `.output` and runs `node .output/server/index.mjs` as a non-root user on port 3000. The image is
`harbor.onelitefeather.dev/onelitefeather/coloeus-admin`, built and pushed by the release workflow next to the backend image.

The backend build no longer packages any UI. No Gradle Node plugin is needed; this supersedes the first revision's decision to
package the UI into the backend jar.

The deployment is a second HelmRelease `coloeus-admin` in the FLUX repository (outside this change): two replicas, readiness on
`/admin/healthz`, and the environment settings of decision 19. The HTTPRoute sends `/admin` to its service and everything else to
the backend. The backend HelmRelease loses the `COLOEUS_WEB_*` values. Dragonfly gets an ACL user restricted to the `staff:*`
keys for Nitro.

### 19. Configuration

Nitro reads only environment variables. `COLOEUS_WEB_CLIENT_ID`, `COLOEUS_WEB_CLIENT_SECRET`, `COLOEUS_WEB_BASE_URL`,
`COLOEUS_WEB_SESSION_SECRET` (at least 32 random bytes), `COLOEUS_WEB_STORAGE_URL` (the Dragonfly URL), `COLOEUS_TENANT_ID`,
`COLOEUS_AUDIENCE` and `COLOEUS_BACKEND_URL` have no defaults in production; the development configuration fills in
`http://localhost:8080` for the backend and a local Dragonfly. A startup check names each missing variable and refuses to start.

The backend reads `COLOEUS_TENANT_ID` and `COLOEUS_AUDIENCE` (existing) and nothing for the web client.

### 20. Operations

Logging: login, refusal, logout, front-channel logout and every staff write are logged at INFO with the `oid` (never the token,
the cookie, the CSRF token or the session secret). OpenTelemetry traces cover the BFF routes and the forwarded calls in Nitro,
and the staff routes in the backend, linked by the trace context of the forwarded request.

## Decisions taken here

These choices were not in the agreed requirements. The maintainer confirms them in review; each has its rationale in the decision
named.

- Nitro is the BFF and the backend is a bearer API (decision 1).
- Sessions and login states are kept in Dragonfly through Nitro storage, not in Postgres; tokens are kept there encrypted, so that
  the refresh works (decisions 2 and 3).
- Staff routes live under `/v1/staff/**` on the backend (decision 8).
- App tokens are refused on staff routes with 401 `UNAUTHORIZED`, and delegated staff tokens are refused on the other `/v1` routes
  with 401 `UNAUTHORIZED`; no new problem code for either (decision 9).
- Configuration writes (client registration, grant replacement, currency creation and default) are logged in `staff_action` without a
  journal transaction, not as journal transactions (decision 11).
- Money writes are journal kinds `RESET` and `ADJUST`, not `TRANSFER` (decision 10).
- Staff idempotency is scoped to `staff:<oid>` through a migration that changes the key of `idempotency_key` (decision 10).
- The CSRF defense is a synchronizer token in the session plus an Origin check (decision 5).
- The `NOT_FOUND` code is added for unknown `/admin/api` paths (decision 13).
- The money-flow windows are `7D` and `ALL`, daily series in UTC, and circulation is the current sum of player balances (decision 12).
- The frontend stack is Nuxt 4 with Nuxt UI, Vitest and `vue-tsc`, taken from Apus; Nitro serves the files; the charts are inline SVG
  (decision 14).
- The UI is packaged as its own image, not into the backend jar; the Gradle Node plugin is not used (decision 18).

## Risks / Trade-offs

- **The Entra claims must be verified.** `sid`, `nonce`, `oid`, `name`, `scp` and `roles` are assumed from Entra's documented
  behavior. Task B1 checks them against a real sign-in before the backend and Nitro are built on them.
- **Delegated tokens reach the ledger unless the rule holds.** A staff token is a valid bearer token for the ledger audience. Without
  decision 9's refusal on the other `/v1` routes, a moderator could read the ledger with it, and an admin could write if the web client
  were registered. The refusal is tested in B4.3, and the registry check is task B4.4.
- **Roles are fixed for the session's life.** A role removed in Entra does not end a running session; the session keeps its roles
  until it expires (at most 8 hours, or 30 minutes idle). Accepting this avoids a directory call per request. Removing a user from the
  app's assignments takes effect at the next login.
- **Tokens at rest in Dragonfly.** They are encrypted with a key derived from `COLOEUS_WEB_SESSION_SECRET`, the store has an ACL limited to
  `staff:*`, and the TTL bounds their life. Rotating the secret ends all sessions; that is accepted and documented in the README.
- **Dragonfly outage.** Nitro fails closed: without the store no session can be read, so staff are sent to the login and no write
  is forwarded. The backend is not affected, because it never reads Dragonfly.
- **Hot accounts under reset.** A reset locks the player's balance rows like any transfer. Staff actions are rare, so this is not a
  concern at network scale.
- **Two write paths for money.** Staff writes call the same `BookingService` as `/v1`, so the invariants (non-negative player balances,
  balanced postings, idempotency) hold by construction; the integration tests of the staff API check that a reset cannot take a balance
  below zero.

## Migration Plan

Flyway `V2__admin_web.sql` is forward-only and runs at start-up. It adds `staff_action`, indexes, a column and two check values, and
backfills the scope of existing idempotency keys; existing rows are kept. Rollback is not a redeploy of the previous image: the previous
code inserts idempotency keys without `scope` and cannot write after `V2`. A failed release is rolled forward with a fix (`V3`), as the
journal is forward-only anyway. The `coloeus-admin` service has no data of its own to migrate; its sessions are created on first login.
