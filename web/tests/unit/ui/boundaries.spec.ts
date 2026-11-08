import { readdirSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

// The building blocks take their texts as props or slots and import nothing from the application (openspec
// staff-web-ui, "Reusable building blocks"). These checks read the source files of ui/ as data.
const uiDirectory = new URL('../../../app/components/ui/', import.meta.url)
const blocks = readdirSync(uiDirectory).filter(name => name.endsWith('.vue'))

describe('building blocks', () => {
  it('include a button, a dialog, a table, a form field, a money display, a status message, an empty state and a page header', () => {
    expect(blocks).toEqual(expect.arrayContaining([
      'AppButton.vue', 'AppDialog.vue', 'AppTable.vue', 'AppFormField.vue',
      'AppMoney.vue', 'AppStatus.vue', 'AppEmptyState.vue', 'AppPageHeader.vue'
    ]))
  })

  for (const block of blocks) {
    const source = readFileSync(new URL(block, uiDirectory), 'utf8')

    it(`${block} imports nothing from pages, features, api or server`, () => {
      expect(source).not.toMatch(/from\s+['"](~|@|\.\.?)\/[^'"]*(pages|features|api|server)\//)
    })

    it(`${block} holds no German text of its own`, () => {
      expect(source).not.toMatch(/[äöüÄÖÜß]/)
    })
  }
})
