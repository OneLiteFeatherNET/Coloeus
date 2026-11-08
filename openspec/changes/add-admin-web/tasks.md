# Tasks

The backend and the frontend sections are independent and can run in parallel. They meet only through the contract in
`specs/staff-api`: the frontend builds against a fake of that contract until the backend serves it. The integration section
runs after both. Checkboxes are ticked in the follow-up pull request, never in the code pull request.

## Backend (BFF, sessions, staff API)

### B1. Verify the Entra claims first

- [ ] B1.1 Sign in once with a staff user of the dev tenant through the web app registration and capture the ID token and the
      access token of scope `api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/Coloeus.Access` (decode only, do not commit them).
- [ ] B1.2 Confirm: the ID token has `sid`, `oid` and `nonce`; the access token has `roles`, `aud` equal to the API client id
      and `oid`. Confirm that the API registration exposes `Coloeus.Access`. If a claim differs, stop and update
      `design.md` decisions 1 and 2 before B2.

### B2. Settings and startup

- [ ] B2.1 `COLOEUS_WEB_CLIENT_ID`, `COLOEUS_WEB_CLIENT_SECRET`, `COLOEUS_WEB_BASE_URL` in `application.yml` without defaults;
      `dev` defaults in `application-dev.yml` (`http://localhost:8080`).
- [ ] B2.2 `RequiredSettingsGuard` names each missing variable; test with an injected environment.
- [ ] B2.3 Token-endpoint and end-session URLs derived from the tenant id; no hard-coded tenant.

### B3. Schema

- [ ] B3.1 Flyway `V2__admin_web.sql`: `staff_session`, `staff_login`, `staff_action` (insert-only trigger as for the journal).
- [ ] B3.2 `idempotency_key`: add `scope`, backfill `client:<client_id>`, primary key `(scope, key)`, client foreign key only for
      client scopes; keep stored answers.
- [ ] B3.3 Extend the `transaction.kind` check by `RESET` and `ADJUST`; index `posting (created_at)` for the window aggregates.
- [ ] B3.4 Migration test on a Testcontainers Postgres that seeds V1 data, runs V2 and checks the backfill.

### B4. Sessions and CSRF

- [ ] B4.1 Session repository: random 256-bit id, SHA-256 hash stored, absolute 8 h and idle 30 min checked with the injected
      `Clock`.
- [ ] B4.2 Server filter on `/admin/api/**` that resolves the `__Host-coloeus_staff` cookie into `Authentication` with the
      session's roles and refreshes the idle time; expired sessions answer 401 `SESSION_REQUIRED` and are deleted.
- [ ] B4.3 CSRF check for writes under `/admin/api/**` and `POST /admin/auth/logout`: `X-CSRF-Token` in constant time and an
      `Origin` equal to the web base URL; refusal before the body is read.
- [ ] B4.4 Unit tests: expiry at the boundaries, token mismatch, missing and foreign `Origin`, bearer token refused on
      `/admin/api`, session cookie refused on `/v1`.

### B5. Login (BFF)

- [ ] B5.1 `GET /admin/auth/login`: state, nonce, PKCE verifier, `staff_login` row with 10 min expiry, `returnTo` validation.
- [ ] B5.2 `GET /admin/auth/callback`: single-use state, code exchange with the client secret, validation of ID token and
      access token (signature, `iss`, `aud`, `nonce`, `exp`), role gate (no session without a staff role).
- [ ] B5.3 Session creation and `Set-Cookie`; refused logins redirect to `/admin/?error=login` or `?error=forbidden` without
      detail; log without tokens.
- [ ] B5.4 Tests against a fake token endpoint and locally signed test tokens: replayed state, foreign audience, missing role,
      expired login state, cookie attributes.

### B6. Logout and front-channel logout

- [ ] B6.1 `POST /admin/auth/logout` (CSRF) deletes the session, expires the cookie, answers `{"logoutUrl"}` with
      `post_logout_redirect_uri`.
- [ ] B6.2 `GET /admin/auth/frontchannel-logout?iss&sid` deletes sessions by `sid` for the tenant issuer only; 400 for a
      foreign issuer; `Cache-Control: no-store`.
- [ ] B6.3 `GET /admin/auth/logout` answers 405.

### B7. Staff reads

- [ ] B7.1 `GET /admin/api/me` with `csrfToken`, `sessionExpiresAt`, `idleExpiresAt`.
- [ ] B7.2 `GET /admin/api/accounts/search` (register only, no Mojang call; exactly one parameter; at most 20 hits).
- [ ] B7.3 `GET /admin/api/accounts/{id}` and `…/postings` with `StaffPosting` (currency, `actorName`), cursor as `/v1`.
- [ ] B7.4 `GET /admin/api/money-flow` (`7D`, `ALL`) and `GET /admin/api/money-flow/series` (zero-filled UTC days), per
      `staff-money-flow`.
- [ ] B7.5 `GET /admin/api/clients`, `GET /admin/api/currencies`, `GET /admin/api/staff-actions` with the envelope and cursor.
- [ ] B7.6 Tests: name with history, unknown name without Mojang call, circulation identity, day boundary in UTC, zero-filled
      series, cursor errors (integration tests with Testcontainers; unit tests with fakes).

### B8. Staff writes

- [ ] B8.1 Staff caller for the booking (no client): reuse `BookingService` with `ADMIN_SOURCE` and `ADMIN_SINK` only; no
      grant check.
- [ ] B8.2 `POST /admin/api/accounts/{id}/reset`: whole balance of one or all currencies, `NOTHING_TO_RESET`, kind `RESET`,
      actor oid, `staff_name` metadata, `staff_action` row in the same transaction.
- [ ] B8.3 `POST /admin/api/accounts/{id}/adjust`: CREDIT and DEBIT, `INSUFFICIENT_FUNDS`, `SCALE_EXCEEDED`, `AMOUNT_INVALID`,
      kind `ADJUST`.
- [ ] B8.4 Idempotency in scope `staff:<oid>` for every staff `POST` and `PUT`: key row in the booking transaction, replay of
      the stored 201 answer, `IDEMPOTENCY_CONFLICT` for another body.
- [ ] B8.5 `POST /admin/api/clients`, `PUT /admin/api/clients/{clientId}/grants` (with `staff_action` rows, no transaction).
- [ ] B8.6 `POST /admin/api/currencies`, `PUT /admin/api/currencies/{code}/default` (with `staff_action` rows).
- [ ] B8.7 Moderator writes answer 403 `FORBIDDEN_ROLE` before the body is read.
- [ ] B8.8 Tests: reset and adjust per `staff-operations`, concurrent reset against a concurrent transfer (two application
      contexts, one database, balance never negative), no `SOURCE`/`SINK` booking, replay and conflict.

### B9. Problems, documentation and OpenAPI

- [ ] B9.1 Add `SESSION_REQUIRED`, `CSRF_INVALID`, `NOTHING_TO_RESET`, `INTERNAL` to `ProblemCode` and to `docs/problems.md`;
      test that every code is documented.
- [ ] B9.2 Map unexpected exceptions of `/admin/api` to 500 `INTERNAL` without cause or table in `detail`.
- [ ] B9.3 OpenAPI document includes the `/admin/api` contract (`META-INF/swagger/coloeus.yml`); the frontend types are
      regenerated from it (see F2.4).
- [ ] B9.4 README: the three variables in the configuration table and a section on the staff UI.

### B10. Serving and headers

- [ ] B10.1 Static resources under `classpath:admin` at `/admin/**`; `_nuxt/` immutable caching; `index.html` `no-store`.
- [ ] B10.2 SPA fallback for `GET` under `/admin` that is neither `/admin/api/**` nor `/admin/auth/**` and matches no file.
- [ ] B10.3 Security headers of `specs/staff-web-ui` on `/admin/**` (CSP, `nosniff`, `Referrer-Policy`, `frame-ancestors`).
- [ ] B10.4 Test with a fixture `admin/index.html` on the test classpath: deep link 200, unknown asset 404, headers present.

### B11. Operations

- [ ] B11.1 Daily job deleting expired `staff_session` and `staff_login` rows with the injected `Clock`.
- [ ] B11.2 Logging of staff writes and refusals at INFO/WARN without secrets (`staff-operations`); OpenTelemetry spans for the
      BFF routes and the staff API.

## Frontend (web module)

### F1. Project setup

- [ ] F1.1 `web/package.json` with Nuxt 4 (`ssr: false`, `app.baseURL` `/admin/`), Vue 3.5, Nuxt UI 4, Tailwind 4, VueUse,
      `@nuxt/eslint`, `eslint-plugin-vuejs-accessibility`, Vitest, `@vue/test-utils`, happy-dom, `vue-tsc`, `openapi-typescript`;
      versions as in `Apus/ui/package.json` on the day of the task; `packageManager` pnpm 11.20.0; `engines` Node 24.
- [ ] F1.2 `pnpm-lock.yaml` committed; `pnpm install --frozen-lockfile` works in a clean checkout.
- [ ] F1.3 Scripts: `generate`, `lint`, `typecheck`, `test`, `api:types`; no runtime configuration file.

### F2. Gradle packaging and contract types

- [ ] F2.1 `web/build.gradle.kts` with the `com.github.node-gradle.node` plugin: pinned Node 24 and pnpm; tasks
      `pnpmInstall` and `pnpmGenerate`; outgoing configuration with `web/.output/public`.
- [ ] F2.2 `backend/build.gradle.kts`: `processResources` depends on the outgoing configuration and copies the files to
      `admin/`; the build fails when `admin/index.html` is missing.
- [ ] F2.3 The backend image needs no Node: verify the Micronaut Docker layers contain only the jar.
- [ ] F2.4 `web/app/types/api.d.ts` generated from the backend OpenAPI document with `openapi-typescript`; the script
      `api:types` regenerates it; a check fails when it is stale.

### F3. Shell and design system

- [ ] F3.1 Layout: skip link, navigation with the pages of `staff-web-ui` filtered by role, header with user name and logout
      button (CSRF token from `/me`), responsive down to 320 px, no horizontal scroll.
- [ ] F3.2 Design tokens on `:root` with dark and light variants; text contrast 4.5:1; focus ring; `prefers-reduced-motion`.
- [ ] F3.3 German message module (`web/app/i18n/de.ts`) for all texts; no literal German strings in components.
- [ ] F3.4 Document title per page ("<Titel> · Coloeus"), `lang="de"`, 404 page "Seite nicht gefunden".

### F4. API client and session handling

- [ ] F4.1 Typed client over `/admin/api` (same origin) with the shapes of `api.d.ts`; sends `X-CSRF-Token` and `Origin` on
      writes; `Idempotency-Key` helper that a dialog keeps for retries.
- [ ] F4.2 Problem mapping: problem `code` to a German message; `SESSION_REQUIRED` redirects to `/admin/anmelden` with the
      return path.
- [ ] F4.3 Read the `me` answer once per page load; roles decide what is enabled; disabled write controls carry the explanation
      text.
- [ ] F4.4 Unit tests with a fake fetch: header presence, key reuse on retry, problem mapping, redirect on expired session.

### F5. Formatting

- [ ] F5.1 Money formatter on decimal strings (no `Number`): comma separator, trailing zeros kept, sign, currency fraction digits.
- [ ] F5.2 Date and time formatter in `de-DE` in the user's time zone; instants stay UTC in the model.
- [ ] F5.3 Tests: `"-0.10"`, eight fraction digits, `"0"`, UTC to local conversion with an injected time zone.

### F6. Übersicht (money flow)

- [ ] F6.1 Window toggle "7 Tage" and "Gesamt"; cards per currency with created, destroyed, net and circulation.
- [ ] F6.2 Table per client and currency; staff bucket shown separately.
- [ ] F6.3 Daily chart as inline SVG with `role="img"` and label; "Als Tabelle anzeigen" alternative; 7 and 30 days.

### F7. Konten (search and detail)

- [ ] F7.1 Search form with name and UUID, validation as in the contract, results list, "Noch kein Konto".
- [ ] F7.2 Detail page: owner, balances, history list with "Mehr laden".
- [ ] F7.3 Reset dialog: currency choice including "alle Währungen", reason, amounts preview, confirmation button disabled until
      valid.
- [ ] F7.4 Adjust dialog: direction, currency, amount with client-side format check, reason; error messages per `staff-web-ui`.
- [ ] F7.5 Dialog focus trap, Escape, focus return; success status message in the live region.

### F8. Server und Rechte (clients and grants)

- [ ] F8.1 List of clients with grants as a table (stacked below `md`).
- [ ] F8.2 "Server registrieren" dialog (UUID and display name) and "Rechte ersetzen" editor (rows currency and permission,
      duplicates refused in the form).

### F9. Währungen

- [ ] F9.1 List with code, names, symbol, fraction digits and default marker (text, not color only).
- [ ] F9.2 "Währung anlegen" form with the ledger's rules; "Als Standard setzen" with confirmation.

### F10. Protokoll

- [ ] F10.1 Paginated list of staff actions with the German action names and links to the account or client.

### F11. Anmelden and errors

- [ ] F11.1 Login page with the start button; `?error=login` and `?error=forbidden` messages in German.
- [ ] F11.2 Moderator view: every write control disabled with "Nur Administratoren können ändern".

### F12. Quality

- [ ] F12.1 `pnpm lint` passes with `eslint-plugin-vuejs-accessibility` and no disabled rule without a comment.
- [ ] F12.2 `pnpm typecheck` passes; `pnpm test` runs without network and without timers (injected clock, fake client).
- [ ] F12.3 Manual check: keyboard only through all pages; screen reader names of dialogs and charts; 360 px width without
      horizontal scroll; contrast of both themes.

## Integration

- [ ] I1 Run `./gradlew build` under `flock ~/.cache/claude-heavy.lock`; check that the jar contains `admin/index.html`.
- [ ] I2 Entra: create the app registration "Coloeus Admin Web" with redirect URI
      `https://coloeus.apps.onelite.feather/admin/auth/callback`, front-channel logout URL
      `https://coloeus.apps.onelite.feather/admin/auth/frontchannel-logout`, a client secret for `COLOEUS_WEB_CLIENT_SECRET`,
      the delegated permission `Coloeus.Access` of the API registration, and user assignment required; check that the API
      registration exposes `Coloeus.Access`.
- [ ] I3 Local end to end against the dev database: a moderator sees the read pages and no enabled write control; an admin
      resets, adjusts, registers a client, replaces grants, creates a currency and sets the default; each write appears in
      the log once; logout ends the session; a front-channel call ends the session of its `sid`.
- [ ] I4 Check the request flow of a duplicate submit: the same `Idempotency-Key` returns the same booking and no second
      journal row exists.
- [ ] I5 Open the code pull request `feat(admin): add staff web ui` from the implementation only: no file under `openspec/`,
      branched from `main` after this proposal is merged and after `add-ledger-core` is merged. The description follows the
      repository's pull request template and names the decisions of `design.md`, section "Decisions taken here".
