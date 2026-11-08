<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppDialog from '~/components/ui/AppDialog.vue'
import AppFormField from '~/components/ui/AppFormField.vue'
import AppRadioGroup from '~/components/ui/AppRadioGroup.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import { de } from '~/i18n/de'
import type { AdjustRequest, Currency, StaffBooking } from '~/types/api'
import { errorMessage } from '~/utils/errors'
import { createSubmitKey } from '~/utils/idempotency'
import { checkAmountInput } from '~/utils/money'
import { checkReason } from '~/utils/validation'
import { useStaff } from '~/composables/useStaff'

// The adjust dialog of a player account: a credit or a debit of one amount in one currency. The amount is checked
// on the client before any request is sent; the server stays the authority.
const props = defineProps<{
  open: boolean
  accountId: string
  currencies: Currency[]
}>()

const emit = defineEmits<{ close: [], done: [booking: StaffBooking] }>()

const staff = useStaff()
const submitKey = createSubmitKey()
const direction = ref<AdjustRequest['direction']>('CREDIT')
const currency = ref('')
const amount = ref('')
const reason = ref('')
const busy = ref(false)
const failure = ref('')

watch(() => props.open, (open) => {
  if (open) {
    direction.value = 'CREDIT'
    currency.value = props.currencies[0]?.code ?? ''
    amount.value = ''
    reason.value = ''
    failure.value = ''
    submitKey.reset()
  }
})

const directionOptions = [
  { value: 'CREDIT', label: de.adjust.credit },
  { value: 'DEBIT', label: de.adjust.debit }
]

function setDirection(value: string) {
  direction.value = value === 'DEBIT' ? 'DEBIT' : 'CREDIT'
}

const digits = computed(() => props.currencies.find(item => item.code === currency.value)?.fractionDigits ?? 0)

/** The amount as the API expects it, or undefined while the field is empty or wrong. */
const amountValue = computed(() => {
  if (amount.value.trim() === '') {
    return undefined
  }
  const result = checkAmountInput(amount.value, digits.value)
  return result.ok ? result.amount : undefined
})

const amountError = computed(() => {
  if (amount.value.trim() === '') {
    return undefined
  }
  const result = checkAmountInput(amount.value, digits.value)
  if (result.ok) {
    return undefined
  }
  return result.problem === 'SCALE_EXCEEDED' ? de.adjust.amountScale(digits.value) : de.adjust.amountInvalid
})

const reasonProblem = computed(() => {
  const problem = checkReason(reason.value)
  if (problem === 'REASON_REQUIRED') {
    return de.common.reasonRequired
  }
  return problem === 'REASON_TOO_LONG' ? de.common.reasonTooLong : undefined
})

const valid = computed(() => amountValue.value !== undefined && currency.value !== '' && reasonProblem.value === undefined)

async function submit() {
  const normalized = amountValue.value
  if (normalized === undefined || !valid.value || busy.value) {
    return
  }
  const body: AdjustRequest = {
    currency: currency.value,
    direction: direction.value,
    amount: normalized,
    reason: reason.value.trim()
  }
  busy.value = true
  failure.value = ''
  try {
    const booking = await staff.api.adjust(props.accountId, body, submitKey.forRequest(JSON.stringify(body)))
    submitKey.reset()
    staff.announce(de.adjust.success)
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
  <AppDialog :open="open" :title="de.adjust.title" :description="de.adjust.description" @close="emit('close')">
    <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
      <AppRadioGroup
        name="direction"
        :legend="de.adjust.direction"
        :model-value="direction"
        :options="directionOptions"
        @update:model-value="setDirection"
      />

      <AppFormField v-slot="{ id, describedby }" :label="de.adjust.currency">
        <select
          :id="id"
          v-model="currency"
          :aria-describedby="describedby"
          class="md-field"
        >
          <option v-for="item in currencies" :key="item.code" :value="item.code">
            {{ item.code }}
          </option>
        </select>
      </AppFormField>

      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.adjust.amountLabel" :hint="de.adjust.amountHint" :error="amountError">
        <input
          :id="id"
          v-model="amount"
          type="text"
          inputmode="decimal"
          autocomplete="off"
          :aria-invalid="invalid"
          :aria-describedby="describedby"
          class="md-field"
        >
      </AppFormField>

      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.adjust.reasonLabel" :hint="de.adjust.reasonHint" :error="reasonProblem">
        <input
          :id="id"
          v-model="reason"
          type="text"
          maxlength="400"
          :aria-invalid="invalid"
          :aria-describedby="describedby"
          class="md-field"
        >
      </AppFormField>

      <AppStatus v-if="failure" :message="failure" tone="error" />

      <div class="flex flex-wrap justify-end gap-3">
        <AppButton variant="outlined" @click="emit('close')">
          {{ de.adjust.cancel }}
        </AppButton>
        <AppButton type="submit" :disabled="!valid || busy">
          {{ busy ? de.common.submitting : de.adjust.submit }}
        </AppButton>
      </div>
    </form>
  </AppDialog>
</template>
