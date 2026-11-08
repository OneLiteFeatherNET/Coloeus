# Proposal

**Ships as:** `feat(admin)` — one code pull request `feat(admin): add staff web ui`.
It opens only after this proposal is merged to `main` and after `add-ledger-core` is merged, because the staff API calls
the ledger services that change defines. The Kubernetes rollout needs no new deployment: the staff UI ships inside the
existing backend image, and only the Entra app registration and three environment variables are new.

## Why

Staff of the OneLiteFeather network answer player questions today by reading the database or by typing commands that
exist in no single place. A player who asks where their coins went cannot be answered from one screen: the balance per
server, the money a client created and destroyed, and the history of who changed what are spread over tables and logs.
Refunds and corrections are made by hand, and nothing records which staff member made them.

The ledger (`add-ledger-core`) already keeps every movement as an append-only journal with an actor, a reason and a
client. What is missing is a place where moderators can read that journal and admins can act on it, with every action
attributed to a named staff member. This change adds that place: a staff web interface served by the backend itself, so
that it is one more route of the service that already holds the money.

## What Changes

- **Login for staff.** A backend-for-frontend (BFF) login with Entra ID: authorization code flow with PKCE against a
  confidential web client ("Coloeus Admin Web"). Only users with the app role `Coloeus.Moderator` or `Coloeus.Admin` get a
  session. The browser holds an HttpOnly, Secure, SameSite=Lax cookie; the session itself lives server-side in Postgres,
  so every replica can serve every request. Sessions have an absolute and an idle expiry, and logout ends the session at
  Entra as well (single sign-out, including a front-channel logout endpoint that ends the session of a given `sid`).
- **Staff API under `/admin/api/**`.** Session-authenticated JSON, complete in `specs/staff-api`, that the backend and the
  frontend implement in parallel. It covers `me`, account search by player name or UUID, account details with balances
  and cursor-paginated postings, the money flow per client and currency (window, all-time and a daily series), reset and
  adjust of player balances (admins only), the client registry with grants, currencies, and the staff action log.
- **Staff writes are audited.** Every money write is a journal transaction whose actor is the staff member's Entra object
  id (`oid`) and whose metadata carries the display name. Every staff write, money or configuration, is also listed in
  an append-only `staff_action` log. Every staff write requires an `Idempotency-Key`.
- **Staff web UI** in German, served under `/admin` from the backend: dashboard (money flow), account search and detail
  with reset and adjust dialogs that require confirmation, clients and grants, currencies, staff actions log. Accessible
  (WCAG 2.2 AA) and usable at phone width.
- **Packaging.** The `web` module builds the static frontend; the `backend` module packages its output on the classpath
  under `admin/`, so the existing image serves both the API and the UI.
- **Problem Details.** Four codes are added: `SESSION_REQUIRED`, `CSRF_INVALID`, `NOTHING_TO_RESET` and `INTERNAL`.

Decisions taken in this proposal that were not part of the agreed requirements are listed in `design.md`, section
"Decisions taken here", and mirrored in the report of the pull request.

## Capabilities

### New Capabilities

- `staff-login`: BFF login, server-side sessions, expiry, logout, front-channel logout, CSRF protection of staff writes.
- `staff-api`: the complete `/admin/api/**` contract (paths, methods, query and body shapes, errors) and the envelope and
  pagination rules.
- `staff-money-flow`: created and destroyed money per client and currency, circulation, daily series.
- `staff-operations`: reset and adjust of player balances, client registry and grant writes, currency writes, the staff
  action log.
- `staff-web-ui`: pages, language, accessibility, responsive layout, serving and packaging of the static frontend.
- `problem-details`: the four new codes (delta).

### Modified Capabilities

None. The `/v1` API, its bearer authentication and the ledger rules are unchanged. A staff session never authorizes a
`/v1` call and a service bearer token never authorizes a `/admin/api` call.

## Impact

- `backend`: session filter and BFF controllers under `/admin/auth` and `/admin/api`, staff services that reuse the
  existing booking, account, currency and client services, a static resource mapping and an SPA fallback for `/admin`.
- `web`: a Nuxt 4 static single-page application (`web/`), built with pnpm and packaged by Gradle.
- Database: Flyway `V2__admin_web.sql` (new tables `staff_session`, `staff_login`, `staff_action`; `idempotency_key`
  keyed by scope instead of client only; two new transaction kinds `RESET` and `ADJUST`). Forward-only.
- Configuration: `COLOEUS_WEB_CLIENT_ID`, `COLOEUS_WEB_CLIENT_SECRET` and `COLOEUS_WEB_BASE_URL` are required; the service
  does not start without them (`RequiredSettingsGuard`). Local defaults for `dev` only, in `application-dev.yml`.
- Entra: a new app registration "Coloeus Admin Web" with redirect URI
  `https://coloeus.apps.onelite.feather/admin/auth/callback`, front-channel logout URL
  `https://coloeus.apps.onelite.feather/admin/auth/frontchannel-logout`, a client secret, and the delegated permission
  `api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/Coloeus.Access`. The API registration must expose that scope.
- Build: Node 24 and pnpm are used by the `web` module through the Gradle build; the backend image itself needs no Node.
- Documentation: `docs/problems.md` gets the four codes in the code pull request; the README gets the three variables.

## Out of scope

- A player-facing web UI (no player logs in to this application).
- A Discord bot, Velocity commands, banks (`BANK` accounts stay unused), and real-time updates (SSE): the UI refreshes on
  navigation and by explicit reload.
- Editing currencies (names, symbol) after creation, deleting clients or grants beyond replacement, and changing
  fraction digits (already forbidden by the ledger).
- Rules that change money flow (fees, limits, overdraft): `add-ledger-rules`.
- The Kubernetes manifests and the Entra app registration itself, which are created by the operator outside this
  repository.

## Open questions (recorded, not resolved)

- **"AMW" in the money flow.** The requirement asks "wie viel Geld nach AMW fließt" but does not define AMW. The money-flow
  view is built generically per client and per account, so it can show AMW once the term is defined. No AMW figure is
  specified here; `staff-money-flow` only defines created, destroyed, net and circulation per client and currency.
- **Entra claims.** The design relies on the `sid` and `oid` claims in the ID token and on the `roles` claim in the access
  token of the scope `Coloeus.Access`. The implementation task verifies both against a real token before the code pull
  request is opened.
