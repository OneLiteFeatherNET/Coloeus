<script setup lang="ts">
import { computed, ref } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppEmptyState from '~/components/ui/AppEmptyState.vue'
import AppFormField from '~/components/ui/AppFormField.vue'
import AppPageHeader from '~/components/ui/AppPageHeader.vue'
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

function setMode(next: 'name' | 'uuid') {
  mode.value = next
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

  <form class="flex flex-col gap-4 rounded-lg border border-(--c-border) bg-(--c-surface) p-4" novalidate @submit.prevent="search">
    <fieldset class="flex flex-wrap gap-4">
      <legend class="font-medium">
        {{ de.accounts.byName }} / {{ de.accounts.byUuid }}
      </legend>
      <label class="flex min-h-11 items-center gap-2">
        <input type="radio" name="search-mode" value="name" :checked="mode === 'name'" @change="setMode('name')">
        {{ de.accounts.byName }}
      </label>
      <label class="flex min-h-11 items-center gap-2">
        <input type="radio" name="search-mode" value="uuid" :checked="mode === 'uuid'" @change="setMode('uuid')">
        {{ de.accounts.byUuid }}
      </label>
    </fieldset>

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
        class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3"
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
    <h2 id="search-results" class="text-xl font-semibold">
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
