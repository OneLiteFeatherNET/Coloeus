<script setup lang="ts">
import { computed } from 'vue'
import { formatMoney } from '~/utils/money'

// A money value: a plain decimal string, shown with the German separators and the currency code. It knows nothing
// about the account the value belongs to, and no number is ever parsed from the amount.
const props = defineProps<{
  amount: string
  currency: string
  fractionDigits: number
}>()

const text = computed(() => `${formatMoney(props.amount, props.fractionDigits)} ${props.currency}`)
</script>

<template>
  <span class="tabular-nums whitespace-nowrap">{{ text }}</span>
</template>
