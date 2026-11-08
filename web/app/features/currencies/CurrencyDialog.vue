<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppDialog from '~/components/ui/AppDialog.vue'
import AppFormField from '~/components/ui/AppFormField.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import { de } from '~/i18n/de'
import type { Currency, CreateCurrencyRequest } from '~/types/api'
import { errorMessage } from '~/utils/errors'
import { createSubmitKey } from '~/utils/idempotency'
import { isCurrencyCode } from '~/utils/validation'
import { useStaff } from '~/composables/useStaff'

// Creates a currency with the ledger's rules: a code of 3 to 8 upper-case letters, names, a symbol and fraction digits
// from 0 to 8. The fraction digits cannot change afterwards, which the form says before the request.
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [], done: [currency: Currency] }>()

const staff = useStaff()
const submitKey = createSubmitKey()
const code = ref('')
const nameSingular = ref('')
const namePlural = ref('')
const symbol = ref('')
const fractionDigits = ref('2')
const busy = ref(false)
const failure = ref('')

watch(() => props.open, (open) => {
  if (open) {
    code.value = ''
    nameSingular.value = ''
    namePlural.value = ''
    symbol.value = ''
    fractionDigits.value = '2'
    failure.value = ''
    submitKey.reset()
  }
})

const codeError = computed(() => code.value === '' || isCurrencyCode(code.value) ? undefined : de.currencies.codeInvalid)
const singularError = computed(() => nameSingular.value.trim() === '' && nameSingular.value !== '' ? de.currencies.nameInvalid : undefined)
const pluralError = computed(() => namePlural.value.trim() === '' && namePlural.value !== '' ? de.currencies.nameInvalid : undefined)
const symbolError = computed(() => symbol.value.trim() === '' && symbol.value !== '' ? de.currencies.symbolInvalid : undefined)
const digitsValue = computed(() => /^\d+$/.test(fractionDigits.value) ? Number(fractionDigits.value) : NaN)
const digitsError = computed(() => Number.isInteger(digitsValue.value) && digitsValue.value >= 0 && digitsValue.value <= 8
  ? undefined
  : de.currencies.fractionInvalid)

const valid = computed(() => isCurrencyCode(code.value)
  && nameSingular.value.trim() !== '' && namePlural.value.trim() !== '' && symbol.value.trim() !== ''
  && digitsError.value === undefined)

async function submit() {
  if (!valid.value || busy.value) {
    return
  }
  const body: CreateCurrencyRequest = {
    code: code.value,
    nameSingular: nameSingular.value.trim(),
    namePlural: namePlural.value.trim(),
    symbol: symbol.value.trim(),
    fractionDigits: digitsValue.value
  }
  busy.value = true
  failure.value = ''
  try {
    const created = await staff.api.createCurrency(body, submitKey.forRequest(JSON.stringify(body)))
    submitKey.reset()
    staff.announce(de.currencies.createdOk)
    emit('done', created)
    emit('close')
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="de.currencies.createTitle" @close="emit('close')">
    <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.currencies.code" :hint="de.currencies.codeHint" :error="codeError">
        <input
          :id="id"
          v-model="code"
          type="text"
          autocomplete="off"
          :aria-invalid="invalid"
          :aria-describedby="describedby"
          class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3 font-mono"
        >
      </AppFormField>
      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.currencies.nameSingular" :error="singularError">
        <input :id="id" v-model="nameSingular" type="text" :aria-invalid="invalid" :aria-describedby="describedby" class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3">
      </AppFormField>
      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.currencies.namePlural" :error="pluralError">
        <input :id="id" v-model="namePlural" type="text" :aria-invalid="invalid" :aria-describedby="describedby" class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3">
      </AppFormField>
      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.currencies.symbol" :error="symbolError">
        <input :id="id" v-model="symbol" type="text" :aria-invalid="invalid" :aria-describedby="describedby" class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3">
      </AppFormField>
      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.currencies.fractionDigits" :hint="de.currencies.fractionHint" :error="digitsError">
        <input
          :id="id"
          v-model="fractionDigits"
          type="text"
          inputmode="numeric"
          :aria-invalid="invalid"
          :aria-describedby="describedby"
          class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3"
        >
      </AppFormField>
      <AppStatus v-if="failure" :message="failure" tone="error" />
      <div class="flex flex-wrap justify-end gap-3">
        <AppButton variant="outlined" @click="emit('close')">
          {{ de.common.cancel }}
        </AppButton>
        <AppButton type="submit" :disabled="!valid || busy">
          {{ busy ? de.common.submitting : de.currencies.create }}
        </AppButton>
      </div>
    </form>
  </AppDialog>
</template>
