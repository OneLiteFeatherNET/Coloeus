<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppEmptyState from '~/components/ui/AppEmptyState.vue'
import AppPageHeader from '~/components/ui/AppPageHeader.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import AppTable from '~/components/ui/AppTable.vue'
import { de } from '~/i18n/de'
import type { StaffAction } from '~/types/api'
import { useStaff } from '~/composables/useStaff'
import { errorMessage } from '~/utils/errors'
import { browserTimeZone, formatInstant } from '~/utils/time'

// Protokoll: every staff write, newest first. Links lead to the account, the client or the currency it touched.
useHead({ title: de.titles.log })

const staff = useStaff()
const actions = ref<StaffAction[]>([])
const nextCursor = ref<string | null>(null)
const failure = ref('')

const columns = [
  { key: 'createdAt', label: de.log.when },
  { key: 'actor', label: de.log.who },
  { key: 'action', label: de.log.action },
  { key: 'target', label: de.log.target },
  { key: 'detail', label: de.log.detail }
]

function targetPath(action: StaffAction): string {
  if (action.action === 'RESET' || action.action === 'ADJUST') {
    return `/konten/${action.target}`
  }
  if (action.action === 'CURRENCY_CREATED' || action.action === 'CURRENCY_DEFAULT_SET') {
    return '/waehrungen'
  }
  return '/clients'
}

function detailText(action: StaffAction): string {
  const reason = typeof action.detail?.reason === 'string' ? action.detail.reason : ''
  return reason
}

const rows = computed(() => actions.value.map(action => ({
  id: action.id,
  createdAt: formatInstant(action.createdAt, browserTimeZone()),
  actor: action.actorName ?? de.log.unknownActor,
  action: de.log.actions[action.action] ?? action.action,
  target: action.target,
  path: targetPath(action),
  detail: detailText(action)
})))

async function load(more = false) {
  try {
    const page = await staff.api.staffActions(more && nextCursor.value ? { cursor: nextCursor.value } : {})
    actions.value = more ? [...actions.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor ?? null
    failure.value = ''
  } catch (error) {
    failure.value = errorMessage(error)
  }
}

onMounted(() => load())
</script>

<template>
  <div class="flex flex-col gap-6">
  <AppPageHeader :title="de.log.heading" :description="de.log.intro" />

  <AppStatus :message="failure" tone="error" />

  <AppEmptyState v-if="actions.length === 0">
    {{ de.log.noEntries }}
  </AppEmptyState>
  <AppTable v-else :caption="de.log.heading" :columns="columns" :rows="rows" row-key="id">
    <template #cell="{ row, column }">
      <NuxtLink
        v-if="column === 'target'"
        :to="String(row.path)"
        class="inline-flex min-h-11 items-center break-all font-mono text-sm underline"
      >
        {{ row.target }}
      </NuxtLink>
      <template v-else>
        {{ row[column] }}
      </template>
    </template>
  </AppTable>

  <div v-if="nextCursor" class="flex justify-start">
    <AppButton variant="outlined" @click="load(true)">
      {{ de.log.loadMore }}
    </AppButton>
  </div>
  <p v-else-if="actions.length > 0" class="text-on-surface-variant">
    {{ de.log.noMore }}
  </p>
  </div>
</template>
