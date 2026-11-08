# Tasks

The code pull request `feat(admin): adopt material design 3 in the staff ui` opens only after `docs(openspec): propose restyle-admin-material`
and `feat(admin): add staff web ui` are merged to `main`. Checkboxes are ticked in a follow-up pull request, never in the code pull request.

## 1. Tokens

- [ ] 1.1 Add `@material/material-color-utilities` and a script `web/scripts/generate-tokens.ts` that reads the seed `#3D5A80` from `web/design/seed.ts`.
- [ ] 1.2 The script writes `web/app/assets/css/tokens.generated.css` with the color roles for light, dark by attribute and dark by system preference; add `pnpm tokens`, run before `build`.
- [ ] 1.3 Test: the generated roles are the same for the same seed; a regenerated file equals the committed file.
- [ ] 1.4 Test: the pairs `on-surface`/`surface`, `on-primary`/`primary`, `on-surface-variant`/`surface-container-highest` and `on-error`/`error` reach 4.5:1 in both schemes.

## 2. Tailwind theme, shape, elevation, state and motion

- [ ] 2.1 `web/app/assets/css/theme.css`: Tailwind 4 `@theme` entries that map every color role to its custom property.
- [ ] 2.2 Shape scale (none to full), elevation levels 0 to 5, state layers (8 %, 10 %, 10 %), and the focus indicator as utilities.
- [ ] 2.3 Motion tokens (standard, emphasized, standard-decelerate; durations) and the `prefers-reduced-motion` rule that sets durations to 0 ms.
- [ ] 2.4 Test: the reduced-motion rule is present in the built CSS and the durations are 0 ms under it.

## 3. Typography and font

- [ ] 3.1 Add `@fontsource-variable/roboto-flex` with the Latin and Latin-extended subsets; import it in `theme.css`.
- [ ] 3.2 Declare the MD3 type scale (display, headline, title, body, label; large, medium, small) as Tailwind utilities.
- [ ] 3.3 Check that the font files are served from `/admin/_nuxt/` and that no external origin is requested (CSP stays `'self'`).

## 4. Icons

- [ ] 4.1 Add `@iconify-json/material-symbols`; implement `AppIcon` rendering the named symbol as inline SVG, emitting only the symbols in use.
- [ ] 4.2 Test: a decorative icon renders `aria-hidden="true"`; an icon-only control has an accessible name from the control.

## 5. Building blocks: buttons, selection and fields

- [ ] 5.1 Restyle `AppButton` (filled, tonal, outlined, text, icon variants) with the state layer and focus indicator.
- [ ] 5.2 Add `AppSegmentedButton` for the window toggle "7 Tage" / "Gesamt" (single selection, keyboard arrows, `aria-pressed` or radio semantics).
- [ ] 5.3 Restyle `AppFormField` as the outlined text field with supporting and error text linked by `aria-describedby`; add `AppSelect`.
- [ ] 5.4 Add `AppCheckbox`, `AppRadio` and `AppSwitch` with labels and the MD3 states.
- [ ] 5.5 Test each new block: label, error text linked, disabled state, keyboard operation.

## 6. Building blocks: surfaces, feedback and navigation

- [ ] 6.1 Add `AppChip` and `AppCard` (elevated, filled, outlined).
- [ ] 6.2 Restyle `AppDialog` to the MD3 basic dialog; keep the focus trap, Escape and focus return; add a test for each.
- [ ] 6.3 Restyle `AppTable` as a data table from `md` and a stacked list below it; keep captions and `scope`.
- [ ] 6.4 Add `AppProgress` (linear and circular) and `AppMenu`.
- [ ] 6.5 Add `AppSnackbar`; success messages go through the `AppStatus` live region; test that the message is announced once.
- [ ] 6.6 Add `AppTopAppBar`, `AppNavigation` (rail, bar, modal drawer) and `useWindowSize` (600 and 840 px); test the three classes with a stubbed media query.

## 7. Dark mode

- [ ] 7.1 Apply the stored choice (system, light, dark) in the client plugin before first render; keep the choice in `localStorage` inside `try`/`catch`.
- [ ] 7.2 Test: with storage unavailable the page follows the system preference; the toggle sets `data-theme` and `system` removes it.

## 8. Page migration

- [ ] 8.1 Migrate the layout (top app bar, navigation, skip link, landmarks) to the new blocks.
- [ ] 8.2 Migrate the money flow page: window toggle, charts in the color roles, legend and table alternative.
- [ ] 8.3 Migrate the account search and account detail pages.
- [ ] 8.4 Migrate the reset and adjust dialogs, the client, currency and log pages.
- [ ] 8.5 Remove `@nuxt/ui` and its configuration once no page imports it.

## 9. Verification

- [ ] 9.1 Run `pnpm lint typecheck test build`; the existing tests keep passing without change of their assertions.
- [ ] 9.2 Visual check at 360 px, 840 px and 1280 px in light and dark: no horizontal scroll at 360 px, navigation shape per size class, contrast and focus visible.
- [ ] 9.3 Keyboard pass over every page: skip link, focus trap in dialogs, focus return, segmented button arrows.

## 10. Code pull request

- [ ] 10.1 Open the code pull request `feat(admin): adopt material design 3 in the staff ui` from the implementation only: no file under `openspec/`, branched from `main` after this proposal and `feat(admin): add staff web ui` are merged. The description follows the repository's pull request template and names the decisions of `design.md`.
