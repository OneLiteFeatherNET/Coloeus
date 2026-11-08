// @ts-check
import vuejsAccessibility from 'eslint-plugin-vuejs-accessibility'
import withNuxt from './.nuxt/eslint.config.mjs'

// Accessibility rules of eslint-plugin-vuejs-accessibility are errors, so the lint step fails on a
// form control without a label, a click handler without a key handler, and the like.
const a11yConfigs = vuejsAccessibility.configs['flat/recommended'].map(config => ({
  ...config,
  files: ['**/*.vue'],
  rules: {
    ...config.rules,
    // Labels that wrap their control or point at it with `for`/`id` are both valid.
    'vuejs-accessibility/label-has-for': [
      'error',
      { required: { some: ['nesting', 'id'] }, allowChildren: false }
    ]
  }
}))

export default withNuxt(...a11yConfigs, {
  rules: {
    'linebreak-style': ['error', 'unix'],
    'no-trailing-spaces': 'error'
  }
}, {
  // The building blocks of components/ui are movable into a shared package: they import nothing from the application.
  files: ['app/components/ui/**/*.{vue,ts}'],
  rules: {
    'no-restricted-imports': ['error', {
      patterns: [{
        group: ['~/pages/**', '~/features/**', '~/api/**', '~/server/**', '**/pages/**', '**/features/**', '**/server/**'],
        message: 'Building blocks must not import from pages, features, api or server.'
      }]
    }]
  }
})
