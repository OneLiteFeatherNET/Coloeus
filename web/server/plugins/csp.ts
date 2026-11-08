import { createHash } from 'node:crypto'
import { contentSecurityPolicy } from '../../shared/csp'

// Nuxt's SPA shell carries one inline script (the build settings of window.__NUXT__). The policy allows exactly the
// scripts of the page by their SHA-256 hash, so an inline script that is not the shell's own is still refused.
const INLINE_SCRIPT = /<script>([\s\S]*?)<\/script>/g

export default defineNitroPlugin((nitro) => {
  nitro.hooks.hook('render:response', (response) => {
    const hashes = [...response.body.matchAll(INLINE_SCRIPT)]
      .map(match => createHash('sha256').update(match[1] ?? '').digest('base64'))
    response.headers = {
      ...response.headers,
      'Content-Security-Policy': contentSecurityPolicy(hashes)
    }
  })
})
