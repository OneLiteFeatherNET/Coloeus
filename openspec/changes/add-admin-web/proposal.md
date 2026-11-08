# Proposal

**Ships as:** `feat(admin)` — one code pull request `feat(admin): add staff web ui`.
It opens only after this proposal is merged to `main` and after `add-ledger-core` is merged, because the staff API calls
the ledger services that change defines. The rollout needs one new deployment: the staff UI runs as its own service
(`coloeus-admin`, a Nuxt 4 application served by its Nitro server), with its own container image and HelmRelease. The
backend gets a bearer staff API and no UI files. Only the Entra app registration and the Nitro settings are new.

## Why

Staff of the OneLiteFeather network answer player questions today by reading the database or by typing commands that
exist in no single place. A player who asks where their coins went cannot be answered from one screen: the balance per
server, the money a client created and destroyed, and the history of who changed what are spread over tables and logs.
Refunds and corrections are made by hand, and nothing records which staff member made them.

The ledger (`add-ledger-core`) already keeps every movement as an append-only journal with an actor, a reason and a
client. What is missing is a place where moderators can read that journal and admins can act on it, with every action
attributed to a named staff member. This change adds that place.

The first revision put the login and the UI into the backend. The maintainer decided otherwise: the staff UI is a Nuxt 4
application with its own Nitro server, which acts as the backend-for-frontend (BFF). Nitro holds the Entra login, the
server-side session, CSRF checks and the token refresh; the browser holds only a cookie. The backend stays a pure
bearer-token API: it serves the same staff resources to Nitro under a staff path and never sees a cookie, a session or
a static file.

## What Changes

- **Login for staff (Nitro).** Nitro runs the Entra login: authorization code flow with PKCE against the confidential web
  client "Coloeus Admin Web". Only users with the app role `Coloeus.Moderator` or `Coloeus.Admin` get a session. The
  browser holds an HttpOnly, Secure, SameSite=Lax cookie `__Host-coloeus_staff`. The session (identity, roles, CSRF token,
  and the Entra tokens encrypted) lives in the cluster's Dragonfly through the Nitro storage layer, so two replicas share
  sessions and front-channel logout finds sessions by `sid`. Sessions expire after 8 hours absolute and 30 minutes idle.
  Logout ends the session at Entra as well, and a front-channel logout endpoint ends the sessions of a given `sid`.
- **Browser API on Nitro, `/admin/api/**`.** Same JSON contract as before, complete in `specs/staff-api`, which the UI
  codes against. Nitro answers `me` itself, checks CSRF and the roles, and forwards every other call to the backend with
  the staff member's delegated access token as a bearer token.
- **Staff API on the backend, `/v1/staff/**`.** The same resources and JSON shapes, authorized by the user token: app roles
  `Coloeus.Moderator` (reads) and `Coloeus.Admin` (writes), actor = token `oid`, display name = token `name`. The backend
  refuses client-credential (app) tokens on these routes, and refuses delegated staff tokens on every other `/v1` route.
- **Staff writes are audited.** Every money write is a journal transaction whose actor is the staff member's Entra object
  id (`oid`) and whose metadata carries the display name. Every staff write, money or configuration, is also listed in
  an append-only `staff_action` log. Every staff write requires an `Idempotency-Key`; Nitro passes it through and the
  backend scopes it to `staff:<oid>`.
- **Staff web UI** in German, served by Nitro under `/admin`: dashboard (money flow), account search and detail with reset
  and adjust dialogs that require confirmation, clients and grants, currencies, staff actions log. The UI is built from
  reusable building blocks (button, dialog, table, form field, money display, status message) that hold no page logic.
  Accessible (WCAG 2.2 AA) and usable at phone width.
- **Deployment.** A second image `harbor.onelitefeather.dev/onelitefeather/coloeus-admin`, built in the release workflow,
  and a second HelmRelease. The HTTPRoute sends `/admin` to the Nitro service and everything else to the backend. The
  backend no longer needs the `COLOEUS_WEB_*` variables and no longer contains any UI file.
- **Problem Details.** Five codes are added and documented in `docs/problems.md`: `SESSION_REQUIRED` and `CSRF_INVALID`
  (answered by Nitro), `NOTHING_TO_RESET` and `INTERNAL` (answered by the backend), and `NOT_FOUND` for unknown routes
  under `/admin/api` (answered by Nitro).

Decisions taken in this proposal that were not part of the agreed requirements are listed in `design.md`, section
"Decisions taken here", and mirrored in the report of the pull request.

## Capabilities

### New Capabilities

- `staff-login`: Nitro BFF login, server-side sessions, expiry, logout, front-channel logout, CSRF protection of staff writes.
- `staff-api`: the `/admin/api/**` contract served by Nitro (browser-facing) and the `/v1/staff/**` bearer contract served by
  the backend, with the envelope and pagination rules. Both halves implement the same resources and shapes.
- `staff-money-flow`: created and destroyed money per client and currency, circulation, daily series (backend).
- `staff-operations`: reset and adjust of player balances, client registry and grant writes, currency writes, the staff
  action log, attribution and idempotency rules (backend).
- `staff-web-ui`: pages, language, accessibility, responsive layout, reusable building blocks, and the Nuxt application with
  its Nitro server and container image.
- `problem-details`: the four new codes and `NOT_FOUND` (delta).

### Modified Capabilities

None in `openspec/specs/`, which is empty. The `/v1` API keeps its bearer rules and the ledger rules, with one
exception that is recorded here: `/v1/**` routes other than `/v1/staff/**` refuse delegated staff tokens. That rule is a
delta to the `service-auth` capability of `add-ledger-core` and is applied by backend task B4.2 (see Impact).

## Impact

- `backend`: a bearer staff API under `/v1/staff/**` that reuses the existing booking, account, currency and client
  services; a token gate for staff routes; a static-free service. No session filter, no `/admin` routes, no static
  resource mapping.
- `web`: a Nuxt 4 application (`web/`) with `ssr: false` for the pages and a Nitro server for the BFF routes. Built with
  pnpm in a Node 24 Docker stage. The backend build does not touch it.
- Database: Flyway `V2__admin_web.sql` (new table `staff_action`; `idempotency_key` keyed by scope instead of client only;
  two new transaction kinds `RESET` and `ADJUST`; an index on `posting (created_at)`). No session tables in Postgres.
  Forward-only.
- Session store: Dragonfly (Redis protocol) through the unstorage redis driver, with a key prefix `staff:` and an ACL user
  limited to that prefix.
- Configuration, Nitro: `COLOEUS_WEB_CLIENT_ID`, `COLOEUS_WEB_CLIENT_SECRET`, `COLOEUS_WEB_BASE_URL`,
  `COLOEUS_WEB_SESSION_SECRET`, `COLOEUS_WEB_STORAGE_URL`, `COLOEUS_TENANT_ID`, `COLOEUS_AUDIENCE`, `COLOEUS_BACKEND_URL`.
  The service does not start without them; dev defaults live in the Nitro dev configuration only.
- Configuration, backend: `COLOEUS_TENANT_ID` and `COLOEUS_AUDIENCE` (existing). No `COLOEUS_WEB_*` variable.
- Entra: a new app registration "Coloeus Admin Web" with redirect URI
  `https://coloeus.apps.onelite.feather/admin/auth/callback`, front-channel logout URL
  `https://coloeus.apps.onelite.feather/admin/auth/frontchannel-logout`, a client secret, and the delegated permission
  `api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/Coloeus.Access`. The API registration must expose that scope. The web client
  must not be registered as a client in the ledger client registry (see design, risks).
- Kubernetes (FLUX repository, outside this change): a second HelmRelease `coloeus-admin` with two replicas, an HTTPRoute
  rule `/admin` to its service, and a Dragonfly ACL user for `staff:*`. The backend HelmRelease loses the `COLOEUS_WEB_*`
  values.
- `add-ledger-core`: no change. The `/v1` routes keep the rules of `service-auth`; delegated staff tokens are already used
  there today for the admin endpoints (for example from the Azure CLI), and writes still need a registered client.
- Build: Node 24 and pnpm are used by the `web` Docker stage and by the release workflow. The backend build and image need
  no Node.
- Documentation: `docs/problems.md` gets the five codes in the code pull request; the README gets the Nitro settings and a
  section on the staff UI.

## Out of scope

- A player-facing web UI (no player logs in to this application).
- A Discord bot, Velocity commands, banks (`BANK` accounts stay unused), and real-time updates (SSE): the UI refreshes on
  navigation and by explicit reload.
- Editing currencies (names, symbol) after creation, deleting clients or grants beyond replacement, and changing
  fraction digits (already forbidden by the ledger).
- Rules that change money flow (fees, limits, overdraft): `add-ledger-rules`.
- Moving the building blocks into a shared package. They are built so that the move is mechanical; the move itself is a
  later change, when a second application needs them.
- The Kubernetes manifests, the Dragonfly ACL and the Entra app registration, which are created by the operator outside this
  repository.

## Open questions (recorded, not resolved)

- **"AMW" in the money flow.** The requirement asks "wie viel Geld nach AMW fließt" but does not define AMW. The money-flow
  view is built generically per client and per account, so it can show AMW once the term is defined. No AMW figure is
  specified here; `staff-money-flow` only defines created, destroyed, net and circulation per client and currency.
- **Entra claims.** The design relies on the `sid` and `nonce` claims of the ID token, the `oid`, `name`, `roles` and `scp`
  claims of the access token of `Coloeus.Access`, and the absence of `scp` in client-credential tokens. Task B1 verifies
  them against a real token before the backend and Nitro are built on them.
