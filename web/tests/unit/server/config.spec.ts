import { describe, expect, it } from 'vitest'
import { ConfigError, loadConfig, REQUIRED_VARIABLES } from '../../../server/lib/config'

const complete = {
  COLOEUS_WEB_CLIENT_ID: 'client',
  COLOEUS_WEB_CLIENT_SECRET: 'secret',
  COLOEUS_WEB_BASE_URL: 'https://coloeus.example/',
  COLOEUS_WEB_SESSION_SECRET: 'a'.repeat(32),
  COLOEUS_WEB_STORAGE_URL: 'redis://dragonfly:6379',
  COLOEUS_TENANT_ID: 'tenant',
  COLOEUS_AUDIENCE: 'audience',
  COLOEUS_BACKEND_URL: 'https://backend.example/'
}

function problemsOf(env: Record<string, string | undefined>): string[] {
  try {
    loadConfig(env)
  } catch (error) {
    if (error instanceof ConfigError) {
      return error.problems
    }
    throw error
  }
  return []
}

describe('loadConfig', () => {
  it('reads a complete environment and strips trailing slashes from the URLs', () => {
    const config = loadConfig({ ...complete, NODE_ENV: 'production' })

    expect(config.baseUrl).toBe('https://coloeus.example')
    expect(config.origin).toBe('https://coloeus.example')
    expect(config.backendUrl).toBe('https://backend.example')
  })

  it('names every missing variable in production instead of the first one only', () => {
    const problems = problemsOf({ NODE_ENV: 'production' })

    for (const name of REQUIRED_VARIABLES) {
      expect(problems, `missing ${name}`).toContain(`${name} is missing`)
    }
  })

  it('refuses a session secret shorter than 32 bytes and names it', () => {
    const problems = problemsOf({ ...complete, COLOEUS_WEB_SESSION_SECRET: 'too-short' })

    expect(problems).toEqual(['COLOEUS_WEB_SESSION_SECRET is shorter than 32 bytes'])
  })

  it('counts the session secret in bytes, not in characters', () => {
    const problems = problemsOf({ ...complete, COLOEUS_WEB_SESSION_SECRET: 'ä'.repeat(15) })

    expect(problems).toEqual(['COLOEUS_WEB_SESSION_SECRET is shorter than 32 bytes'])
  })

  it('fills in the backend URL and the local Dragonfly outside production only', () => {
    const { COLOEUS_BACKEND_URL: _backend, COLOEUS_WEB_STORAGE_URL: _storage, ...rest } = complete

    const development = loadConfig({ ...rest, NODE_ENV: 'development' })

    expect(development.backendUrl).toBe('http://localhost:8080')
    expect(problemsOf({ ...rest, NODE_ENV: 'production' })).toEqual(expect.arrayContaining([
      'COLOEUS_BACKEND_URL is missing',
      'COLOEUS_WEB_STORAGE_URL is missing'
    ]))
  })

  it('never defaults the web client settings, even in development', () => {
    const problems = problemsOf({ NODE_ENV: 'development' })

    expect(problems).toContain('COLOEUS_WEB_CLIENT_ID is missing')
    expect(problems).toContain('COLOEUS_WEB_SESSION_SECRET is missing')
  })

  it('refuses a base URL that is not absolute', () => {
    const problems = problemsOf({ ...complete, COLOEUS_WEB_BASE_URL: 'coloeus.example' })

    expect(problems).toEqual(['COLOEUS_WEB_BASE_URL is not an absolute URL'])
  })
})
