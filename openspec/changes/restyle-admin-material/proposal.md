# Proposal

**Ships as:** `feat(admin)` — one code pull request `feat(admin): adopt material design 3 in the staff ui`.
It opens after this proposal is merged to `main`; it restyles the pages and components that `add-admin-web` created
(code merged as `feat(admin): add staff web ui`, live since 0.3.0). It adds no
deployment, no service and no backend change.

## Why

Staff asked for the staff UI to follow Material Design 3. The UI that went live in 0.3.0 uses plain Tailwind tokens of its own, without
a design language staff recognise. A familiar, consistent look reduces the time a new moderator needs to read the screens, and one
set of design tokens (colors, type, shape, elevation, motion) is the same wherever the building blocks are used. The
maintainer decided that the UI is Material Design 3 and that the building blocks must stay reusable in other OneLiteFeather
projects, which is only possible when they depend on Tailwind CSS and Vue and on nothing that is specific to one UI kit.

## What Changes

- **Design tokens from one seed color.** A build script generates the Material color roles (primary, secondary, tertiary,
  error, surface containers, outline, inverse and scrim) from one seed color, `#3D5A80`, with
  `@material/material-color-utilities`. It writes CSS custom properties for the light and the dark scheme. The seed is set in
  one place. Tailwind CSS 4 exposes the roles as theme colors.
- **Dark mode** follows the system preference and the existing theme toggle of the UI.
- **Typography** uses the Material type scale (display, headline, title, body and label, each large, medium and small) as
  Tailwind utilities. Roboto Flex is self-hosted through `@fontsource-variable/roboto-flex`; no external font service is
  used, so the Content Security Policy stays `'self'`.
- **Shape, elevation, state and motion.** The shape scale, the elevation levels 0 to 5, the state layers (8 % hover, 10 %
  focus and pressed), the Material focus indicator and the motion tokens are defined once. Motion respects
  `prefers-reduced-motion`.
- **Icons** are Material Symbols, self-hosted as SVG through an icon component. Decorative icons are hidden from assistive
  technology.
- **Components** are generic and hold no page logic: buttons (filled, tonal, outlined, text, icon), a segmented button, text
  fields and selects with supporting and error text, checkbox, radio, switch, chips, cards, a basic dialog, a data table or
  list, a top app bar, a navigation rail or bar, a snackbar, progress indicators and a menu. The building blocks of
  `add-admin-web` keep their names and their rules, and they are restyled rather than replaced.
- **Layout** uses the Material window size classes: compact below 600 px, medium from 600 px to 839 px, expanded from 840 px.
  The navigation rail is shown from 840 px; below it a navigation bar or a modal drawer is used.
- **Charts** take their colors from the color roles.

## Unchanged

Behaviour, routes, the browser-facing API contract, the German texts, and the accessibility requirements of `add-admin-web`
(contrast of at least 4.5:1 for text, keyboard operation, focus trap in dialogs, live region for status messages) stay as they
are. The existing tests keep passing. The change adds no page and no route.

## Out of Scope

The backend, the Nitro server logic (login, sessions, forwarding, headers), new pages and new staff functions. The move of the
building blocks into a shared package stays a later change, as `add-admin-web` already states.

## Impact

- Affected code: `web/` only (styles, tokens, components, pages' markup and the chart components).
- Dependencies: `tailwindcss` (4.x), `@material/material-color-utilities`, `@fontsource-variable/roboto-flex` and
  `@iconify-json/material-symbols` are added. `@nuxt/ui` is removed.
- Supersedes: design decision 14 of `add-admin-web` (Nuxt UI as the component library). Its other decisions stay.
