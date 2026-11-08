// The Content-Security-Policy of every answer under /admin/ (openspec staff-web-ui). Nuxt's SPA shell carries one inline
// script with the build settings; the server allows exactly that script by its SHA-256 hash (server/plugins/csp.ts)
// and no other inline script.

export function contentSecurityPolicy(scriptHashes: readonly string[] = []): string {
  const scriptSource = ['\'self\'', ...scriptHashes.map(hash => `'sha256-${hash}'`)].join(' ')
  return [
    'default-src \'self\'',
    `script-src ${scriptSource}`,
    'style-src \'self\' \'unsafe-inline\'',
    'img-src \'self\' data:',
    'connect-src \'self\'',
    'frame-ancestors \'none\'',
    'base-uri \'none\'',
    'form-action \'self\''
  ].join('; ')
}
