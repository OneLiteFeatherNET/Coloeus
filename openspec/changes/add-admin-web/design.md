# Design

## Context

Coloeus is one central, stateless service in the OneLiteFeather Kubernetes cluster with at least two replicas and one
Postgres database (CloudNativePG). `add-ledger-core` builds the journal, accounts, currencies, clients and grants behind
`/v1`, which services call with Entra ID bearer tokens. Staff need the same data and the same actions through a browser,
with a named person behind every write. Requirements: Outline "Anforderungen: Coloeus"; the principles in
`openspec/config.yaml` (Postgres as the only store of money, idempotent writes, JSR 354 amounts, stateless replicas,
Entra for staff, OpenTelemetry) apply unchanged.

The staff web interface is a second front door to the same services. It must not weaken the first one: the `/v1` bearer
rules stay as they are, and a browser session never reaches `/v1`.

## Goals / Non-Goals

**Goals:** staff log in with their Entra account and nothing else; the browser never holds a token; a stolen cookie is
useless after logout or after the idle timeout; every staff write is attributed and idempotent; the UI ships in the
existing image; the contract is complete enough for two teams to build in parallel.

**Non-Goals:** a player UI, real-time updates, currency editing, changes to ledger rules (see `proposal.md`).

## Decisions

### 1. Backend-for-frontend with server-side sessions

The backend is the OAuth client. `GET /admin/auth/login` creates a `staff_login` row (state, nonce, PKCE verifier, return
path, expiry 10 minutes), redirects to Entra with `response_type=code`, `code_challenge_method=S256`, the scope
`openid profile api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/Coloeus.Access` and the web client id. `GET /admin/auth/callback`
looks up and deletes the login row by `state` (single use), exchanges the code with `COLOEUS_WEB_CLIENT_SECRET`, validates
the ID token (signature via the tenant JWKS, `iss`, `aud` = `COLOEUS_WEB_CLIENT_ID`, `nonce`, `exp`) and the access token
(signature, `iss`, `aud` = `COLOEUS_AUDIENCE`), and reads the roles from the access token. A user with neither
`Coloeus.Moderator` nor `Coloeus.Admin` gets no session. Otherwise a `staff_session` row is inserted and the cookie set.

Tokens are not stored. After the callback the backend has what it needs: the session holds the `oid`, the display name,
the `sid`, the roles and the expiry. The staff API runs the same services in process; it never calls `/v1` with the
user's token, so no token needs to be kept.

*Rejected:* the public-client SPA login used by Apus and Corvus (`oidc-client-ts` in the browser, tokens in
`localStorage`). It is the pattern of those two projects, but the requirement asks for a confidential BFF, and a token
in browser storage is readable by any script that runs on the page. *Rejected:* a Nitro or Nuxt server as BFF (a second
runtime in the image, a second session store, and a second place for the Entra logic).

### 2. Sessions in Postgres, cookie holds a random id

`staff_session (id_hash, oid, display_name, sid, roles, csrf_token, created_at, last_seen_at, expires_at)`. The cookie
`__Host-coloeus_staff` carries 256 bits of randomness; the table stores its SHA-256 only, so a leaked database does not
leak live sessions. Cookie attributes: `HttpOnly; Secure; SameSite=Lax; Path=/` (`__Host-` also forbids a `Domain`).
Lifetime: absolute 8 hours from login; idle 30 minutes, extended on every `/admin/api` call. Both are checked against the
injected `java.time.Clock`. Expired rows are deleted by a daily job, as the idempotency keys are.

`SameSite=Lax` lets the cookie travel on the top-level redirect back from Entra, and blocks it on cross-site `POST`s.

*Rejected:* a signed, stateless JWT in the cookie (no server-side revocation, so logout and front-channel logout could not
end a session); Redis (a second system, and a second place that must agree with the booking transaction); in-memory
sessions (wrong with more than one replica, principle 4).

### 3. Single sign-out

- `POST /admin/auth/logout` (CSRF-protected, see decision 4) deletes the caller's session row, expires the cookie and
  answers with the Entra `end_session` URL including `post_logout_redirect_uri` = `COLOEUS_WEB_BASE_URL/admin/`. The SPA
  navigates there.
- `GET /admin/auth/frontchannel-logout?iss=…&sid=…` is called by Entra in an iframe when the user signs out elsewhere.
  It checks that `iss` is the tenant issuer and deletes every `staff_session` row with that `sid`. It needs no cookie
  (a third-party iframe would not send it) and answers 200 with `Cache-Control: no-store`. The `sid` of the session is
  the only key, so the endpoint cannot end sessions of other users.

The front-channel URL must be registered on the app as `https://coloeus.apps.onelite.feather/admin/auth/frontchannel-logout`.

### 4. CSRF protection: synchronizer token in the session plus an Origin check

Every state-changing request under `/admin/api` and `POST /admin/auth/logout` must carry the header `X-CSRF-Token`, equal
to the session's `csrf_token` (compared in constant time), and an `Origin` header equal to `COLOEUS_WEB_BASE_URL`. A
missing or different `Origin` is refused; a missing or different token is refused. Both checks answer 403 `CSRF_INVALID`.
The token is returned by `GET /admin/api/me`.

*Why this over a double-submit cookie:* the session is already server-side, so the token can live there. A double-submit
cookie would need a second cookie and would be weaker against a sibling subdomain that can set cookies. *Why the Origin
check too:* it costs nothing, does not depend on the page's JavaScript, and blocks cross-site form posts even in browsers
that do not send `Sec-Fetch-Site`. Reads (`GET`) change nothing and need no token; the `GET` front-channel endpoint
changes only sessions of a matching `sid`.

### 5. Authorization and the staff API

`/admin/api/**` is authenticated by a server filter that resolves the session cookie into the request's `Authentication`
(roles from the session). The role matrix:

| Route group                                   | Moderator | Admin |
|-----------------------------------------------|-----------|-------|
| `me`, account search and detail, postings     | read      | read  |
| money flow, staff action log                  | read      | read  |
| clients and currencies (list)                 | read      | read  |
| reset, adjust                                 | 403       | write |
| clients (register, grants), currencies (create, default) | 403 | write |

Admins also have every moderator right. A moderator's write attempt answers 403 `FORBIDDEN_ROLE` before any body is read.

`intercept-url-map` keeps `/admin` (the static UI) and `/admin/auth/**` anonymous. `/v1/**` keeps the bearer rules. A
session cookie on a `/v1` call is ignored, and a bearer token on `/admin/api` is refused with 401 `SESSION_REQUIRED`.

### 6. Staff writes: idempotency scope and the booking

Principle 2 requires an `Idempotency-Key` on every mutating request. The existing `idempotency_key` table is keyed by
`client_id` with a foreign key to `client`, and a staff member has no client row. Migration `V2__admin_web.sql` therefore
adds `scope VARCHAR(128)` to `idempotency_key`, backfills it as `client:<uuid>`, keys the table by `(scope, key)`, and
keeps the client foreign key for client scopes only. Staff writes use the scope `staff:<oid>`. The key row is inserted in
the same database transaction as the booking, exactly as for `/v1` (design decision 5 of `add-ledger-core`).

Money writes call the existing `BookingService` with a staff caller that has no client. Reset and adjust use the system
accounts `ADMIN_SOURCE` and `ADMIN_SINK` of the currency, so no grant is needed and no `SOURCE` or `SINK` account is
touched. The journal kinds `RESET` and `ADJUST` are added to the `transaction.kind` check in `V2`, so the log can tell
staff actions from client traffic. `transaction.client_id` stays `NULL` for staff transactions.

### 7. Attribution

- Money writes: `transaction.actor` = the staff member's `oid`; `transaction.reason` = the mandatory reason;
  `transaction.metadata` contains `staff_name` (display name at the time of the write).
- Every staff write, money or configuration: one row in `staff_action (id, actor_oid, actor_name, action, target,
  transaction_id, detail, created_at)`. `transaction_id` is set for money writes. The table is insert-only (the same
  trigger pattern as the journal).

*Decision taken here, not in the requirement:* the requirement says "every staff write is a journal transaction". Client
registration, grant replacement and currency creation move no money and would have no postings, so they cannot be
journal transactions without inventing fake postings. They are therefore recorded in `staff_action` only. The money
writes are both. This needs the maintainer's confirmation.

### 8. Money flow from the postings

Created money of a client in a window is `-SUM(posting.amount)` over postings of its `SOURCE` account with
`posting.created_at` in the window; destroyed money is `SUM(posting.amount)` over its `SINK` account. Staff credits and
debits (`ADMIN_SOURCE`, `ADMIN_SINK`) are reported as their own bucket, not as client traffic. Circulation is the sum of
`PLAYER` balances now, which is the same as all-time created minus destroyed over all clients and the staff bucket
(the postings of every transaction sum to zero, and `FEE` and `BANK` are not counted). Circulation does not depend on the
window. The windows are `7D` (default) and `ALL`; the daily series is computed in UTC with zero-filled days. Postings are
read with the existing `posting_history` index for account-scoped queries; the window aggregates use
`posting (created_at)` through a new index in `V2`.

### 9. API shape and errors

Under `/admin/api`, all list answers are `{"items": [...]}`; paginated lists add `nextCursor`. Errors are RFC 9457 Problem
Details as elsewhere. Amounts in JSON are `{"amount": "12.50", "currency": "COINS"}` or, in aggregates, a plain decimal
string next to the currency code (principle 3). The full contract is in `specs/staff-api`.

The cursor of postings is the existing `PostingCursor` (base64url of `createdAt|postingId`), so `/v1` and `/admin/api`
paginate the same way.

### 10. Frontend stack

A single-page application built with Nuxt 4 in static mode (`ssr: false`, `nuxt generate`), Vue 3, Nuxt UI 4 on Tailwind 4,
VueUse, ESLint with `eslint-plugin-vuejs-accessibility`, Vitest with `@vue/test-utils` on happy-dom, `vue-tsc` for types,
pnpm with a lockfile, Node 24. The exact versions follow `Apus/ui/package.json` on the day of the code pull request, and
Renovate keeps them current.

Evidence from the sibling projects (read-only, 2026-11-08):

- `Apus/ui/package.json`: Nuxt 4.5.2, `@nuxt/ui` 4.10.0, Tailwind 4.3.3, Vue 3.5.41, VueUse 14.4.0, ESLint 10 with
  `eslint-plugin-vuejs-accessibility`, Vitest 4.1.10, happy-dom, `vue-tsc`, pnpm 11.20.0. `nuxt.config.ts` sets
  `ssr: false`. Its README states the SPA is built as static assets and served by a plain webserver.
- `Corvus/ui/package.json`: Nuxt 4.6, Tailwind 4 through `@tailwindcss/vite`, `@nuxtjs/i18n`, `oidc-client-ts`, Vitest,
  `openapi-typescript` for the API types. `nuxt.config.ts` sets `ssr: false` and `nitro: { preset: 'static' }`, and
  `ui/Dockerfile` runs `nuxt generate` in a Node 24 stage and serves the output from nginx.
- Both projects use the same shape (Nuxt static SPA, Tailwind, Vitest, `vue-tsc`, pnpm), so the stack is the one the
  network already runs.

What is taken from them and what is not:

- Taken: Nuxt 4 static output, Nuxt UI and Tailwind (Apus, the newer design), Vitest and `vue-tsc`, the accessibility
  ESLint plugin (Apus), the Node 24 and pnpm pins.
- Not taken: `oidc-client-ts` (the BFF keeps tokens out of the browser), `@nuxtjs/i18n` (the UI is German only; Corvus is
  bilingual), nginx (the backend serves the files). `openapi-typescript` is taken from Corvus, see the next decision.

*Rejected:* a Vue SPA with Vite alone (no file-based routing or auto imports, and more own setup for the same result);
React (no sibling uses it, so the team would carry a second frontend stack); Nuxt with SSR or Nitro (a second runtime in
the image, which the static-output design avoids); a component library other than Nuxt UI (none is used in the network).

Charts are inline SVG components with a data table alternative, not a chart library: the views need three chart types
(bars per day, a stacked bar per currency, a sparkline), and a library adds a dependency for those alone.

### 11. Generated client types

The contract is written here as a spec. The backend generates OpenAPI from its annotations (as `/v1` does). The frontend
types are generated from that document with `openapi-typescript` as Corvus does, so a shape mismatch fails `vue-tsc` in
the frontend build instead of at run time. The generated file is committed under `web/app/types/` and regenerated by a
script; a CI check fails if it is stale.

### 12. Packaging in Gradle

`web/build.gradle.kts` uses the `com.github.node-gradle.node` plugin to install the pinned Node 24 and pnpm, runs
`pnpm install --frozen-lockfile` and `pnpm generate`, and exposes `web/.output/public` as an outgoing configuration. The
backend's `processResources` depends on it and copies the files to `admin/` on the classpath. The backend image is built
from the jar by the Micronaut Docker task, so Node is needed by the build, never by the image.

*Rejected:* calling a host `pnpm` from `Exec` tasks (the build depends on what is installed on the machine that runs it,
and CI and laptops drift). *Rejected:* a separate frontend image with nginx (the requirement says the backend serves the
UI, and a second image needs a second deployment). The `node-gradle` plugin is the one Gradle plugin this change adds to
the build. Apus keeps its UI outside Gradle on purpose; Coloeus packages the UI into the service, so it needs Gradle to be
in charge of the build.

### 13. Serving and the SPA fallback

Static files under `classpath:admin` are served at `/admin/**`, with hashed `_nuxt/` assets cached immutable and
`index.html` sent with `Cache-Control: no-store`. Routes of the UI (`/admin/konten/…`) fall back to `index.html`. The
fallback applies only to `GET` requests that are neither `/admin/api/**` nor `/admin/auth/**` and that match no file.

Response headers on `/admin/**`: `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'
'unsafe-inline'` (Nuxt UI injects style attributes; the `unsafe-inline` is limited to styles), `img-src 'self' data:`,
`connect-src 'self'`, `frame-ancestors 'none'`, `base-uri 'none'`, `form-action 'self'`; `X-Content-Type-Options: nosniff`;
`Referrer-Policy: same-origin`. Inline scripts are avoided rather than hashed (Corvus hashes them in a postprocess step;
the simpler setup is preferred when the build emits none).

### 14. Operations

Configuration only from environment variables: `COLOEUS_WEB_CLIENT_ID`, `COLOEUS_WEB_CLIENT_SECRET` and
`COLOEUS_WEB_BASE_URL` have no defaults in production; `dev` fills in `http://localhost:8080`. `RequiredSettingsGuard`
names the missing variable. Logging: login, refusal, logout and every staff write are logged at INFO with the `oid` (never
the token, the cookie or the CSRF token). OpenTelemetry traces cover the BFF routes and the staff API.

## Decisions taken here

These choices were not in the agreed requirements. The maintainer confirms them in review; each has its rationale in the
decision named.

- Configuration writes (client registration, grant replacement, currency creation and default) are logged in
  `staff_action` without a journal transaction, not as journal transactions (decision 7).
- Money writes are journal kinds `RESET` and `ADJUST`, not `TRANSFER` (decision 6).
- Staff idempotency is scoped to `staff:<oid>` through a migration that changes the key of `idempotency_key` (decision 6).
- The CSRF defense is a synchronizer token in the session plus an Origin check (decision 4).
- Sessions are server-side rows keyed by a hash of a random cookie value; tokens are not stored (decisions 1 and 2).
- Name search uses the register only and never calls Mojang; a UUID without an account is reported with `accountId` `null`
  instead of creating an account (`staff-api`).
- The money-flow windows are `7D` and `ALL`, daily series in UTC, and circulation is the current sum of player balances
  (decision 8).
- The frontend stack is Nuxt 4 static with Nuxt UI, Vitest and `vue-tsc`, taken from Apus and Corvus; the charts are inline
  SVG (decision 10).
- Frontend types are generated from the backend OpenAPI document and committed (decision 11).
- The `node-gradle` plugin packages the UI into the backend jar (decision 12).
- The `/admin` UI is public static content; only `/admin/api` needs a session (decision 5).

## Risks / Trade-offs

- **The Entra claims must be verified.** `sid`, `oid` and the `roles` claim of the scope are assumed from Entra's
  documented behavior. The first task of the backend section checks them against a real sign-in; a mismatch changes the
  session model, so it is checked before the rest of the backend is built.
- **Roles are fixed for the session's life.** A role removed in Entra does not end a running session; the session keeps
  its roles until it expires (at most 8 hours, or 30 minutes idle). Accepting this avoids a directory call per request.
  Removing a user from the app's assignments takes effect at the next login.
- **Hot accounts under reset.** A reset locks the player's balance rows like any transfer. Staff actions are rare, so this
  is not a concern at network scale.
- **Two write paths for money.** Staff writes call the same `BookingService` as `/v1`, so the invariants (non-negative
  player balances, balanced postings, idempotency) hold by construction; the integration tests of the staff API check that
  a reset cannot take a balance below zero.

## Migration Plan

Flyway `V2__admin_web.sql` is forward-only and runs at start-up. It adds tables, indexes, a column and two check values, and
backfills the scope of existing idempotency keys; existing rows are kept. Rollback is not a redeploy of the previous image:
the previous code inserts idempotency keys without `scope` and cannot write after `V2`. A failed release is rolled forward
with a fix (`V3`), as the journal is forward-only anyway.
