<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppDialog from '~/components/ui/AppDialog.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import { de } from '~/i18n/de'
import type { ClientView, Currency, Grant, Permission } from '~/types/api'
import { errorMessage } from '~/utils/errors'
import { createSubmitKey } from '~/utils/idempotency'
import { useStaff } from '~/composables/useStaff'

// Replaces all grants of one client. The rows are edited as a whole; a duplicate pair of currency and permission is
// refused in the form, before the request is sent. An empty list removes every grant.
const props = defineProps<{
  open: boolean
  client: ClientView | null
  currencies: Currency[]
}>()
const emit = defineEmits<{ close: [], done: [client: ClientView] }>()

const PERMISSIONS: Permission[] = ['MINT', 'BURN', 'TRANSFER_ON_BEHALF', 'READ']

const staff = useStaff()
const submitKey = createSubmitKey()
const rows = ref<Grant[]>([])
const busy = ref(false)
const failure = ref('')

watch(() => props.open, (open) => {
  if (open) {
    rows.value = (props.client?.grants ?? []).map(grant => ({ ...grant }))
    failure.value = ''
    submitKey.reset()
  }
})

const duplicate = computed(() => {
  const seen = new Set<string>()
  for (const row of rows.value) {
    const key = `${row.currency}|${row.permission}`
    if (seen.has(key)) {
      return true
    }
    seen.add(key)
  }
  return false
})

const incomplete = computed(() => rows.value.some(row => row.currency === '' || row.permission === undefined))
const valid = computed(() => !duplicate.value && !incomplete.value)

function addRow() {
  rows.value.push({ currency: props.currencies[0]?.code ?? '', permission: 'READ' })
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
}

async function submit() {
  if (!props.client || !valid.value || busy.value) {
    return
  }
  const grants = rows.value.map(row => ({ currency: row.currency, permission: row.permission }))
  const clientId = props.client.clientId
  busy.value = true
  failure.value = ''
  try {
    const updated = await staff.api.replaceGrants(clientId, grants, submitKey.forRequest(JSON.stringify({ clientId, grants })))
    submitKey.reset()
    staff.announce(de.clients.grantsSaved)
    emit('done', updated)
    emit('close')
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="de.clients.grantsTitle" :description="client?.displayName" @close="emit('close')">
    <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
      <p v-if="rows.length === 0" class="text-on-surface-variant">
        {{ de.clients.emptyGrants }}
      </p>
      <fieldset v-for="(row, index) in rows" :key="index" class="flex flex-wrap items-end gap-3 rounded-md border border-outline-variant p-3">
        <legend class="sr-only">
          {{ de.clients.grants }} {{ index + 1 }}
        </legend>
        <label class="flex min-w-40 flex-1 flex-col gap-1">
          <span class="font-medium">{{ de.clients.grantCurrency }}</span>
          <select v-model="row.currency" class="md-field">
            <option v-for="item in currencies" :key="item.code" :value="item.code">
              {{ item.code }}
            </option>
          </select>
        </label>
        <label class="flex min-w-40 flex-1 flex-col gap-1">
          <span class="font-medium">{{ de.clients.grantPermission }}</span>
          <select v-model="row.permission" class="md-field">
            <option v-for="permission in PERMISSIONS" :key="permission" :value="permission">
              {{ de.clients.permissions[permission] }}
            </option>
          </select>
        </label>
        <AppButton variant="outlined" @click="removeRow(index)">
          {{ de.clients.removeGrant }}
        </AppButton>
      </fieldset>
      <p v-if="duplicate" class="font-medium text-error" role="alert">
        {{ de.clients.duplicateGrant }}
      </p>
      <div>
        <AppButton variant="outlined" @click="addRow">
          {{ de.clients.addGrant }}
        </AppButton>
      </div>
      <AppStatus v-if="failure" :message="failure" tone="error" />
      <div class="flex flex-wrap justify-end gap-3">
        <AppButton variant="outlined" @click="emit('close')">
          {{ de.common.cancel }}
        </AppButton>
        <AppButton type="submit" :disabled="!valid || busy">
          {{ busy ? de.common.submitting : de.clients.replaceGrants }}
        </AppButton>
      </div>
    </form>
  </AppDialog>
</template>
