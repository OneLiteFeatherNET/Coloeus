<script setup lang="ts">
// An on/off switch: a checkbox with the switch role, drawn by the md-switch class. The label is a prop and names it.
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
  <label class="inline-flex min-h-11 cursor-pointer items-center gap-3 text-body-large text-on-surface has-disabled:cursor-not-allowed">
    <input
      type="checkbox"
      role="switch"
      class="md-switch"
      :checked="modelValue"
      :disabled="disabled"
      @change="onChange"
    >
    <span>{{ label }}</span>
  </label>
</template>
