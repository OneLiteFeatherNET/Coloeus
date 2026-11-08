<script setup lang="ts">
// A checkbox on the native input, so that keyboard and screen readers work as usual, colored by the primary role.
// The label is a prop; the control is nested in its label, which names it.
defineProps<{
  modelValue: boolean
  label: string
  disabled?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

function onChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).checked)
}
</script>

<template>
  <label class="inline-flex min-h-11 cursor-pointer items-center gap-3 text-body-large text-on-surface has-disabled:cursor-not-allowed has-disabled:opacity-40">
    <input
      type="checkbox"
      class="size-5 shrink-0 accent-primary"
      :checked="modelValue"
      :disabled="disabled"
      @change="onChange"
    >
    <span>{{ label }}</span>
  </label>
</template>
