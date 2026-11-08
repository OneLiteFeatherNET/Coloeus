<script setup lang="ts">
import { computed } from 'vue'
import AppPageHeader from '~/components/ui/AppPageHeader.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import { de } from '~/i18n/de'
import { validReturnPath } from '#shared/utils/returnPath'

// Anmelden: starts the login with the server. The browser follows the redirect to Entra; a refused login comes back
// as ?error=login or ?error=forbidden, and an expired session as ?expired=1.
definePageMeta({ layout: 'bare' })
useHead({ title: de.titles.login })

const route = useRoute()

const returnTo = computed(() => validReturnPath(typeof route.query.returnTo === 'string' ? route.query.returnTo : null))

const failure = computed(() => {
  if (route.query.error === 'login') {
    return de.login.errorLogin
  }
  if (route.query.error === 'forbidden') {
    return de.login.errorForbidden
  }
  return ''
})

const notice = computed(() => route.query.expired === '1' && failure.value === '' ? de.login.expired : '')

const startHref = computed(() => `/admin/auth/login?returnTo=${encodeURIComponent(returnTo.value)}`)
</script>

<template>
  <div class="flex flex-col gap-6">
  <AppPageHeader :title="de.login.heading" :description="de.login.intro" />

  <AppStatus :message="failure" tone="error" />
  <AppStatus :message="notice" tone="info" />

  <a
    :href="startHref"
    class="inline-flex min-h-11 items-center justify-center rounded-lg bg-(--c-accent) px-4 py-2 font-medium text-(--c-accent-text)"
  >
    {{ de.login.start }}
  </a>
  </div>
</template>
