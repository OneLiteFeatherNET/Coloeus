<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppDialog from '~/components/ui/AppDialog.vue'
import AppEmptyState from '~/components/ui/AppEmptyState.vue'
import AppPageHeader from '~/components/ui/AppPageHeader.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import AppTable from '~/components/ui/AppTable.vue'
import CurrencyDialog from '~/features/currencies/CurrencyDialog.vue'
import { de } from '~/i18n/de'
import type { Currency } from '~/types/api'
import { createSubmitKey } from '~/utils/idempotency'
import { errorMessage } from '~/utils/errors'
import { useStaff } from '~/composables/useStaff'
import { browserTimeZone, formatInstant } from '~/utils/time'

// Währungen: the currency table with the default marked in text, creation and the change of the default.
useHead({ title: de.titles.currencies })

const staff = useStaff()
const currencies = ref<Currency[]>([])
const failure = ref('')
const createOpen = ref(false)
const pending = ref<Currency | null>(null)
const busy = ref(false)
const confirmKey = createSubmitKey()

const columns = [
  { key: 'code', label: de.currencies.code },
  { key: 'names', label: de.currencies.nameSingular },
  { key: 'symbol', label: de.currencies.symbol },
  { key: 'fractionDigits', label: de.currencies.fractionDigits, align: 'end' as const },
  { key: 'defaultCurrency', label: de.currencies.defaultMarker },
  { key: 'createdAt', label: de.currencies.created },
  { key: 'actions', label: '' }
]

function rowsOf(items: Currency[]) {
  return items.map(currency => ({
    id: currency.code,
    code: currency.code,
    names: `${currency.nameSingular} / ${currency.namePlural}`,
    symbol: currency.symbol,
    fractionDigits: String(currency.fractionDigits),
    defaultCurrency: currency.defaultCurrency ? de.currencies.defaultMarker : '',
    createdAt: formatInstant(currency.createdAt, browserTimeZone()),
    actions: currency.code,
    isDefault: currency.defaultCurrency
  }))
}

const rows = ref<ReturnType<typeof rowsOf>>([])

async function load() {
  try {
    currencies.value = (await staff.api.currencies()).items
    rows.value = rowsOf(currencies.value)
    failure.value = ''
  } catch (error) {
    failure.value = errorMessage(error)
  }
}

async function setDefault() {
  const target = pending.value
  if (!target || busy.value) {
    return
  }
  busy.value = true
  failure.value = ''
  try {
    await staff.api.setDefaultCurrency(target.code, confirmKey.forRequest(target.code))
    confirmKey.reset()
    staff.announce(de.currencies.defaultOk)
    pending.value = null
    await load()
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-6">
  <AppPageHeader :title="de.currencies.heading" :description="de.currencies.intro">
    <template #actions>
      <AppButton :disabled="!staff.canWrite.value" @click="createOpen = true">
        {{ de.currencies.create }}
      </AppButton>
    </template>
  </AppPageHeader>

  <p v-if="!staff.canWrite.value" class="text-(--c-muted)">
    {{ de.app.readOnlyNote }}
  </p>
  <AppStatus :message="failure" tone="error" />

  <AppEmptyState v-if="currencies.length === 0">
    {{ de.currencies.noCurrencies }}
  </AppEmptyState>
  <AppTable v-else :caption="de.currencies.heading" :columns="columns" :rows="rows" row-key="id">
    <template #cell="{ row, column }">
      <template v-if="column === 'actions'">
        <AppButton
          v-if="!row.isDefault"
          variant="outlined"
          :disabled="!staff.canWrite.value"
          @click="pending = currencies.find(item => item.code === row.code) ?? null"
        >
          {{ de.currencies.setDefault }}
        </AppButton>
      </template>
      <template v-else>
        {{ row[column] }}
      </template>
    </template>
  </AppTable>

  <CurrencyDialog :open="createOpen" @close="createOpen = false" @done="load" />

  <AppDialog
    :open="pending !== null"
    :title="de.currencies.setDefaultTitle"
    :description="pending ? de.currencies.defaultConfirmText(pending.code) : undefined"
    @close="pending = null"
  >
    <div class="flex flex-wrap justify-end gap-3">
      <AppButton variant="outlined" @click="pending = null">
        {{ de.common.cancel }}
      </AppButton>
      <AppButton :disabled="busy" @click="setDefault">
        {{ busy ? de.common.submitting : de.currencies.setDefault }}
      </AppButton>
    </div>
  </AppDialog>
  </div>
</template>
