<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppIcon from '~/components/ui/AppIcon.vue'
import AppNavigation from '~/components/ui/AppNavigation.vue'
import AppSnackbar from '~/components/ui/AppSnackbar.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import AppTopAppBar from '~/components/ui/AppTopAppBar.vue'
import type { MaterialSymbolName } from '~/assets/icons/material-symbols'
import { de } from '~/i18n/de'
import { useStaff } from '~/composables/useStaff'
import { errorMessage } from '~/utils/errors'
import { browserTimeZone, formatInstant } from '~/utils/time'

// The shell of every page after the login: skip link, top app bar with the signed-in user, the navigation (a bar below
// the `lg` window size, a rail from there on) and the snackbar for success messages. The navigation lists every page
// for every staff role; write controls on the pages say why they are disabled.
const route = useRoute()
const staff = useStaff()
const error = ref('')
const loggingOut = ref(false)

const navigation: Array<{ key: string, to: string, label: string, icon: MaterialSymbolName }> = [
  { key: 'overview', to: '/', label: de.nav.overview, icon: 'dashboard' },
  { key: 'accounts', to: '/konten', label: de.nav.accounts, icon: 'search' },
  { key: 'clients', to: '/clients', label: de.nav.clients, icon: 'groups' },
  { key: 'currencies', to: '/waehrungen', label: de.nav.currencies, icon: 'currency-exchange' },
  { key: 'log', to: '/protokoll', label: de.nav.log, icon: 'history' }
]

const items = computed(() => navigation.map(item => ({
  ...item,
  active: item.to === '/' ? route.path === '/' : route.path.startsWith(item.to)
})))

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
  <div class="flex min-h-dvh flex-col bg-surface text-on-surface">
    <a href="#main" class="sr-only-focusable rounded-md bg-primary px-4 py-2 text-on-primary">
      {{ de.app.skipToContent }}
    </a>
    <AppTopAppBar>
      <NuxtLink to="/" class="text-title-large text-on-surface">
        {{ de.app.name }}
      </NuxtLink>
      <template #actions>
        <template v-if="staff.me.value">
          <span>
            {{ de.app.signedInAs }} <strong>{{ staff.me.value.name }}</strong>
          </span>
          <span class="text-body-small text-on-surface-variant">
            {{ de.app.sessionUntil(formatInstant(staff.me.value.sessionExpiresAt, browserTimeZone())) }}
          </span>
          <AppButton variant="outlined" :disabled="loggingOut" @click="logout">
            {{ loggingOut ? de.app.loggingOut : de.app.logout }}
          </AppButton>
        </template>
      </template>
    </AppTopAppBar>
    <div class="flex flex-1 flex-col lg:flex-row">
      <AppNavigation :label="de.app.navigationLabel" :items="items">
        <template #default="{ item }">
          <NuxtLink
            :to="item.to"
            class="group flex flex-col items-center gap-1 py-1 text-label-medium text-on-surface"
            :aria-current="item.active ? 'page' : undefined"
          >
            <span
              class="md-state flex h-8 w-16 items-center justify-center rounded-full"
              :class="item.active ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface-variant'"
            >
              <AppIcon :name="item.icon" />
            </span>
            <span>{{ item.label }}</span>
          </NuxtLink>
        </template>
      </AppNavigation>
      <main id="main" tabindex="-1" class="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-4 py-6 pb-28 md:px-6 lg:pb-8">
        <AppStatus :message="error" tone="error" />
        <slot />
      </main>
    </div>
    <AppSnackbar
      :message="staff.announcement.value"
      :dismiss-label="de.app.dismissMessage"
      @dismiss="staff.announce('')"
    />
  </div>
</template>
