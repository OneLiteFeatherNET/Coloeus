// https://nuxt.com/docs/api/configuration/nuxt-config
//
// The staff UI is a Nuxt 4 application with `ssr: false`: the pages are a single-page application built to
// static files, and the Nitro server (preset `node-server`) serves those files under `/admin/` and runs the
// backend-for-frontend routes under `/admin/auth` and `/admin/api`. The session is an HttpOnly cookie; the
// browser never holds a token. Settings come from the environment at run time (see server/lib/config.ts).
import tailwindcss from '@tailwindcss/vite'
import { contentSecurityPolicy } from './shared/csp'

const securityHeaders = {
  // The server adds the hash of the shell's inline script to script-src (server/plugins/csp.ts).
  'Content-Security-Policy': contentSecurityPolicy(),
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'same-origin'
}

export default defineNuxtConfig({
  compatibilityDate: '2026-08-09',
  devtools: { enabled: false },
  ssr: false,
  modules: ['@vueuse/nuxt', '@nuxt/eslint'],
  // The self-hosted Roboto Flex is bundled into _nuxt/ (no external font request; font-src 'self'). The generated
  // theme (md3-theme.css) defines the color roles that main.css maps to Tailwind utilities.
  css: ['@fontsource-variable/roboto-flex/index.css', '~/assets/css/md3-theme.css', '~/assets/css/main.css'],
  app: {
    baseURL: '/admin/',
    buildAssetsDir: '_nuxt/',
    head: {
      htmlAttrs: { lang: 'de' },
      titleTemplate: '%s · Coloeus'
    }
  },
  nitro: {
    preset: 'node-server'
  },
  experimental: {
    // Without this Nuxt emits an inline import map, which the Content-Security-Policy (script-src 'self') refuses.
    // The bundles then import the entry chunk by its real file name, so the page has no inline script.
    entryImportMap: false
  },
  routeRules: {
    // The Nitro server is mounted under the base `/admin`, so route rules are written relative to that base.
    '/**': {
      headers: { ...securityHeaders, 'Cache-Control': 'no-store' }
    },
    '/_nuxt/**': {
      headers: { 'Cache-Control': 'public, max-age=31536000, immutable' }
    }
  },
  vite: {
    plugins: [tailwindcss()]
  },
  typescript: {
    strict: true
  }
})
