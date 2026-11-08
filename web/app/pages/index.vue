<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppBarChart from '~/components/ui/AppBarChart.vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppEmptyState from '~/components/ui/AppEmptyState.vue'
import AppMoney from '~/components/ui/AppMoney.vue'
import AppPageHeader from '~/components/ui/AppPageHeader.vue'
import AppSegmentedButton from '~/components/ui/AppSegmentedButton.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import AppTable from '~/components/ui/AppTable.vue'
import { de } from '~/i18n/de'
import type { Currency, MoneyFlow, MoneySeries } from '~/types/api'
import { useStaff } from '~/composables/useStaff'
import { errorMessage } from '~/utils/errors'
import { formatDay } from '~/utils/time'

// Übersicht: money created and destroyed per client and currency, the staff bucket, and the daily chart.
useHead({ title: de.titles.overview })

const staff = useStaff()
const flowWindow = ref<'7D' | 'ALL'>('7D')
const flow = ref<MoneyFlow | null>(null)
const currencies = ref<Currency[]>([])
const seriesCurrency = ref('')
const days = ref<7 | 30>(7)
const series = ref<MoneySeries | null>(null)
const showTable = ref(false)
const failure = ref('')

const digitsOf = (code: string) => currencies.value.find(item => item.code === code)?.fractionDigits ?? 0

const clientRows = computed(() => (flow.value?.clients ?? []).flatMap(client => client.currencies.map(entry => ({
  id: `${client.clientId}-${entry.currency}`,
  client: client.displayName,
  currency: entry.currency,
  created: entry.created,
  destroyed: entry.destroyed,
  netInWindow: entry.netInWindow
}))))

const staffRows = computed(() => (flow.value?.staff ?? []).map(entry => ({ id: entry.currency, ...entry })))

const chartDays = computed(() => series.value?.days ?? [])
const tableRows = computed(() => chartDays.value.map(day => ({ id: day.date, date: formatDay(day.date), created: day.created, destroyed: day.destroyed })))

const clientColumns = [
  { key: 'client', label: de.overview.client },
  { key: 'currency', label: de.overview.currency },
  { key: 'created', label: de.overview.created, align: 'end' as const },
  { key: 'destroyed', label: de.overview.destroyed, align: 'end' as const },
  { key: 'netInWindow', label: de.overview.net, align: 'end' as const }
]

const staffColumns = [
  { key: 'currency', label: de.overview.currency },
  { key: 'credited', label: de.overview.staffCredited, align: 'end' as const },
  { key: 'debited', label: de.overview.staffDebited, align: 'end' as const },
  { key: 'netInWindow', label: de.overview.net, align: 'end' as const }
]

const dayColumns = [
  { key: 'date', label: de.overview.tableDay },
  { key: 'created', label: de.overview.created, align: 'end' as const },
  { key: 'destroyed', label: de.overview.destroyed, align: 'end' as const }
]

const chartLabel = computed(() => de.overview.chartLabel(seriesCurrency.value, days.value))

async function loadFlow() {
  try {
    flow.value = await staff.api.moneyFlow(flowWindow.value)
    failure.value = ''
  } catch (error) {
    failure.value = errorMessage(error)
  }
}

async function loadSeries() {
  if (!seriesCurrency.value) {
    series.value = null
    return
  }
  try {
    series.value = await staff.api.moneySeries(seriesCurrency.value, days.value)
  } catch (error) {
    failure.value = errorMessage(error)
  }
}

const windowOptions = [
  { value: '7D', label: de.overview.window7d },
  { value: 'ALL', label: de.overview.windowAll }
]

const dayOptions = [
  { value: '7', label: de.overview.days7 },
  { value: '30', label: de.overview.days30 }
]

function setWindow(value: string) {
  flowWindow.value = value === 'ALL' ? 'ALL' : '7D'
}

function setDays(value: string) {
  days.value = value === '30' ? 30 : 7
}

watch(flowWindow, loadFlow)
watch([seriesCurrency, days], loadSeries)

onMounted(async () => {
  try {
    currencies.value = (await staff.api.currencies()).items
    seriesCurrency.value = currencies.value[0]?.code ?? ''
  } catch (error) {
    failure.value = errorMessage(error)
  }
  await loadFlow()
})
</script>

<template>
  <div class="flex flex-col gap-6">
  <AppPageHeader :title="de.overview.heading" :description="de.overview.intro">
    <template #actions>
      <AppSegmentedButton
        :label="de.overview.windowLabel"
        :model-value="flowWindow"
        :options="windowOptions"
        @update:model-value="setWindow"
      />
    </template>
  </AppPageHeader>

  <AppStatus :message="failure" tone="error" />

  <section v-if="flow" class="flex flex-col gap-4" aria-labelledby="flow-currencies">
    <h2 id="flow-currencies" class="text-title-large">
      {{ de.overview.currency }}
    </h2>
    <AppEmptyState v-if="flow.currencies.length === 0">
      {{ de.overview.noCurrencies }}
    </AppEmptyState>
    <ul v-else class="grid gap-4 md:grid-cols-2">
      <li v-for="entry in flow.currencies" :key="entry.currency" class="flex flex-col gap-2 rounded-md bg-surface-container-low p-4 shadow-elevation-1">
        <h3 class="text-title-medium">
          {{ entry.currency }}
        </h3>
        <dl class="grid grid-cols-2 gap-x-4 gap-y-1">
          <dt>{{ de.overview.created }}</dt>
          <dd class="text-end"><AppMoney :amount="entry.created" :currency="entry.currency" :fraction-digits="digitsOf(entry.currency)" /></dd>
          <dt>{{ de.overview.destroyed }}</dt>
          <dd class="text-end"><AppMoney :amount="entry.destroyed" :currency="entry.currency" :fraction-digits="digitsOf(entry.currency)" /></dd>
          <dt>{{ de.overview.net }}</dt>
          <dd class="text-end"><AppMoney :amount="entry.netInWindow" :currency="entry.currency" :fraction-digits="digitsOf(entry.currency)" /></dd>
          <dt>{{ de.overview.circulation }}</dt>
          <dd class="text-end"><AppMoney :amount="entry.circulation" :currency="entry.currency" :fraction-digits="digitsOf(entry.currency)" /></dd>
        </dl>
      </li>
    </ul>
  </section>

  <section v-if="flow" class="flex flex-col gap-3" aria-labelledby="flow-clients">
    <h2 id="flow-clients" class="text-title-large">
      {{ de.overview.perClient }}
    </h2>
    <AppEmptyState v-if="clientRows.length === 0">
      {{ de.overview.noClients }}
    </AppEmptyState>
    <AppTable v-else :caption="de.overview.perClientCaption" :columns="clientColumns" :rows="clientRows" row-key="id">
      <template #cell="{ row, column }">
        <AppMoney
          v-if="column === 'created' || column === 'destroyed' || column === 'netInWindow'"
          :amount="String(row[column])"
          :currency="String(row.currency)"
          :fraction-digits="digitsOf(String(row.currency))"
        />
        <template v-else>
          {{ row[column] }}
        </template>
      </template>
    </AppTable>
  </section>

  <section v-if="flow" class="flex flex-col gap-3" aria-labelledby="flow-staff">
    <h2 id="flow-staff" class="text-title-large">
      {{ de.overview.staffHeading }}
    </h2>
    <AppEmptyState v-if="staffRows.length === 0">
      {{ de.overview.noStaff }}
    </AppEmptyState>
    <AppTable v-else :caption="de.overview.staffCaption" :columns="staffColumns" :rows="staffRows" row-key="id">
      <template #cell="{ row, column }">
        <AppMoney
          v-if="column !== 'currency'"
          :amount="String(row[column])"
          :currency="String(row.currency)"
          :fraction-digits="digitsOf(String(row.currency))"
        />
        <template v-else>
          {{ row[column] }}
        </template>
      </template>
    </AppTable>
  </section>

  <section class="flex flex-col gap-3" aria-labelledby="flow-daily">
    <h2 id="flow-daily" class="text-title-large">
      {{ de.overview.dailyHeading }}
    </h2>
    <div class="flex flex-wrap items-end gap-3">
      <label class="flex flex-col gap-1">
        <span class="font-medium">{{ de.overview.dailyCurrency }}</span>
        <select v-model="seriesCurrency" class="md-field">
          <option v-for="item in currencies" :key="item.code" :value="item.code">
            {{ item.code }}
          </option>
        </select>
      </label>
      <AppSegmentedButton
        :label="de.overview.dailyHeading"
        :model-value="String(days)"
        :options="dayOptions"
        @update:model-value="setDays"
      />
      <AppButton variant="outlined" :pressed="showTable" @click="showTable = !showTable">
        {{ showTable ? de.overview.hideTable : de.overview.showTable }}
      </AppButton>
    </div>

    <AppEmptyState v-if="!series || chartDays.length === 0">
      {{ de.overview.noSeries }}
    </AppEmptyState>
    <AppTable
      v-else-if="showTable"
      :caption="de.overview.dailyCaption(seriesCurrency, days)"
      :columns="dayColumns"
      :rows="tableRows"
      row-key="id"
    >
      <template #cell="{ row, column }">
        <AppMoney
          v-if="column === 'created' || column === 'destroyed'"
          :amount="String(row[column])"
          :currency="seriesCurrency"
          :fraction-digits="digitsOf(seriesCurrency)"
        />
        <template v-else>
          {{ row[column] }}
        </template>
      </template>
    </AppTable>
    <AppBarChart
      v-else
      :label="chartLabel"
      :labels="chartDays.map(day => day.date)"
      :series="[
        { name: de.overview.chartCreated, values: chartDays.map(day => day.created), pattern: 'solid' },
        { name: de.overview.chartDestroyed, values: chartDays.map(day => day.destroyed), pattern: 'hatched' }
      ]"
    />
  </section>
  </div>
</template>
