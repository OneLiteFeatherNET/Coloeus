# Spec Delta

## Purpose
The staff web interface: which pages exist, what each shows and may do, how it is served by its own Nitro server, how its components
are built, and the accessibility and layout rules. The UI calls only the browser-facing contract of `staff-api`.

## ADDED Requirements

### Requirement: Pages and routes
The UI SHALL offer the pages below under the base path `/admin/`, with German titles. Every page title SHALL have the form
`<Titel> · Coloeus`. A route not listed SHALL show "Seite nicht gefunden" with a link to `/admin/`.

```text
/admin/                 Übersicht          money flow per currency and client, daily chart (money-flow, money-flow/series)
/admin/konten           Konten             search by player name or UUID
/admin/konten/:id       Konto              owner, balances, history; "Zurücksetzen" and "Anpassen" (admins)
/admin/clients          Server und Rechte  clients and grants; "Server registrieren", "Rechte ersetzen" (admins)
/admin/waehrungen       Währungen          currencies; "Währung anlegen", "Als Standard setzen" (admins)
/admin/protokoll        Protokoll          staff action log, newest first, paginated
/admin/anmelden         Anmelden           starts the login; shows ?error=login or ?error=forbidden
```

#### Scenario: Moderator navigation
- **GIVEN** a session with `Coloeus.Moderator`
- **WHEN** the navigation renders
- **THEN** every page of the table is listed, and every write control is visible but disabled with the text "Nur Administratoren können ändern"

#### Scenario: Expired session on any page
- **WHEN** a page's request answers 401 `SESSION_REQUIRED`
- **THEN** the UI navigates to `/admin/anmelden` and keeps the current path as the return path

### Requirement: Account search
The search page SHALL accept a player name (1 to 16 characters of `A-Za-z0-9_`) or a UUID, validated as the API validates them, and SHALL
list the results with the name, the last seen time and a link to the account. A result without `accountId` SHALL say "Noch kein Konto".

#### Scenario: Name not known
- **WHEN** the search returns no items
- **THEN** the page says "Kein Spieler mit diesem Namen gefunden." and offers the UUID field

### Requirement: Account detail
The detail page SHALL show the owner, every balance in German number format with the currency's fraction digits, and the history as a list
that loads the next page on request ("Mehr laden").

#### Scenario: Money is not parsed as a float
- **GIVEN** a balance `"0.10"` and a balance `"12.345678"` in a currency with 8 fraction digits
- **WHEN** the balances are shown
- **THEN** both values are shown exactly with comma as the decimal separator, and no value passes through a JavaScript number

### Requirement: Reset dialog
The "Zurücksetzen" dialog SHALL let the admin choose one currency or "alle Währungen" and SHALL require a reason of 1 to 255 characters. It
SHALL state the amounts that will move, and its button "Konto zurücksetzen" SHALL stay disabled until the form is valid.

#### Scenario: Confirmation before money moves
- **GIVEN** a player with 12.50 `COINS`
- **WHEN** an admin opens "Zurücksetzen" and chooses `COINS`
- **THEN** the dialog states "12,50 COINS werden auf das Admin-Konto übertragen" and the button is disabled until a reason is entered

### Requirement: Adjust dialog
The "Anpassen" dialog SHALL offer the direction "Gutschrift" or "Abbuchung", the currency, the amount and the reason. It SHALL check the
amount format on the client (plain decimal, no more fraction digits than the currency has) before any request is sent.

#### Scenario: Too many fraction digits
- **WHEN** an admin enters `1.005` in a currency with two fraction digits
- **THEN** the field shows the error "Der Betrag hat zu viele Nachkommastellen." and no request is sent

### Requirement: Idempotency keys in dialogs
A dialog SHALL generate one `Idempotency-Key` when it opens and SHALL reuse it for every retry of the same submit. The key travels to Nitro,
which passes it on unchanged. A successful answer SHALL close the dialog, show a status message and refresh the account.

#### Scenario: Retry keeps the key
- **GIVEN** a submit whose network request failed
- **WHEN** the admin presses the button again in the same dialog
- **THEN** the second request carries the same `Idempotency-Key`

### Requirement: Dialog error messages
Errors SHALL be shown next to the form in German: `NOTHING_TO_RESET` as "Dieses Konto hat nichts zurückzusetzen.", `INSUFFICIENT_FUNDS` as
"Das Guthaben reicht für diese Abbuchung nicht.", and `SCALE_EXCEEDED` as "Der Betrag hat zu viele Nachkommastellen.". A `SESSION_REQUIRED`
answer SHALL send the user to the login.

#### Scenario: Insufficient funds shown
- **WHEN** an adjust debit is answered 422 `INSUFFICIENT_FUNDS`
- **THEN** the dialog stays open and shows "Das Guthaben reicht für diese Abbuchung nicht."

### Requirement: Dashboard charts
Charts SHALL be inline SVG with an accessible name and a data table alternative, switched by the button "Als Tabelle anzeigen". The daily chart
SHALL show 7 days by default and offer 30. Colors SHALL NOT be the only way to tell created from destroyed.

#### Scenario: Chart has a table alternative
- **WHEN** the daily chart renders
- **THEN** it has `role="img"` with an `aria-label` naming the currency and the period, and the button switches to a table with the same values

### Requirement: Reusable building blocks
The UI SHALL build its pages from generic components in `web/app/components/ui/` that hold no page logic.

#### Scenario: Building blocks exist
- **WHEN** the components of `web/app/components/ui/` are listed
- **THEN** they include a button, a dialog, a table, a form field, a money display, a status message, an empty state and a page header

#### Scenario: No imports from the application
- **WHEN** the imports of `web/app/components/ui/` are checked
- **THEN** no file there imports from `pages/`, `features/`, `api/` or `server/`, and the lint step fails when one does

#### Scenario: Texts come in as props or slots
- **WHEN** a building block renders a label or a message
- **THEN** the text is a prop or a slot, and the block contains no German sentence of its own

#### Scenario: Pages import the blocks
- **WHEN** a page is built
- **THEN** it imports the building blocks, and no building block imports a page

#### Scenario: Money display is generic
- **GIVEN** the money display receives the decimal string `"-0.10"` and the currency `COINS` with two fraction digits as props
- **WHEN** it renders
- **THEN** it shows `-0,10 COINS` without any knowledge of the account it belongs to

### Requirement: Accessible structure
The UI SHALL have a skip link to the main content, the document language `de`, a visible focus indicator, and a label for every form control.
Error messages SHALL be linked to their fields with `aria-describedby`. Status messages SHALL go to an `aria-live="polite"` region. Tables SHALL
have captions, and header cells SHALL have `scope`.

#### Scenario: Skip link
- **WHEN** a keyboard user opens any page and presses Tab once
- **THEN** the skip link to the main content receives focus

### Requirement: Accessible contrast and motion
Text contrast SHALL be at least 4.5:1 in both themes. No information SHALL be carried by color alone. Motion SHALL be reduced under
`prefers-reduced-motion`.

#### Scenario: Reduced motion
- **GIVEN** a user with `prefers-reduced-motion: reduce`
- **WHEN** a page with a chart is shown
- **THEN** no transition or animation runs

### Requirement: Accessible dialogs
Dialogs SHALL trap focus, close with Escape, and return focus to the element that opened them. A successful dialog SHALL return focus to its
trigger.

#### Scenario: Dialog focus
- **WHEN** the "Anpassen" dialog closes after success
- **THEN** focus returns to the "Anpassen" button of the account page

### Requirement: Lint enforces accessibility
The ESLint configuration SHALL include `eslint-plugin-vuejs-accessibility`, and the lint step SHALL fail on its errors.

#### Scenario: Missing label
- **WHEN** a form control without a label is added and the lint step runs
- **THEN** the lint step fails

### Requirement: Responsive layout
The UI SHALL be usable from 320 CSS pixels: no horizontal page scroll, tables shown as stacked lists below the `md` breakpoint, and touch
targets of at least 44 by 44 CSS pixels.

#### Scenario: Phone width
- **WHEN** the account page is rendered at 360 pixels width
- **THEN** the page has no horizontal scroll bar and the history is shown as a list

### Requirement: German texts and time
All UI texts SHALL be German (`de-DE`) and kept in one module of message strings. Dates SHALL be shown in `de-DE` format in the user's time
zone, while API instants stay UTC in the model.

#### Scenario: Instant display
- **GIVEN** `createdAt` `2026-11-08T10:15:30Z`
- **WHEN** it is shown to a user in Berlin
- **THEN** the page shows `08.11.2026, 11:15`

### Requirement: Served by the Nitro server under /admin
The `coloeus-admin` service SHALL serve the built pages from `/admin/` and the browser-facing API from `/admin/api/`, on one origin, from its
own Nitro server. The backend SHALL NOT serve any file under `/admin`. The UI SHALL read no runtime configuration file. Hashed assets under
`/admin/_nuxt/` SHALL be sent with `Cache-Control: public, max-age=31536000, immutable`.

#### Scenario: Unknown asset
- **WHEN** a browser requests `GET /admin/_nuxt/missing.js`
- **THEN** the answer is 404

#### Scenario: Backend serves no UI
- **WHEN** a browser requests `GET /admin/konten` on the backend service
- **THEN** the backend does not answer with the UI, because the HTTPRoute sends `/admin` to the `coloeus-admin` service

### Requirement: SPA fallback and no-store shell
The application shell (`index.html`) SHALL be sent with `Cache-Control: no-store`. Any `GET` under `/admin` that matches no file, and is
neither under `/admin/api/` nor under `/admin/auth/`, SHALL be answered with the shell.

#### Scenario: Deep link
- **WHEN** a browser requests `GET /admin/konten/5f0c…` directly
- **THEN** the answer is the shell of the UI with status 200 and `Cache-Control: no-store`

### Requirement: Security headers of the UI
Every answer under `/admin/` SHALL carry the header `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'
'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`, `X-Content-Type-Options:
nosniff` and `Referrer-Policy: same-origin`. The build SHALL emit no inline script.

#### Scenario: Framing refused
- **WHEN** another site embeds `/admin/`
- **THEN** the browser refuses to display it because of `frame-ancestors 'none'`

### Requirement: Health endpoint
Nitro SHALL serve `GET /admin/healthz`, which answers 200 without a session and without a call to Dragonfly or the backend, for the
readiness probe.

#### Scenario: Readiness without a session
- **WHEN** the probe calls `GET /admin/healthz` with no cookie while Dragonfly is down
- **THEN** the answer is 200

### Requirement: Nuxt application with Node 24 and pnpm
`web/` SHALL be a Nuxt 4 application with `ssr: false` for its pages, `baseURL` `/admin/`, and the Nitro server for its BFF routes, built with
pnpm from a lockfile installed with `--frozen-lockfile`, using Node 24. The build SHALL NOT depend on a host Node installation.

#### Scenario: Clean install
- **WHEN** `pnpm install --frozen-lockfile` runs in a clean checkout
- **THEN** the install succeeds without changing the lockfile

### Requirement: Container image of the UI
The UI SHALL be built into the image `harbor.onelitefeather.dev/onelitefeather/coloeus-admin` from a Dockerfile in `web/`: a Node 24 build
stage, and a Node 24 runtime stage that runs the Nitro server as a non-root user on port 3000 and contains no source code. The release workflow
SHALL build and push this image next to the backend image.

#### Scenario: Image starts without the backend
- **WHEN** the image starts with its settings and no backend is reachable
- **THEN** `GET /admin/healthz` answers 200

### Requirement: Committed contract types
The frontend types of the contract SHALL be generated from the backend OpenAPI document (including `/v1/staff`) into `web/app/types/`, and
committed. The `Me` shape, which only the browser path has, SHALL be defined in `web/shared/` and tested against `staff-api`. A check SHALL fail
when the committed file differs from a fresh generation.

#### Scenario: Stale types
- **GIVEN** a changed backend contract that was not regenerated
- **WHEN** the check runs
- **THEN** the check fails and names the stale file

### Requirement: Offline frontend tests
The UI's unit tests SHALL run without network, without a running backend or Dragonfly, and without the system clock. The clock SHALL be
injected into the formatting helpers and into the session logic of Nitro, the API client and the Redis-backed storage SHALL be replaced by
fakes, and no test SHALL wait for timers. One integration test MAY use a Dragonfly container and SHALL be tagged as integration.

#### Scenario: Money formatting
- **WHEN** the formatter gets `"-0.10"` in a currency with two fraction digits
- **THEN** it returns `-0,10`
