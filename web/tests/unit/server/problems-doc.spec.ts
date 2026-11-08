import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { csrfInvalid, forbiddenRole, internalError, notFound, requestInvalid, sessionRequired } from '../../../server/lib/problems'

// The catalogue of the repository (docs/problems.md) must state the status and the title that the BFF answers for
// each of its codes. The file is read as data; no network and no clock are involved.
const catalogue = readFileSync(new URL('../../../../docs/problems.md', import.meta.url), 'utf8')

function sectionOf(code: string): string {
  const start = catalogue.indexOf(`## ${code}\n`)
  if (start < 0) {
    return ''
  }
  const next = catalogue.indexOf('\n## ', start + 1)
  return catalogue.slice(start, next < 0 ? undefined : next)
}

describe('docs/problems.md', () => {
  const answers = [sessionRequired(), csrfInvalid(), notFound(), internalError(), forbiddenRole(), requestInvalid('detail')]

  for (const answer of answers) {
    const problem = JSON.parse(answer.body) as { code: string, status: number, title: string }

    it(`documents ${problem.code} with the status and title the BFF answers`, () => {
      const section = sectionOf(problem.code)

      expect(section, `section ${problem.code}`).not.toBe('')
      expect(section).toContain(`- Status: ${problem.status}`)
      expect(section).toContain(`- Title: ${problem.title}`)
    })
  }
})
