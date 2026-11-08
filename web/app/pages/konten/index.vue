<script setup lang="ts">
import { computed, ref } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppEmptyState from '~/components/ui/AppEmptyState.vue'
import AppFormField from '~/components/ui/AppFormField.vue'
import AppPageHeader from '~/components/ui/AppPageHeader.vue'
import AppRadioGroup from '~/components/ui/AppRadioGroup.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import AppTable from '~/components/ui/AppTable.vue'
import { de } from '~/i18n/de'
import type { AccountHit } from '~/types/api'
import { useStaff } from '~/composables/useStaff'
import { errorMessage } from '~/utils/errors'
import { formatInstant, browserTimeZone } from '~/utils/time'
import { isPlayerName, isUuid } from '~/utils/validation'

// Konten: search for a player by name or by UUID. The name register answers; no Mojang call is made.
useHead({ title: de.titles.accounts })

const staff = useStaff()
const mode = ref<'name' | 'uuid'>('name')
const value = ref('')
const searched = ref<{ mode: 'name' | 'uuid', value: string } | null>(null)
const hits = ref<AccountHit[]>([])
const busy = ref(false)
const failure = ref('')

const problem = computed(() => {
  if (value.value === '') {
    return undefined
  }
  if (mode.value === 'name') {
    return isPlayerName(value.value) ? undefined : de.accounts.nameInvalid
  }
  return isUuid(value.value) ? undefined : de.accounts.uuidInvalid
})

const canSearch = computed(() => value.value !== '' && problem.value === undefined && !busy.value)

const columns = [
  { key: 'name', label: de.accounts.nameLabel },
  { key: 'uuid', label: de.accounts.uuidLabel },
  { key: 'lastSeen', label: de.accounts.lastSeen },
  { key: 'account', label: de.account.kindLabel }
]

const rows = computed(() => hits.value.map(hit => ({
  id: hit.uuid,
  name: hit.name ?? '',
  uuid: hit.uuid,
  lastSeen: hit.lastSeen ? formatInstant(hit.lastSeen, browserTimeZone()) : '',
  account: hit.accountId,
  accountId: hit.accountId
})))

const modeOptions = [
  { value: 'name', label: de.accounts.byName },
  { value: 'uuid', label: de.accounts.byUuid }
]

function setMode(next: string) {
  mode.value = next === 'uuid' ? 'uuid' : 'name'
  value.value = ''
}

async function search() {
  if (!canSearch.value) {
    return
  }
  busy.value = true
  failure.value = ''
  const query = mode.value === 'name' ? value.value : value.value.toLowerCase()
  try {
    const page = await staff.api.searchAccounts(mode.value === 'name' ? { name: query } : { uuid: query })
    hits.value = page.items
    searched.value = { mode: mode.value, value: query }
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
  <AppPageHeader :title="de.accounts.heading" :description="de.accounts.intro" />

  <form class="flex flex-col gap-4 rounded-md bg-surface-container-low p-4 shadow-elevation-1" novalidate @submit.prevent="search">
    <AppRadioGroup
      name="search-mode"
      :legend="`${de.accounts.byName} / ${de.accounts.byUuid}`"
      :model-value="mode"
      :options="modeOptions"
      @update:model-value="setMode"
    />

    <AppFormField
      v-slot="{ id, describedby, invalid }"
      :label="mode === 'name' ? de.accounts.byName : de.accounts.byUuid"
      :hint="mode === 'name' ? de.accounts.byNameHint : de.accounts.byUuidHint"
      :error="problem"
    >
      <input
        :id="id"
        v-model="value"
        type="text"
        autocomplete="off"
        :aria-invalid="invalid"
        :aria-describedby="describedby"
        class="md-field"
      >
    </AppFormField>

    <div>
      <AppButton type="submit" :disabled="!canSearch">
        {{ de.accounts.search }}
      </AppButton>
    </div>
  </form>

  <AppStatus :message="failure" tone="error" />

  <section v-if="searched" class="flex flex-col gap-3" aria-labelledby="search-results">
    <h2 id="search-results" class="text-title-large">
      {{ de.accounts.results }}
    </h2>
    <AppEmptyState v-if="hits.length === 0">
      <template v-if="searched.mode === 'name'">
        {{ de.accounts.noResults }} {{ de.accounts.tryUuid }}
      </template>
      <template v-else>
        {{ de.accounts.noResultsUuid }}
      </template>
    </AppEmptyState>
    <AppTable v-else :caption="de.accounts.results" :columns="columns" :rows="rows" row-key="id">
      <template #cell="{ row, column }">
        <template v-if="column === 'account'">
          <NuxtLink
            v-if="row.accountId"
            :to="`/konten/${row.accountId}`"
            class="inline-flex min-h-11 items-center font-medium underline"
          >
            {{ de.accounts.open }}
          </NuxtLink>
          <span v-else>{{ de.accounts.noAccount }}</span>
        </template>
        <template v-else>
          {{ row[column] }}
        </template>
      </template>
    </AppTable>
  </section>
  </div>
</template>
