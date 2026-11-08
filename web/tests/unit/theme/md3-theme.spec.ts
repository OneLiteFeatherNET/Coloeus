import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { contrastRatio, generateThemeCss, ROLES, rolesFor, SEED_COLOR } from '../../../scripts/generate-theme'

// The Material Design 3 roles come from one seed color (openspec add-admin-web, staff UI restyle). These tests read the
// generator and the committed stylesheets as data; they run without a browser, a network or the system clock.
const committedTheme = readFileSync(new URL('../../../app/assets/css/md3-theme.css', import.meta.url), 'utf8')
const mainCss = readFileSync(new URL('../../../app/assets/css/main.css', import.meta.url), 'utf8')
const HEX = /^#[0-9a-f]{6}$/

describe('Material Design 3 theme generation', () => {
  it('gives every color role a hex value for the light theme', () => {
    const light = rolesFor(SEED_COLOR, 'light')

    expect(Object.keys(light)).toHaveLength(ROLES.length)
    expect(Object.values(light).every(value => HEX.test(value))).toBe(true)
  })

  it('gives every color role a hex value for the dark theme', () => {
    const dark = rolesFor(SEED_COLOR, 'dark')

    expect(Object.keys(dark)).toHaveLength(ROLES.length)
    expect(Object.values(dark).every(value => HEX.test(value))).toBe(true)
  })

  it('names the roles the staff UI needs', () => {
    const names = ROLES.map(([name]) => name)

    expect(names).toEqual(expect.arrayContaining([
      'primary', 'on-primary', 'primary-container', 'on-primary-container',
      'secondary', 'tertiary', 'error', 'surface', 'surface-dim', 'surface-bright',
      'surface-container-lowest', 'surface-container-low', 'surface-container', 'surface-container-high',
      'surface-container-highest', 'on-surface', 'on-surface-variant', 'outline', 'outline-variant',
      'inverse-surface', 'inverse-on-surface', 'inverse-primary', 'scrim', 'shadow'
    ]))
  })

  it('produces the same stylesheet for the same seed', () => {
    expect(generateThemeCss(SEED_COLOR)).toBe(generateThemeCss(SEED_COLOR))
  })

  it('keeps the committed stylesheet in step with the generator', () => {
    expect(committedTheme).toBe(generateThemeCss(SEED_COLOR))
  })

  it('derives a dark surface for the dark theme and a light one for the light theme', () => {
    const light = rolesFor(SEED_COLOR, 'light')
    const dark = rolesFor(SEED_COLOR, 'dark')

    expect(contrastRatio(dark['surface'] as string, '#ffffff'))
      .toBeGreaterThan(contrastRatio(light['surface'] as string, '#ffffff'))
  })

  it('exposes every role as a Tailwind color in main.css', () => {
    const missing = ROLES
      .map(([name]) => name)
      .filter(name => !mainCss.includes(`--color-${name}: var(--md-sys-color-${name});`))

    expect(missing).toEqual([])
  })
})

describe('Text contrast of the generated roles', () => {
  const textPairs: Array<[text: string, background: string]> = [
    ['on-surface', 'surface'],
    ['on-surface', 'surface-container-highest'],
    ['on-surface-variant', 'surface'],
    ['on-surface-variant', 'surface-container-high'],
    ['on-surface-variant', 'surface-container-low'],
    ['primary', 'surface'],
    ['error', 'surface'],
    ['on-primary', 'primary'],
    ['on-primary-container', 'primary-container'],
    ['on-secondary-container', 'secondary-container'],
    ['on-tertiary-container', 'tertiary-container'],
    ['on-error', 'error'],
    ['on-error-container', 'error-container'],
    ['inverse-on-surface', 'inverse-surface']
  ]

  for (const mode of ['light', 'dark'] as const) {
    it(`reaches 4.5 to 1 for every text pair in the ${mode} theme`, () => {
      const roles = rolesFor(SEED_COLOR, mode)
      const failing = textPairs
        .filter(([text, background]) => contrastRatio(roles[text] as string, roles[background] as string) < 4.5)
        .map(([text, background]) => `${text} on ${background}`)

      expect(failing).toEqual([])
    })
  }
})
