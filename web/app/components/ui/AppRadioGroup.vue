<script setup lang="ts">
// A group of radio buttons under one legend. The options and the legend come in as props; the native inputs keep the
// keyboard behaviour of a radio group, and the choice is the modelValue.
export interface AppRadioOption {
  value: string
  label: string
}

const props = defineProps<{
  legend: string
  name: string
  modelValue: string
  options: readonly AppRadioOption[]
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<template>
  <fieldset class="flex flex-wrap gap-x-6 gap-y-2">
    <legend class="mb-1 text-label-large text-on-surface">
      {{ props.legend }}
    </legend>
    <label
      v-for="option in options"
      :key="option.value"
      class="inline-flex min-h-11 cursor-pointer items-center gap-3 text-body-large text-on-surface"
    >
      <input
        type="radio"
        class="size-5 shrink-0 accent-primary"
        :name="props.name"
        :value="option.value"
        :checked="props.modelValue === option.value"
        @change="emit('update:modelValue', option.value)"
      >
      <span>{{ option.label }}</span>
    </label>
  </fieldset>
</template>
