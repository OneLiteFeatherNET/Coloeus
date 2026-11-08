// Settings of the Nitro server. Only environment variables are read. Production has no defaults; the
// development configuration fills in the backend URL and a local Dragonfly, and nothing else.

export interface WebConfig {
  clientId: string
  clientSecret: string
  baseUrl: string
  /** The origin of `baseUrl`; the Origin header of a write must equal it. */
  origin: string
  sessionSecret: string
  storageUrl: string
  tenantId: string
  audience: string
  backendUrl: string
}

export const REQUIRED_VARIABLES = [
  'COLOEUS_WEB_CLIENT_ID',
  'COLOEUS_WEB_CLIENT_SECRET',
  'COLOEUS_WEB_BASE_URL',
  'COLOEUS_WEB_SESSION_SECRET',
  'COLOEUS_WEB_STORAGE_URL',
  'COLOEUS_TENANT_ID',
  'COLOEUS_AUDIENCE',
  'COLOEUS_BACKEND_URL'
] as const

export const DEVELOPMENT_DEFAULTS: Partial<Record<(typeof REQUIRED_VARIABLES)[number], string>> = {
  COLOEUS_BACKEND_URL: 'http://localhost:8080',
  COLOEUS_WEB_STORAGE_URL: 'redis://localhost:6379'
}

export const MIN_SESSION_SECRET_BYTES = 32

export class ConfigError extends Error {
  readonly problems: string[]

  constructor(problems: string[]) {
    super(`Invalid settings: ${problems.join('; ')}`)
    this.name = 'ConfigError'
    this.problems = problems
  }
}

type Environment = Record<string, string | undefined>

/**
 * Reads the settings from `env`. Throws a ConfigError that names every missing or invalid variable, so that the
 * server refuses to start with a complete list instead of the first problem only.
 */
export function loadConfig(env: Environment): WebConfig {
  const isProduction = env.NODE_ENV === 'production'
  const problems: string[] = []
  const value = (name: (typeof REQUIRED_VARIABLES)[number]): string => {
    const raw = env[name]?.trim()
    if (raw) {
      return raw
    }
    if (!isProduction && DEVELOPMENT_DEFAULTS[name]) {
      return DEVELOPMENT_DEFAULTS[name]
    }
    problems.push(`${name} is missing`)
    return ''
  }

  const clientId = value('COLOEUS_WEB_CLIENT_ID')
  const clientSecret = value('COLOEUS_WEB_CLIENT_SECRET')
  const baseUrl = value('COLOEUS_WEB_BASE_URL').replace(/\/+$/, '')
  const sessionSecret = env.COLOEUS_WEB_SESSION_SECRET ?? ''
  if (!env.COLOEUS_WEB_SESSION_SECRET) {
    problems.push('COLOEUS_WEB_SESSION_SECRET is missing')
  } else if (Buffer.byteLength(sessionSecret, 'utf8') < MIN_SESSION_SECRET_BYTES) {
    problems.push(`COLOEUS_WEB_SESSION_SECRET is shorter than ${MIN_SESSION_SECRET_BYTES} bytes`)
  }
  const storageUrl = value('COLOEUS_WEB_STORAGE_URL')
  const tenantId = value('COLOEUS_TENANT_ID')
  const audience = value('COLOEUS_AUDIENCE')
  const backendUrl = value('COLOEUS_BACKEND_URL').replace(/\/+$/, '')

  let origin = ''
  if (baseUrl) {
    try {
      origin = new URL(baseUrl).origin
    } catch {
      problems.push('COLOEUS_WEB_BASE_URL is not an absolute URL')
    }
  }
  if (problems.length > 0) {
    throw new ConfigError(problems)
  }
  return {
    clientId,
    clientSecret,
    baseUrl,
    origin,
    sessionSecret,
    storageUrl,
    tenantId,
    audience,
    backendUrl
  }
}
