<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppEmptyState from '~/components/ui/AppEmptyState.vue'
import AppPageHeader from '~/components/ui/AppPageHeader.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import AppTable from '~/components/ui/AppTable.vue'
import RegisterClientDialog from '~/features/clients/RegisterClientDialog.vue'
import GrantsDialog from '~/features/clients/GrantsDialog.vue'
import { de } from '~/i18n/de'
import type { ClientView, Currency } from '~/types/api'
import { useStaff } from '~/composables/useStaff'
import { errorMessage } from '~/utils/errors'
import { browserTimeZone, formatInstant } from '~/utils/time'

// Server und Rechte: the registered clients and their grants. Writes are for administrators only.
useHead({ title: de.titles.clients })

const staff = useStaff()
const clients = ref<ClientView[]>([])
const currencies = ref<Currency[]>([])
const failure = ref('')
const registerOpen = ref(false)
const grantsFor = ref<ClientView | null>(null)

const columns = [
  { key: 'displayName', label: de.clients.displayName },
  { key: 'clientId', label: de.clients.clientId },
  { key: 'grants', label: de.clients.grants },
  { key: 'createdAt', label: de.clients.createdAt },
  { key: 'actions', label: '' }
]

const rows = computed(() => clients.value.map(client => ({
  id: client.clientId,
  displayName: client.displayName,
  clientId: client.clientId,
  grants: client.grants.length === 0
    ? de.clients.noGrants
    : client.grants.map(grant => `${grant.currency}: ${de.clients.permissions[grant.permission] ?? grant.permission}`).join(', '),
  createdAt: formatInstant(client.createdAt, browserTimeZone()),
  actions: client.clientId
})))

async function load() {
  try {
    clients.value = (await staff.api.clients()).items
    currencies.value = (await staff.api.currencies()).items
    failure.value = ''
  } catch (error) {
    failure.value = errorMessage(error)
  }
}

function editGrants(clientId: string) {
  grantsFor.value = clients.value.find(client => client.clientId === clientId) ?? null
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-6">
  <AppPageHeader :title="de.clients.heading" :description="de.clients.intro">
    <template #actions>
      <AppButton :disabled="!staff.canWrite.value" @click="registerOpen = true">
        {{ de.clients.register }}
      </AppButton>
    </template>
  </AppPageHeader>

  <p v-if="!staff.canWrite.value" class="text-(--c-muted)">
    {{ de.app.readOnlyNote }}
  </p>
  <AppStatus :message="failure" tone="error" />

  <AppEmptyState v-if="clients.length === 0">
    {{ de.clients.noClients }}
  </AppEmptyState>
  <AppTable v-else :caption="de.clients.heading" :columns="columns" :rows="rows" row-key="id">
    <template #cell="{ row, column }">
      <AppButton
        v-if="column === 'actions'"
        variant="outlined"
        :disabled="!staff.canWrite.value"
        @click="editGrants(String(row.actions))"
      >
        {{ de.clients.replaceGrants }}
      </AppButton>
      <template v-else>
        {{ row[column] }}
      </template>
    </template>
  </AppTable>

  <RegisterClientDialog :open="registerOpen" @close="registerOpen = false" @done="load" />
  <GrantsDialog
    :open="grantsFor !== null"
    :client="grantsFor"
    :currencies="currencies"
    @close="grantsFor = null"
    @done="load"
  />
  </div>
</template>
