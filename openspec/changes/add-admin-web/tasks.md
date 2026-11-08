# Tasks

The backend (B) and the Nitro and UI (F) sections are independent once B1 is done and the contract in `specs/staff-api` is fixed. The
UI builds against a fake of the browser contract until the backend and Nitro serve it. Deployment (D) follows both; integration (I)
runs last. Checkboxes are ticked in the follow-up pull request, never in the code pull request.

## Backend (bearer API under /v1/staff)

### B1. Verify the Entra claims first

- [ ] B1.1 Sign in once with a staff user of the dev tenant through the web app registration and capture the ID token and the access
      token of scope `api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/Coloeus.Access` (decode only, do not commit them). Capture also a
      client-credential token of a service for the negative tests.
- [ ] B1.2 Confirm: the ID token has `sid`, `oid` and `nonce`; the access token has `roles`, `scp` containing `Coloeus.Access`, `aud` equal to
      the API client id, `oid` and `name`; the client-credential token has no `scp`. Confirm that the API registration exposes
      `Coloeus.Access` and that the refresh token is issued with `offline_access`. If a claim differs, stop and update `design.md`
      decisions 2, 3 and 9 before B2.

### B2. Settings

- [ ] B2.1 The backend reads `COLOEUS_TENANT_ID` and `COLOEUS_AUDIENCE` only (no default in production; `dev` defaults in
      `application-dev.yml`). It reads no `COLOEUS_WEB_*` variable.
- [ ] B2.2 Test: the backend starts with its settings and without any `COLOEUS_WEB_*` variable.

### B3. Schema

- [ ] B3.1 Flyway `V2__admin_web.sql`: `staff_action` with the insert-only trigger as for the journal. No session or login table.
- [ ] B3.2 `idempotency_key`: add `scope`, backfill `client:<client_id>`, primary key `(scope, key)`, client foreign key only for client
      scopes; keep stored answers.
- [ ] B3.3 Extend the `transaction.kind` check by `RESET` and `ADJUST`; index `posting (created_at)` for the window aggregates.
- [ ] B3.4 Migration test on a Testcontainers Postgres that seeds V1 data, runs V2 and checks the backfill and the trigger.

### B4. Bearer authorization of the staff routes

- [ ] B4.1 `/v1/staff/**` accepts a token only if `scp` contains `Coloeus.Access`, `oid` is present and `idtyp`, where present, is `user`.
      Otherwise 401 `UNAUTHORIZED`. The actor is `oid` and the display name is `name` of that token.
- [ ] B4.2 Every other `/v1/**` route keeps the rules of `service-auth`; no new refusal of delegated tokens (the admin endpoints are used
      with them today).
- [ ] B4.3 Unit tests with fake token claims: client-credential token refused on `/v1/staff`; staff token of the web client refused on a ledger write (`CLIENT_NOT_REGISTERED`); moderator
      write refused with 403 `FORBIDDEN_ROLE` before the body is read; admin write allowed; moderator read allowed.
- [ ] B4.4 The web client id is not a registered ledger client. Test: a delegated token whose `azp` is not registered cannot write on `/v1`
      (existing `CLIENT_NOT_REGISTERED`). Record in the README that the web client must never be registered.

### B5. Staff reads

- [ ] B5.1 `GET /v1/staff/accounts/search` (register only, no Mojang call; exactly one parameter; at most 20 hits).
- [ ] B5.2 `GET /v1/staff/accounts/{id}` and `…/postings` with `StaffPosting` (currency, `actorName`), cursor as `/v1`.
- [ ] B5.3 `GET /v1/staff/money-flow` (`7D`, `ALL`) and `GET /v1/staff/money-flow/series` (zero-filled UTC days), per `staff-money-flow`.
- [ ] B5.4 `GET /v1/staff/clients`, `GET /v1/staff/currencies`, `GET /v1/staff/staff-actions` with the envelope and cursor.
- [ ] B5.5 Tests: name with history, unknown name without Mojang call, circulation identity, day boundary in UTC, zero-filled series, cursor
      errors (integration tests with Testcontainers; unit tests with fakes).

### B6. Staff writes

- [ ] B6.1 Staff caller for the booking (no client): reuse `BookingService` with `ADMIN_SOURCE` and `ADMIN_SINK` only; no grant check.
- [ ] B6.2 `POST /v1/staff/accounts/{id}/reset`: whole balance of one or all currencies, `NOTHING_TO_RESET`, kind `RESET`, actor oid,
      `staff_name` metadata, `staff_action` row in the same transaction.
- [ ] B6.3 `POST /v1/staff/accounts/{id}/adjust`: CREDIT and DEBIT, `INSUFFICIENT_FUNDS`, `SCALE_EXCEEDED`, `AMOUNT_INVALID`, kind `ADJUST`.
- [ ] B6.4 Idempotency in scope `staff:<oid>` (oid of the token) for every staff `POST` and `PUT`: key row in the booking transaction, replay
      of the stored 201, `IDEMPOTENCY_CONFLICT` for another body, `IDEMPOTENCY_KEY_MISSING` without the header.
- [ ] B6.5 `POST /v1/staff/clients`, `PUT /v1/staff/clients/{clientId}/grants` (with `staff_action` rows, no transaction).
- [ ] B6.6 `POST /v1/staff/currencies`, `PUT /v1/staff/currencies/{code}/default` (with `staff_action` rows).
- [ ] B6.7 Tests: reset and adjust per `staff-operations`; concurrent reset against a concurrent transfer (two application contexts, one
      database, balance never negative); no `SOURCE` or `SINK` booking; replay and conflict.

### B7. Problems, documentation and OpenAPI

- [ ] B7.1 Add `NOTHING_TO_RESET` and `INTERNAL` to `ProblemCode` and to `docs/problems.md`; test that every backend code is documented.
- [ ] B7.2 Map unexpected exceptions of `/v1/staff` to 500 `INTERNAL` without cause, table or host in `detail`.
- [ ] B7.3 The OpenAPI document (`META-INF/swagger/coloeus.yml`) includes the `/v1/staff` paths, without `me` (the browser path is in
      `web/`).
- [ ] B7.4 README: the backend settings are unchanged (no `COLOEUS_WEB_*`); a section on the staff API and the rule that the web client is
      never a registered client.

### B8. Observability

- [ ] B8.1 Log every staff write at INFO with action, `oid`, target and transaction id, and no token, cookie, secret or reason text; test
      with a captured appender.
- [ ] B8.2 OpenTelemetry spans for `/v1/staff` routes, linked to the trace context the Nitro server forwards.

## Nitro and UI (web module)

### F1. Project setup and container

- [ ] F1.1 `web/package.json`: Nuxt 4 (`ssr: false`, `app.baseURL` `/admin/`, Nitro `node-server` preset), Vue 3.5, Nuxt UI 4, Tailwind 4,
      VueUse, `jose`, `unstorage` with the redis driver, `@nuxt/eslint`, `eslint-plugin-vuejs-accessibility`, Vitest, `@vue/test-utils`,
      happy-dom, `vue-tsc`, `openapi-typescript`; versions as in `Apus/ui/package.json` where it has them; `packageManager` pnpm 11.20.0;
      `engines` Node 24.
- [ ] F1.2 `pnpm-lock.yaml` committed; `pnpm install --frozen-lockfile` works in a clean checkout.
- [ ] F1.3 Scripts: `dev`, `build`, `lint`, `typecheck`, `test`, `api:types`.
- [ ] F1.4 `web/Dockerfile`: Node 24 build stage; Node 24 runtime stage with `.output` only, non-root user, port 3000, `node .output/server/index.mjs`.

### F2. Settings and storage

- [ ] F2.1 Settings from environment: `COLOEUS_WEB_CLIENT_ID`, `COLOEUS_WEB_CLIENT_SECRET`, `COLOEUS_WEB_BASE_URL`, `COLOEUS_WEB_SESSION_SECRET`,
      `COLOEUS_WEB_STORAGE_URL`, `COLOEUS_TENANT_ID`, `COLOEUS_AUDIENCE`, `COLOEUS_BACKEND_URL`; no defaults in production; development
      defaults for the backend URL and a local Dragonfly only.
- [ ] F2.2 Startup check names each missing or too-short variable (the session secret needs at least 32 bytes) and refuses to start; test
      with an injected environment.
- [ ] F2.3 Storage through the unstorage redis driver with the key prefix `staff:`; one storage instance per process.

### F3. Session store

- [ ] F3.1 Session record and key: the cookie value is 256 random bits; the key is `staff:session:<HMAC-SHA256(secret, value)>`; the record
      holds `oid`, name, roles, `sid`, CSRF token, `createdAt`, `lastSeenAt`, absolute and idle expiry.
- [ ] F3.2 Tokens (access, refresh, ID) stored with AES-256-GCM under a key derived from the session secret; round trip and tamper tests.
- [ ] F3.3 Absolute 8 h and idle 30 min checked with the injected clock; the Redis TTL is the shorter of the two; every `/admin/api` call
      moves the idle expiry.
- [ ] F3.4 The `staff:sid:<sid>` set holds the session keys of a `sid`, written with each session and expired with the latest of them.
- [ ] F3.5 Unit tests with an in-memory storage fake: expiry at the boundaries, `sid` index, no cookie value in any key.

### F4. Login and callback

- [ ] F4.1 `GET /admin/auth/login`: state, nonce, PKCE verifier and return path in `staff:login:<state>` with a 10-minute TTL; return path
      validation as in `staff-login`; the scope includes `offline_access`.
- [ ] F4.2 `GET /admin/auth/callback`: read and delete the login state in one command (`GETDEL`); code exchange with the client secret;
      ID token validation (JWKS, `iss`, `aud`, `nonce`, `exp`, `sid`, `oid`); access token validation (signature, `iss`, `aud`,
      `scp` containing `Coloeus.Access`, `roles`).
- [ ] F4.3 Role gate: no staff role redirects to `/admin/?error=forbidden` without a session; a refused login to `error=login`.
- [ ] F4.4 Session creation and `Set-Cookie` with `__Host-coloeus_staff`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`.
- [ ] F4.5 Tests against a fake token endpoint and locally signed test tokens: replayed state, foreign audience, client-credential token,
      missing role, expired login state, cookie attributes, no token in logs.

### F5. Refresh and forwarding

- [ ] F5.1 Refresh the access token when fewer than two minutes remain, store the new tokens encrypted; a failed refresh deletes the session
      and answers 401 `SESSION_REQUIRED`.
- [ ] F5.2 Forward `/admin/api/<rest>` to `COLOEUS_BACKEND_URL` + `/v1/staff/<rest>` with the same method, path and query; set
      `Authorization: Bearer`; forward `Idempotency-Key`, `Content-Type` and `Accept`; drop the cookie and `X-CSRF-Token`; return the
      backend's status and problem document unchanged.
- [ ] F5.3 A backend that cannot be reached or does not answer in time gives 500 `INTERNAL` with a `detail` that names no host.
- [ ] F5.4 Tests with a fake backend: headers forwarded and dropped, problem passed through, refresh before the call, timeout mapping.

### F6. Logout and front-channel logout

- [ ] F6.1 `POST /admin/auth/logout` (CSRF, F7.1): delete the session and the cookie; answer `{"logoutUrl"}` with `id_token_hint` and
      `post_logout_redirect_uri`; `GET` answers 405.
- [ ] F6.2 `GET /admin/auth/frontchannel-logout?iss&sid`: 400 `REQUEST_INVALID` for a foreign issuer; otherwise delete every session of the
      `sid` set; `Cache-Control: no-store`; no cookie needed.
- [ ] F6.3 Tests: two sessions with one `sid` removed and one kept; foreign issuer refused; logout without CSRF refused.

### F7. CSRF, roles and the browser API

- [ ] F7.1 Writes under `/admin/api` and `POST /admin/auth/logout`: `X-CSRF-Token` compared in constant time and `Origin` equal to
      `COLOEUS_WEB_BASE_URL`, checked before the body is read; otherwise 403 `CSRF_INVALID`.
- [ ] F7.2 Role gate per route from the session roles (reads: moderator or admin; writes: admin); a moderator's write is 403
      `FORBIDDEN_ROLE` before forwarding. A request without a valid session is 401 `SESSION_REQUIRED`.
- [ ] F7.3 `GET /admin/api/me` answered from the session with the `Me` shape, including `csrfToken`, `sessionExpiresAt`, `idleExpiresAt`.
- [ ] F7.4 Unknown paths under `/admin/api` answer 404 `NOT_FOUND` without forwarding.
- [ ] F7.5 Tests: missing token, cross-site origin, moderator write with no backend call, no cookie, unknown path.

### F8. Headers, serving and health

- [ ] F8.1 Route rules for `/admin/**`: the CSP, `nosniff` and `Referrer-Policy` of `staff-web-ui`; `frame-ancestors 'none'`.
- [ ] F8.2 `_nuxt/` assets `immutable` for a year; the shell `index.html` `no-store`; SPA fallback for `GET` that is neither under
      `/admin/api/` nor `/admin/auth/`.
- [ ] F8.3 `GET /admin/healthz` answers 200 without session and without Dragonfly or backend calls.
- [ ] F8.4 Tests: deep link 200 with `no-store`, unknown asset 404, headers present, unknown API path 404 `NOT_FOUND` and no fallback.

### F9. Problem catalogue and error mapping

- [ ] F9.1 `docs/problems.md`: `SESSION_REQUIRED` (401), `CSRF_INVALID` (403), `NOT_FOUND` (404) with title and meaning; a Nitro test reads
      the file and finds them.
- [ ] F9.2 Problem `code` to German message mapping in the UI (`NOTHING_TO_RESET`, `INSUFFICIENT_FUNDS`, `SCALE_EXCEEDED` and the rest of
      `staff-api`); `SESSION_REQUIRED` redirects to `/admin/anmelden` with the return path.

### F10. API client in the UI

- [ ] F10.1 Typed client over `/admin/api` (same origin) with the shapes of `api.d.ts`; sends `X-CSRF-Token` from `me` on writes; reads
      `me` once per page load.
- [ ] F10.2 `Idempotency-Key` helper: a dialog generates the key on open and keeps it for retries of the same submit.
- [ ] F10.3 Tests with a fake fetch: header presence, key reuse on retry, problem mapping, redirect on expired session.

### F11. Reusable building blocks

- [ ] F11.1 Components in `web/app/components/ui/`: `AppButton`, `AppDialog` (focus trap, Escape, focus return), `AppTable` (caption, `scope`,
      stacked below `md`), `AppFormField` (label, hint, error linked by `aria-describedby`), `AppMoney` (decimal string and currency),
      `AppStatus` (`aria-live="polite"`), `AppEmptyState`, `AppPageHeader`.
- [ ] F11.2 Lint rule: nothing under `ui/` imports from `pages/`, `features/`, `api/` or `server/`.
- [ ] F11.3 Unit tests per block with props and slots only; no German literal in `ui/`.

### F12. Shell and design system

- [ ] F12.1 Layout: skip link, navigation with the pages of `staff-web-ui` filtered by role, header with the user name and logout (CSRF token
      from `me`), responsive down to 320 px, no horizontal scroll.
- [ ] F12.2 Design tokens on `:root` with dark and light variants; text contrast 4.5:1; focus ring; `prefers-reduced-motion`.
- [ ] F12.3 German message module `web/app/i18n/de.ts` for all texts; no literal German string in components; document title per page
      ("<Titel> · Coloeus"), `lang="de"`, 404 page "Seite nicht gefunden".

### F13. Formatting

- [ ] F13.1 Money formatter on decimal strings (no `Number`): comma separator, trailing zeros kept, sign, currency fraction digits.
- [ ] F13.2 Date and time formatter in `de-DE` in the user's time zone; instants stay UTC in the model.
- [ ] F13.3 Tests: `"-0.10"`, eight fraction digits, `"0"`, UTC to local conversion with an injected time zone.

### F14. Übersicht (money flow)

- [ ] F14.1 Window toggle "7 Tage" and "Gesamt"; cards per currency with created, destroyed, net and circulation.
- [ ] F14.2 Table per client and currency; staff bucket shown separately.
- [ ] F14.3 Daily chart as inline SVG with `role="img"` and label; "Als Tabelle anzeigen" alternative; 7 and 30 days.

### F15. Konten (search, detail, dialogs)

- [ ] F15.1 Search form with name and UUID, validation as in the contract, results list, "Noch kein Konto".
- [ ] F15.2 Detail page: owner, balances, history list with "Mehr laden".
- [ ] F15.3 Reset dialog: currency choice including "alle Währungen", reason, amounts preview, button disabled until valid.
- [ ] F15.4 Adjust dialog: direction, currency, amount with client-side format check, reason; error messages per `staff-web-ui`.
- [ ] F15.5 Success shows a status message in the live region, closes the dialog and returns focus to its trigger.

### F16. Server und Rechte (clients and grants)

- [ ] F16.1 List of clients with grants as a table (stacked below `md`).
- [ ] F16.2 "Server registrieren" dialog (UUID and display name) and "Rechte ersetzen" editor (rows of currency and permission, duplicates
      refused in the form).

### F17. Währungen

- [ ] F17.1 List with code, names, symbol, fraction digits and default marker (text, not color only).
- [ ] F17.2 "Währung anlegen" form with the ledger's rules; "Als Standard setzen" with confirmation.

### F18. Protokoll

- [ ] F18.1 Paginated list of staff actions with the German action names and links to the account or client.

### F19. Anmelden and moderator view

- [ ] F19.1 Login page with the start button; `?error=login` and `?error=forbidden` messages in German.
- [ ] F19.2 Moderator view: every write control disabled with "Nur Administratoren können ändern".

### F20. Contract types

- [ ] F20.1 `web/app/types/api.d.ts` generated from the backend OpenAPI document with `openapi-typescript`; `api:types` regenerates it; a
      check fails when it is stale.
- [ ] F20.2 `Me` defined in `web/shared/` and tested against the `staff-api` shape.

### F21. Quality

- [ ] F21.1 `pnpm lint` passes with `eslint-plugin-vuejs-accessibility` and no disabled rule without a comment.
- [ ] F21.2 `pnpm typecheck` passes; `pnpm test` runs without network, without timers and without Dragonfly.
- [ ] F21.3 Manual check: keyboard only through all pages; screen reader names of dialogs and charts; 360 px width without horizontal
      scroll; contrast of both themes.

## Deployment

- [ ] D1 Release workflow builds and pushes `harbor.onelitefeather.dev/onelitefeather/coloeus-admin` next to the backend image, with the
      same tags and provenance.
- [ ] D2 Dragonfly: an ACL user limited to the `staff:*` keys, its secret in the cluster.
- [ ] D3 Entra app registration "Coloeus Admin Web": redirect URI `https://coloeus.apps.onelite.feather/admin/auth/callback`, front-channel
      logout URL `https://coloeus.apps.onelite.feather/admin/auth/frontchannel-logout`, a client secret, the delegated permission
      `Coloeus.Access` of the API registration, and user assignment required. Check that the API registration exposes `Coloeus.Access`
      and that the web client is not registered in the ledger client registry.
- [ ] D4 FLUX: second HelmRelease `coloeus-admin` (two replicas, readiness on `/admin/healthz`, secret-based settings listed in F2.1).
- [ ] D5 FLUX: HTTPRoute sends `/admin` to the `coloeus-admin` service and everything else to the backend; the backend HelmRelease loses
      the `COLOEUS_WEB_*` values.
- [ ] D6 Rollout order: `coloeus-admin` deployed but not routed; backend with `V2` deployed; then the HTTPRoute switched to Nitro.

## Integration

- [ ] I1 Run `./gradlew build` under `flock ~/.cache/claude-heavy.lock`; check that the backend jar contains no file under `admin/`.
- [ ] I2 Run `pnpm lint typecheck test build` and the image build under `flock ~/.cache/claude-heavy.lock`; the image answers
      `GET /admin/healthz`.
- [ ] I3 Local end to end with Nitro, the backend, the dev database and Dragonfly: a moderator sees the read pages and no enabled write
      control; an admin resets, adjusts, registers a client, replaces grants, creates a currency and sets the default; each write appears in
      the log once; logout ends the session; a front-channel call ends the session of its `sid`.
- [ ] I4 Two Nitro replicas on different ports sharing one Dragonfly: a login started on A is completed and used on B; a front-channel call
      on A ends a session used on B.
- [ ] I5 A duplicate submit through Nitro with the same `Idempotency-Key` returns the same booking and no second journal row exists.
- [ ] I6 Open the code pull request `feat(admin): add staff web ui` from the implementation only: no file under `openspec/`, branched from
      `main` after this proposal and `add-ledger-core` are merged. Deployment manifests are in the FLUX repository and are not part of it.
      The description follows the repository's pull request template and names the decisions of `design.md`, section "Decisions taken here".
