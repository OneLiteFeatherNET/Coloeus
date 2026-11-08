<script setup lang="ts">
import { computed } from 'vue'

// A progress indicator, linear or circular. Without a value it is indeterminate and only moves when the motion setting
// allows it. The label is a prop and names the indicator for assistive technology.
const props = withDefaults(defineProps<{
  label: string
  mode?: 'linear' | 'circular'
  value?: number
}>(), {
  mode: 'linear',
  value: undefined
})

const determinate = computed(() => props.value !== undefined)
const percent = computed(() => Math.min(100, Math.max(0, props.value ?? 0)))
// The circle of radius 20 has a circumference of about 125.66.
const arc = computed(() => (percent.value / 100) * 125.66)
</script>

<template>
  <div
    v-if="mode === 'linear'"
    role="progressbar"
    :aria-label="label"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-valuenow="determinate ? percent : undefined"
    class="relative h-1 w-full overflow-hidden rounded-full bg-surface-container-highest"
  >
    <div
      class="h-full bg-primary"
      :class="determinate ? '' : 'w-1/3 animate-pulse'"
      :style="determinate ? { width: `${percent}%` } : undefined"
    />
  </div>
  <svg
    v-else
    role="progressbar"
    :aria-label="label"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-valuenow="determinate ? percent : undefined"
    viewBox="0 0 48 48"
    class="size-12 -rotate-90 text-primary"
    :class="determinate ? '' : 'animate-spin'"
  >
    <circle cx="24" cy="24" r="20" fill="none" stroke="currentColor" stroke-width="4" class="text-surface-container-highest" />
    <circle
      cx="24"
      cy="24"
      r="20"
      fill="none"
      stroke="currentColor"
      stroke-width="4"
      stroke-linecap="round"
      :stroke-dasharray="determinate ? `${arc} 125.66` : '90 125.66'"
    />
  </svg>
</template>
