import { describe, expect, it } from 'vitest'
import { ENDPOINTS, matchEndpoint } from '../../../server/lib/endpoints'
import { contentSecurityPolicy } from '../../../shared/csp'

describe('matchEndpoint', () => {
  it('matches a static path with its method', () => {
    expect(matchEndpoint('GET', ['money-flow'])?.access).toBe('read')
  })

  it('matches a parameter segment of any value', () => {
    expect(matchEndpoint('PUT', ['currencies', 'GEMS', 'default'])?.access).toBe('admin')
  })

  it('prefers the search endpoint over the account parameter route', () => {
    expect(matchEndpoint('GET', ['accounts', 'search'])?.pattern).toEqual(['accounts', 'search'])
  })

  it('refuses a method that the path does not have', () => {
    expect(matchEndpoint('DELETE', ['clients'])).toBeNull()
  })

  it('refuses an empty parameter segment', () => {
    expect(matchEndpoint('GET', ['accounts', ''])).toBeNull()
  })

  it('refuses a path with extra segments', () => {
    expect(matchEndpoint('GET', ['accounts', 'a', 'postings', 'extra'])).toBeNull()
  })

  it('gives reads the read role and writes the admin role', () => {
    for (const endpoint of ENDPOINTS) {
      expect(endpoint.access, endpoint.pattern.join('/')).toBe(endpoint.method === 'GET' || endpoint.local ? 'read' : 'admin')
    }
  })

  it('lists the fifteen rows of the contract, of which only me is answered locally', () => {
    expect(ENDPOINTS).toHaveLength(15)
    expect(ENDPOINTS.filter(endpoint => endpoint.local).map(endpoint => endpoint.pattern.join('/'))).toEqual(['me'])
  })
})

describe('contentSecurityPolicy', () => {
  it('allows scripts from the origin only when no hash is given', () => {
    expect(contentSecurityPolicy()).toContain('script-src \'self\';')
  })

  it('adds the hashes of the page scripts and nothing else', () => {
    const policy = contentSecurityPolicy(['abc='])

    expect(policy).toContain('script-src \'self\' \'sha256-abc=\';')
    expect(policy).not.toMatch(/script-src[^;]*unsafe-inline/)
    expect(policy).toContain('frame-ancestors \'none\'')
  })
})
