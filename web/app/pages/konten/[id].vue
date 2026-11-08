<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppEmptyState from '~/components/ui/AppEmptyState.vue'
import AppMoney from '~/components/ui/AppMoney.vue'
import AppPageHeader from '~/components/ui/AppPageHeader.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import AppTable from '~/components/ui/AppTable.vue'
import AdjustDialog from '~/features/accounts/AdjustDialog.vue'
import ResetDialog from '~/features/accounts/ResetDialog.vue'
import { de } from '~/i18n/de'
import type { AccountDetail, Currency, StaffPosting } from '~/types/api'
import { useStaff } from '~/composables/useStaff'
import { errorMessage } from '~/utils/errors'
import { ApiError } from '~/utils/staffApi'
import { browserTimeZone, formatInstant } from '~/utils/time'

// Konto: owner, balances, the history with "Mehr laden", and for admins the reset and adjust dialogs of a player.
const route = useRoute()
const staff = useStaff()
const accountId = computed(() => String(route.params.id ?? ''))

const account = ref<AccountDetail | null>(null)
const currencies = ref<Currency[]>([])
const postings = ref<StaffPosting[]>([])
const nextCursor = ref<string | null>(null)
const failure = ref('')
const notFound = ref(false)
const resetOpen = ref(false)
const adjustOpen = ref(false)

const isPlayer = computed(() => account.value?.kind === 'PLAYER')
const digitsOf = (code: string) => currencies.value.find(item => item.code === code)?.fractionDigits ?? 0
const writable = computed(() => staff.canWrite.value && isPlayer.value)

useHead({ title: de.titles.account })

const historyColumns = [
  { key: 'createdAt', label: de.account.created },
  { key: 'kind', label: de.account.kindLabel },
  { key: 'reason', label: de.account.reason },
  { key: 'actor', label: de.account.actor },
  { key: 'amount', label: de.account.amount, align: 'end' as const }
]

const historyRows = computed(() => postings.value.map(posting => ({
  id: posting.id,
  createdAt: formatInstant(posting.createdAt, browserTimeZone()),
  kind: de.kinds.posting[posting.kind] ?? posting.kind,
  reason: posting.reason ?? '',
  actor: posting.actorName ?? posting.actor ?? de.log.unknownActor,
  amount: posting.amount,
  currency: posting.currency
})))

async function loadAccount() {
  try {
    account.value = await staff.api.account(accountId.value)
    notFound.value = false
  } catch (error) {
    account.value = null
    notFound.value = error instanceof ApiError && error.code === 'ACCOUNT_NOT_FOUND'
    failure.value = notFound.value ? '' : errorMessage(error)
  }
}

async function loadHistory(more = false) {
  try {
    const page = await staff.api.postings(accountId.value, more && nextCursor.value ? { cursor: nextCursor.value } : {})
    postings.value = more ? [...postings.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor ?? null
  } catch (error) {
    failure.value = errorMessage(error)
  }
}

async function reload() {
  await loadAccount()
  await loadHistory()
}

onMounted(async () => {
  try {
    currencies.value = (await staff.api.currencies()).items
  } catch (error) {
    failure.value = errorMessage(error)
  }
  await reload()
})
</script>

<template>
  <div class="flex flex-col gap-6">
  <NuxtLink to="/konten" class="inline-flex min-h-11 items-center font-medium underline">
    {{ de.account.backToSearch }}
  </NuxtLink>

  <AppStatus v-if="failure" :message="failure" tone="error" />

  <AppEmptyState v-if="notFound">
    {{ de.account.notFound }}
  </AppEmptyState>

  <template v-else-if="account">
    <AppPageHeader :title="de.titles.account" :description="de.kinds.account[account.kind] ?? account.kind" />

    <section class="flex flex-col gap-3 rounded-lg border border-(--c-border) bg-(--c-surface) p-4" aria-labelledby="account-owner">
      <h2 id="account-owner" class="text-lg font-semibold">
        {{ de.account.owner }}
      </h2>
      <dl v-if="account.owner" class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        <dt class="font-medium">{{ de.account.ownerName }}</dt>
        <dd class="break-all">{{ account.owner.name ?? de.common.empty }}</dd>
        <dt class="font-medium">{{ de.account.ownerUuid }}</dt>
        <dd class="break-all font-mono text-sm">{{ account.owner.uuid }}</dd>
      </dl>
      <p v-else class="text-(--c-muted)">
        {{ de.account.noOwner }}
      </p>
      <p v-if="account.client" class="text-(--c-muted)">
        {{ de.account.client }}: {{ account.client.displayName }}
      </p>
      <p v-if="account.currency" class="text-(--c-muted)">
        {{ de.account.currency }}: {{ account.currency }}
      </p>
    </section>

    <section class="flex flex-col gap-3" aria-labelledby="account-balances">
      <h2 id="account-balances" class="text-lg font-semibold">
        {{ de.account.balances }}
      </h2>
      <AppEmptyState v-if="account.balances.length === 0">
        {{ de.account.noBalances }}
      </AppEmptyState>
      <ul v-else class="grid gap-2 sm:grid-cols-2">
        <li
          v-for="balance in account.balances"
          :key="balance.currency"
          class="flex justify-between gap-3 rounded-lg border border-(--c-border) bg-(--c-surface) p-3"
        >
          <span class="font-medium">{{ balance.currency }}</span>
          <AppMoney :amount="balance.amount" :currency="balance.currency" :fraction-digits="digitsOf(balance.currency)" />
        </li>
      </ul>
    </section>

    <section v-if="isPlayer" class="flex flex-col gap-3" aria-labelledby="account-actions">
      <h2 id="account-actions" class="text-lg font-semibold">
        {{ de.account.reset }} / {{ de.account.adjust }}
      </h2>
      <p v-if="!staff.canWrite.value" class="text-(--c-muted)">
        {{ de.app.readOnlyNote }}
      </p>
      <div class="flex flex-wrap gap-3">
        <AppButton variant="danger" :disabled="!writable" @click="resetOpen = true">
          {{ de.account.reset }}
        </AppButton>
        <AppButton variant="secondary" :disabled="!writable" @click="adjustOpen = true">
          {{ de.account.adjust }}
        </AppButton>
      </div>
      <ResetDialog
        :open="resetOpen"
        :account-id="accountId"
        :balances="account.balances"
        :currencies="currencies"
        @close="resetOpen = false"
        @done="reload"
      />
      <AdjustDialog
        :open="adjustOpen"
        :account-id="accountId"
        :currencies="currencies"
        @close="adjustOpen = false"
        @done="reload"
      />
    </section>
    <p v-else class="text-(--c-muted)">
      {{ de.account.notPlayer }}
    </p>

    <section class="flex flex-col gap-3" aria-labelledby="account-history">
      <h2 id="account-history" class="text-lg font-semibold">
        {{ de.account.history }}
      </h2>
      <AppEmptyState v-if="postings.length === 0">
        {{ de.account.noHistory }}
      </AppEmptyState>
      <AppTable v-else :caption="de.account.history" :columns="historyColumns" :rows="historyRows" row-key="id">
        <template #cell="{ row, column }">
          <AppMoney
            v-if="column === 'amount'"
            :amount="String(row.amount)"
            :currency="String(row.currency)"
            :fraction-digits="digitsOf(String(row.currency))"
          />
          <template v-else>
            {{ row[column] }}
          </template>
        </template>
      </AppTable>
      <div v-if="nextCursor" class="flex justify-start">
        <AppButton variant="secondary" @click="loadHistory(true)">
          {{ de.account.loadMore }}
        </AppButton>
      </div>
      <p v-else-if="postings.length > 0" class="text-(--c-muted)">
        {{ de.account.noMore }}
      </p>
    </section>
  </template>
  </div>
</template>
