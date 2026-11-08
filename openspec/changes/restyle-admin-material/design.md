# Design

## Context

`add-admin-web` builds the staff UI as a Nuxt 4 application with Nuxt UI on Tailwind 4 (its design decision 14), and it
composes its pages from the generic building blocks of its design decision 15 (`AppButton`, `AppDialog`, `AppTable`,
`AppFormField`, `AppMoney`, `AppStatus`, `AppEmptyState`, `AppPageHeader`). The maintainer decided that the UI follows
Material Design 3 and that the building blocks stay reusable. This design keeps the Nuxt, Tailwind, Vue, Vitest and
accessibility tooling of `add-admin-web` and replaces the component library and the theme.

The CSP of `add-admin-web` is `default-src 'self'` with `style-src 'self' 'unsafe-inline'`. Fonts and icons must therefore be
served from the UI's own origin.

## Goals / Non-Goals

Goals: one seed color and one build step define every color role; the same CSS custom properties and Tailwind theme names are
used in every component; the building blocks depend only on Vue, Tailwind and the token files; the look follows MD3 closely
enough to be recognised; behaviour, routes, API and accessibility do not change.

Non-goals: a Material component library; a copy of the full MD3 specification (only the roles, scales and components listed in
the proposal); runtime theme switching beyond light, dark and system; a theme editor; a shared package (a later change).

## Decisions

### 1. One seed, generated at build time

A script `web/scripts/generate-tokens.ts` reads the seed from `web/design/seed.ts` (`#3D5A80`, the only place it is written) and
calls `themeFromSourceColor` from `@material/material-color-utilities`. It writes `web/app/assets/css/tokens.generated.css` with
the color roles for `:root` (light) and for dark, under `:root[data-theme="dark"]` and under
`@media (prefers-color-scheme: dark)` guarded by `:root:not([data-theme="light"])`. The generated file is committed. A test
regenerates the tokens in memory and fails when the committed file differs, so a stale file cannot ship. The script runs with
`pnpm tokens` and before `build`.

The generator is deterministic: the same seed always gives the same file. It uses no time, no random value and no network.

*Rejected:* generating the colors in the browser at run time (the palette would be computed on every page load and could not be
checked in a test). *Rejected:* hand-written palettes (they drift from the MD3 tonal rules and cannot be regenerated for another
seed).

### 2. Tailwind 4 theme mapping

`web/app/assets/css/theme.css` imports Tailwind and declares `@theme` entries that point at the custom properties, for example
`--color-primary: var(--md-sys-color-primary)`, so that `bg-primary`, `text-on-primary-container` and
`bg-surface-container-high` work. The type scale, shape, elevation and motion tokens are declared in the same way
(`text-title-medium`, `rounded-md`, `shadow-elevation-2`, `ease-emphasized`). Components use these utilities only; no component
hard-codes a color or a size that a token covers.

*Rejected:* a Tailwind preset package (a second package boundary for a theme that is used in one place).

### 3. Dark mode

The default is the system preference. The existing toggle sets `data-theme` on `<html>` to `light` or `dark`; the value `system`
removes the attribute. The choice is kept in `localStorage`, read and written inside `try`/`catch`; when storage is unavailable the
page follows the system preference. The client plugin of the Nuxt app applies the stored choice before the first render, so the
build still emits no inline script.

Both schemes meet the contrast rule. The token test checks the pairs `on-surface` / `surface`, `on-primary` / `primary`,
`on-surface-variant` / `surface-container-highest` and `on-error` / `error`, each at least 4.5:1 in both schemes.

### 4. Typography and font

The type scale of MD3 (display, headline, title, body and label, each large, medium and small) is declared as Tailwind utilities that
set size, line height, weight, tracking and font family. `@fontsource-variable/roboto-flex` is imported in `theme.css`. Fontsource
copies the font files into the build output, so the browser loads them from `/admin/_nuxt/` under the `font-src` that
`default-src 'self'` gives. Only the Latin and Latin-extended subsets that German text needs are imported.

*Rejected:* Google Fonts (an external origin: it would need a CSP change and a third-party request for every visitor).

### 5. Shape, elevation, state layers and focus

Shape tokens: none 0, extra-small 4 px, small 8 px, medium 12 px, large 16 px, extra-large 28 px, full. Elevation levels 0 to 5 combine a
tonal surface container (`surface-container-lowest` to `surface-container-highest`) with a shadow. State layers are a pseudo-element
in the content color: hover 8 %, focus 10 %, pressed 10 % opacity, applied through one `state-layer` utility that every interactive
block uses. The focus indicator is a two-pixel outline in the `secondary` color with a two-pixel offset, shown on `:focus-visible`
only. The focus ring is never removed.

### 6. Motion

Easing tokens: standard, emphasized and standard-decelerate as cubic-bezier values from MD3; durations short (50 to 200 ms), medium and
long. Under `@media (prefers-reduced-motion: reduce)` every duration token becomes 0 ms, and the dialog and snackbar appear without a
transition. No information depends on an animation.

### 7. Icons

Icons come from `@iconify-json/material-symbols`. The `AppIcon` component renders the path data of the named symbol inline, read from
the installed icon set at build time; only the symbols in use are emitted. A decorative icon has `aria-hidden="true"`. An icon that is
the only content of a control gets its accessible name from the control (`aria-label`), and the icon stays hidden.

*Rejected:* the Material Symbols web font (a variable font of several megabytes for a few dozen glyphs; the SVG path keeps the bundle
small and needs no font loading).

### 8. Components

Each component is a file in `web/app/components/ui/` and keeps the rules of `add-admin-web` decision 15: props, slots and events only, no
route, no API resource, no staff role and no German sentence of its own. The existing blocks keep their names and props and change only
their markup and classes. New blocks: `AppSegmentedButton` (the window toggle "7 Tage" / "Gesamt"), `AppSelect`, `AppCheckbox`, `AppRadio`,
`AppSwitch`, `AppChip`, `AppCard` (variant elevated, filled or outlined), `AppTopAppBar`, `AppNavigation` (rail, bar or drawer, chosen by
the layout), `AppSnackbar`, `AppProgress` (linear and circular), `AppMenu` and `AppIcon`. `AppDialog` gains the MD3 basic dialog layout;
its focus trap, Escape handling and focus return are unchanged. `AppTable` shows a data table from `md` and a stacked list below it, as
`add-admin-web` requires.

`AppFormField` wraps the outlined text field: label, supporting text and error text, linked to the control with `aria-describedby`.

### 9. Window size classes and navigation

`web/app/composables/useWindowSize.ts` returns `compact`, `medium` or `expanded` from the viewport width (600 and 840 px), built on
VueUse's `useMediaQuery`, so the class follows resizes and tests can stub the media query. The layout shows the navigation rail at
`expanded`, the navigation bar at `compact` and `medium`, and a modal drawer for the menu at `compact`. The layout keeps the skip link
and the landmark structure of `add-admin-web`.

### 10. Snackbar and live region

`AppSnackbar` shows a success message visually; the message is announced through the existing `aria-live="polite"` region of `AppStatus`.
The snackbar is never a second live region. It closes after a time, and it stays open while it has focus. Errors stay next to the form as
`add-admin-web` requires.

### 11. Charts

The inline SVG charts of `add-admin-web` take their colors from the roles (`primary` for created, `tertiary` for destroyed, `outline` for
axes and grid). Colors are never the only way to tell created from destroyed: the legend and the data table keep the labels.

## Risks / Trade-offs

- The look is MD3-like, not MD3-exact. The maintainer asked for a consistent look, and exact conformance would need the component library
  this change rejects. Mitigation: the roles and scales are the MD3 ones, so single components can be aligned later.
- The generated file must match the script. Mitigation: the test that regenerates the tokens in memory and compares them to the committed
  file.
- Restyling touches every page. Mitigation: behaviour is covered by the existing tests, and pages are migrated group by group, each with a
  visual check.
- Removing `@nuxt/ui` removes its accessible defaults for some controls. Mitigation: the building blocks implement the focus, label and
  `aria-describedby` rules themselves, and `eslint-plugin-vuejs-accessibility` stays on.

## Migration Plan

1. Land the token generator and the Tailwind theme with no page change; the existing pages keep working on the new tokens.
2. Add the new building blocks and their tests.
3. Migrate the pages group by group (account, money flow, client, currency, log) and remove `@nuxt/ui` after the last page.
4. Run the visual check at 360 px, 840 px and 1280 px in light and dark, then open the code pull request.

Rollback: revert the code pull request. No data, route or deployment changes with it.

## Rejected alternatives (summary)

- Vuetify: its own styling system and theme that Tailwind does not drive; a second design language next to Tailwind.
- `@material/web`: web components with shadow DOM, which Tailwind classes cannot style from outside and which need Vue interop.
- Nuxt UI (the choice of `add-admin-web`): a different look and token set; superseded by this change.
- Tailwind preset package, Google Fonts, the Material Symbols web font, run-time palette generation: see the decisions above.
