import { describe, expect, it } from 'vitest'
import { canWrite, staffRoles } from '../../../shared/me'
import { validReturnPath } from '../../../shared/utils/returnPath'

describe('staffRoles', () => {
  it('keeps only the two staff roles, in their fixed order', () => {
    expect(staffRoles(['Coloeus.Admin', 'Other.Role', 'Coloeus.Moderator'])).toEqual(['Coloeus.Moderator', 'Coloeus.Admin'])
  })

  it('returns no role for a user without staff roles', () => {
    expect(staffRoles(['Other.Role'])).toEqual([])
  })
})

describe('canWrite', () => {
  it('is true only with the admin role', () => {
    expect(canWrite(['Coloeus.Admin'])).toBe(true)
    expect(canWrite(['Coloeus.Moderator'])).toBe(false)
  })
})

describe('validReturnPath', () => {
  it('keeps a path under /admin/', () => {
    expect(validReturnPath('/admin/konten/5f0c?tab=history')).toBe('/admin/konten/5f0c?tab=history')
  })

  it('replaces a protocol-relative path', () => {
    expect(validReturnPath('//example.org/admin/')).toBe('/admin/')
  })

  it('replaces an absolute URL', () => {
    expect(validReturnPath('https://example.org/admin/')).toBe('/admin/')
  })

  it('replaces a path outside the admin area', () => {
    expect(validReturnPath('/konten')).toBe('/admin/')
  })

  it('replaces a path with a backslash, which some browsers read as a host separator', () => {
    expect(validReturnPath('/admin/\\\\example.org')).toBe('/admin/')
  })

  it('replaces a path longer than 512 characters', () => {
    expect(validReturnPath(`/admin/${'a'.repeat(512)}`)).toBe('/admin/')
  })

  it('replaces a missing value', () => {
    expect(validReturnPath(null)).toBe('/admin/')
  })
})
