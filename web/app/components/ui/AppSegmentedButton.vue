<script setup lang="ts">
// A segmented button: one choice out of a few short options, such as a time window. Each option is a button that
// reports aria-pressed, so assistive technology reads the choice; the group has the name it was given.
export interface AppSegmentedOption {
  value: string
  label: string
}

defineProps<{
  label: string
  modelValue: string
  options: readonly AppSegmentedOption[]
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<template>
  <div role="group" :aria-label="label" class="inline-flex overflow-hidden rounded-full border border-outline">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :aria-pressed="option.value === modelValue ? 'true' : 'false'"
      class="md-state min-h-11 min-w-11 cursor-pointer px-4 text-label-large text-on-surface"
      :class="{ 'bg-secondary-container text-on-secondary-container': option.value === modelValue }"
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>
