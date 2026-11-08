<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import { de } from '~/i18n/de'
import { useStaff } from '~/composables/useStaff'
import { errorMessage } from '~/utils/errors'
import { browserTimeZone, formatInstant } from '~/utils/time'

// The shell of every page after the login: skip link, navigation, the signed-in user and the status region. The
// navigation lists every page for every staff role; write controls on the pages say why they are disabled.
const staff = useStaff()
const error = ref('')
const loggingOut = ref(false)

const navigation = [
  { to: '/', label: de.nav.overview },
  { to: '/konten', label: de.nav.accounts },
  { to: '/clients', label: de.nav.clients },
  { to: '/waehrungen', label: de.nav.currencies },
  { to: '/protokoll', label: de.nav.log }
]

onMounted(async () => {
  try {
    await staff.ensureMe()
  } catch (failure) {
    error.value = errorMessage(failure)
  }
})

async function logout() {
  loggingOut.value = true
  try {
    await staff.logout()
  } catch (failure) {
    error.value = errorMessage(failure)
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="flex min-h-dvh flex-col">
    <a href="#main" class="sr-only-focusable rounded-lg bg-(--c-accent) px-4 py-2 text-(--c-accent-text)">
      {{ de.app.skipToContent }}
    </a>
    <header class="border-b border-(--c-border) bg-(--c-surface)">
      <div class="flex flex-wrap items-center justify-between gap-3 px-4 py-3 md:px-6">
        <NuxtLink to="/" class="text-lg font-bold">
          {{ de.app.name }}
        </NuxtLink>
        <div v-if="staff.me.value" class="flex flex-wrap items-center gap-3 text-sm">
          <span>
            {{ de.app.signedInAs }} <strong>{{ staff.me.value.name }}</strong>
          </span>
          <span class="text-(--c-muted)">
            {{ de.app.sessionUntil(formatInstant(staff.me.value.sessionExpiresAt, browserTimeZone())) }}
          </span>
          <AppButton variant="secondary" :disabled="loggingOut" @click="logout">
            {{ loggingOut ? de.app.loggingOut : de.app.logout }}
          </AppButton>
        </div>
      </div>
      <nav :aria-label="de.app.navigationLabel" class="flex flex-wrap gap-2 px-4 pb-3 md:px-6">
        <NuxtLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          class="inline-flex min-h-11 items-center rounded-lg px-3 font-medium underline-offset-4 hover:underline"
          active-class="bg-(--c-accent) text-(--c-accent-text)"
        >
          {{ item.label }}
        </NuxtLink>
      </nav>
    </header>
    <main id="main" tabindex="-1" class="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-4 py-6 md:px-6">
      <AppStatus :message="error" tone="error" />
      <AppStatus :message="staff.announcement.value" tone="success" />
      <slot />
    </main>
  </div>
</template>
