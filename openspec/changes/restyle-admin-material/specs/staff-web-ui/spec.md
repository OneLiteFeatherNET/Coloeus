# Spec Delta

## Purpose
The staff web interface follows Material Design 3: its colors come from one seed through generated tokens, its type, shape, elevation,
state, motion and icons come from the same tokens, and its building blocks are generic and reusable. Behaviour, routes, the browser-facing
API and the accessibility rules of `add-admin-web` do not change.

## ADDED Requirements

### Requirement: Color roles generated from one seed
The UI SHALL define its colors as Material color roles generated from the seed color `#3D5A80`. The seed SHALL be written in one module,
and the generated CSS custom properties SHALL be committed. The roles SHALL be exposed as Tailwind theme colors.

#### Scenario: Complete role set
- **WHEN** the generated file is read
- **THEN** it defines primary, on-primary, primary-container, on-primary-container, secondary, tertiary, error, surface, surface-dim,
  surface-bright, surface-container-lowest to surface-container-highest, on-surface, on-surface-variant, outline, outline-variant,
  inverse-surface, inverse-on-surface, inverse-primary and scrim, for both schemes

#### Scenario: Seed changed in one place
- **GIVEN** the seed module contains `#3D5A80`
- **WHEN** the seed is changed to another color and the token script runs
- **THEN** every role in the generated file changes and no other file holds a color value of the palette

#### Scenario: Stale generated file
- **GIVEN** the committed token file differs from the output of the script for the current seed
- **WHEN** the test suite runs
- **THEN** the test fails and names the file to regenerate

### Requirement: Light and dark schemes
The UI SHALL provide a light and a dark scheme. The default SHALL be the system preference; the theme toggle SHALL set the scheme to light or
dark, or back to the system preference. The choice SHALL be stored in the browser, and a failing storage access SHALL NOT break the page.

#### Scenario: System preference dark
- **GIVEN** no stored choice and a system preference for dark
- **WHEN** a page is shown
- **THEN** the dark roles are applied before the first render

#### Scenario: Storage unavailable
- **GIVEN** the browser refuses access to storage
- **WHEN** the user opens the UI
- **THEN** the page follows the system preference and the toggle still changes the scheme for the current page

### Requirement: Text contrast in both schemes
Every text color pair of the tokens SHALL reach a contrast of at least 4.5:1 against its background in both schemes, including the pairs
`on-surface`/`surface`, `on-primary`/`primary`, `on-surface-variant`/`surface-container-highest` and `on-error`/`error`.

#### Scenario: Pair below the limit
- **WHEN** the token test computes the contrast of a text pair in either scheme
- **THEN** a pair below 4.5:1 fails the test and names the pair and the scheme

### Requirement: Material type scale
The UI SHALL use the Material type scale (display, headline, title, body and label, each in large, medium and small) through Tailwind utilities
that set size, line height, weight, tracking and font family. The UI SHALL use Roboto Flex, self-hosted from the UI's own origin, and SHALL
request no font from an external origin.

#### Scenario: Font from another origin
- **WHEN** a page loads
- **THEN** every font file is requested from `/admin/` and no request goes to an external origin

#### Scenario: Type utility applied
- **GIVEN** a heading that uses the headline large utility
- **WHEN** the page renders
- **THEN** its size, line height and weight are those of the headline large token

### Requirement: Shape, elevation, state layers and focus indicator
The UI SHALL define the shape scale none, extra-small (4 px), small (8 px), medium (12 px), large (16 px), extra-large (28 px) and full, and the
elevation levels 0 to 5 as a tonal surface container plus a shadow. Interactive elements SHALL show state layers of the content color at 8 %
on hover, 10 % on focus and 10 % when pressed. A focused element SHALL show the Material focus indicator on `:focus-visible`, and the
indicator SHALL NOT be removed.

#### Scenario: Hover state layer
- **WHEN** a pointer rests on a filled button
- **THEN** a layer of the content color at 8 % opacity is shown over the button

#### Scenario: Keyboard focus
- **WHEN** a button receives focus from the keyboard
- **THEN** the focus indicator is visible with a contrast of at least 3:1 against the adjacent color

### Requirement: Motion tokens and reduced motion
The UI SHALL define motion tokens (standard, emphasized and standard-decelerate easings and short, medium and long durations). When the user
prefers reduced motion, every duration token SHALL be 0 ms, and no information SHALL depend on an animation.

#### Scenario: Reduced motion preferred
- **GIVEN** the system prefers reduced motion
- **WHEN** a dialog or the snackbar opens
- **THEN** it appears without a transition and its content is complete at once

### Requirement: Material Symbols as self-hosted icons
Icons SHALL be Material Symbols, rendered as inline SVG from the installed icon set. A decorative icon SHALL be hidden from assistive technology
with `aria-hidden="true"`. A control whose only content is an icon SHALL have an accessible name from the control.

#### Scenario: Decorative icon
- **WHEN** an icon is shown next to a visible text label
- **THEN** the icon carries `aria-hidden="true"` and the label alone is announced

#### Scenario: Icon-only control
- **WHEN** a button shows only an icon
- **THEN** the button has an `aria-label` with its German name and the icon is hidden from assistive technology

### Requirement: Material building block set
The UI SHALL provide the Material building blocks in `web/app/components/ui/`, and each block SHALL take props, slots and events only.
A block SHALL NOT import from `pages/`, `features/`, `api/` or `server/`.

#### Scenario: Block set complete
- **WHEN** the folder `web/app/components/ui/` is listed
- **THEN** it contains buttons (filled, tonal, outlined, text and icon), a segmented button, an outlined text field, a select, checkbox, radio,
  switch, chips, cards (elevated, filled and outlined), a basic dialog, a data table or list, a top app bar, a navigation rail, a navigation bar,
  a modal drawer, a snackbar, linear and circular progress and a menu

#### Scenario: Segmented button for the window
- **GIVEN** the money flow page shows the window toggle
- **WHEN** the page renders
- **THEN** the toggle is a segmented button with the choices "7 Tage" and "Gesamt" and exactly one choice is selected

#### Scenario: Block without page logic
- **WHEN** a building block is reviewed
- **THEN** it contains no route, no API resource, no staff role and no German sentence of its own

### Requirement: Material window size classes
The UI SHALL classify the viewport width as compact (below 600 px), medium (600 px to 839 px) or expanded (840 px and above). At expanded the
navigation SHALL be a navigation rail; at compact and medium it SHALL be a navigation bar, and the menu at compact SHALL be a modal drawer.
The class SHALL follow a resize of the viewport.

#### Scenario: Expanded width
- **GIVEN** a viewport of 1280 px
- **WHEN** the layout renders
- **THEN** the navigation is a rail and the window class is expanded

#### Scenario: Resize to compact
- **GIVEN** a viewport of 840 px showing the rail
- **WHEN** the viewport is narrowed to 360 px
- **THEN** the navigation becomes a navigation bar and the window class is compact

### Requirement: Snackbar and live region
A success message SHALL be shown in a snackbar and SHALL be announced once through the existing `aria-live="polite"` status region. The snackbar
SHALL NOT be a second live region. The snackbar SHALL stay open while it has focus and SHALL close after its timeout otherwise.

#### Scenario: Success announced once
- **WHEN** a write succeeds
- **THEN** the success message is announced once through the status region and the snackbar shows the same text

#### Scenario: Snackbar keeps focus
- **GIVEN** a snackbar with keyboard focus
- **WHEN** its timeout elapses
- **THEN** the snackbar stays open until focus leaves it

### Requirement: Chart colors from the roles
The dashboard charts SHALL take their colors from the color roles: created amounts in `primary`, destroyed amounts in `tertiary` and axes and grid
in `outline`. Colors SHALL NOT be the only way to tell created from destroyed; the legend and the data table alternative SHALL keep the labels.

#### Scenario: Created and destroyed distinguishable
- **WHEN** a chart shows created and destroyed amounts in a color-blind simulation
- **THEN** the series are still told apart by their labels in the legend and the table

### Requirement: Behaviour, routes and contract unchanged
The restyling SHALL NOT change any route, any German text, any API call or any request header of the UI, and SHALL add no page. The existing
tests of the UI SHALL keep passing with their assertions unchanged.

#### Scenario: Route list unchanged
- **WHEN** the route table of the UI is compared with the one of `add-admin-web`
- **THEN** the two tables are identical

#### Scenario: Existing tests
- **WHEN** the test suite runs after the restyling
- **THEN** every test that passed before passes, and no assertion of an existing test was changed to make it pass
