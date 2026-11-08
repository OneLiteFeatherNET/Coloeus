<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppDialog from '~/components/ui/AppDialog.vue'
import AppFormField from '~/components/ui/AppFormField.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import { de } from '~/i18n/de'
import type { AmountJson, Currency, StaffBooking } from '~/types/api'
import { createSubmitKey } from '~/utils/idempotency'
import { errorMessage } from '~/utils/errors'
import { formatMoney, isPlainDecimal } from '~/utils/money'
import { checkReason } from '~/utils/validation'
import { useStaff } from '~/composables/useStaff'

// The reset dialog of a player account. It books the positive balance of one currency or of all currencies into the
// admin sink. The page passes the account's balances and the currency table; the dialog knows no other account.
const props = defineProps<{
  open: boolean
  accountId: string
  balances: AmountJson[]
  currencies: Currency[]
}>()

const emit = defineEmits<{ close: [], done: [booking: StaffBooking] }>()

const ALL = ''
const staff = useStaff()
const submitKey = createSubmitKey()
const currency = ref(ALL)
const reason = ref('')
const busy = ref(false)
const failure = ref('')

watch(() => props.open, (open) => {
  if (open) {
    currency.value = ALL
    reason.value = ''
    failure.value = ''
    submitKey.reset()
  }
})

const digitsOf = (code: string) => props.currencies.find(item => item.code === code)?.fractionDigits ?? 0

const reasonProblem = computed(() => {
  const problem = checkReason(reason.value)
  if (problem === 'REASON_REQUIRED') {
    return de.common.reasonRequired
  }
  return problem === 'REASON_TOO_LONG' ? de.common.reasonTooLong : undefined
})

const selectedBalances = computed(() => props.balances
  .filter(balance => currency.value === ALL || balance.currency === currency.value)
  .filter(balance => isPlainDecimal(balance.amount) && !balance.amount.startsWith('-') && !/^0*(\.0*)?$/.test(balance.amount)))

const previewLines = computed(() => selectedBalances.value.map(balance =>
  de.reset.preview(formatMoney(balance.amount, digitsOf(balance.currency)), balance.currency)))

const valid = computed(() => reasonProblem.value === undefined)

async function submit() {
  if (!valid.value || busy.value) {
    return
  }
  const body = {
    currency: currency.value === ALL ? null : currency.value,
    reason: reason.value.trim()
  }
  busy.value = true
  failure.value = ''
  try {
    const booking = await staff.api.reset(props.accountId, body, submitKey.forRequest(JSON.stringify(body)))
    submitKey.reset()
    staff.announce(de.reset.success)
    emit('done', booking)
    emit('close')
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="de.reset.title" :description="de.reset.description" @close="emit('close')">
    <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
      <AppFormField v-slot="{ id, describedby }" :label="de.reset.currency">
        <select
          :id="id"
          v-model="currency"
          :aria-describedby="describedby"
          class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3"
        >
          <option :value="ALL">
            {{ de.reset.allCurrencies }}
          </option>
          <option v-for="item in currencies" :key="item.code" :value="item.code">
            {{ item.code }}
          </option>
        </select>
      </AppFormField>

      <div class="rounded-lg bg-(--c-disabled-bg) p-3" aria-live="polite">
        <p v-if="previewLines.length === 0">
          {{ de.reset.previewNone }}
        </p>
        <ul v-else class="flex flex-col gap-1">
          <li v-for="line in previewLines" :key="line">
            {{ line }}
          </li>
        </ul>
      </div>

      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.reset.reasonLabel" :hint="de.reset.reasonHint" :error="reasonProblem">
        <input
          :id="id"
          v-model="reason"
          type="text"
          maxlength="400"
          :aria-invalid="invalid"
          :aria-describedby="describedby"
          class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3"
        >
      </AppFormField>

      <AppStatus v-if="failure" :message="failure" tone="error" />

      <div class="flex flex-wrap justify-end gap-3">
        <AppButton variant="outlined" @click="emit('close')">
          {{ de.reset.cancel }}
        </AppButton>
        <AppButton type="submit" variant="danger" :disabled="!valid || busy">
          {{ busy ? de.common.submitting : de.reset.submit }}
        </AppButton>
      </div>
    </form>
  </AppDialog>
</template>
