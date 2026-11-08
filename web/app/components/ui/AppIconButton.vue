<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import type { MaterialSymbolName } from '~/assets/icons/material-symbols'

// An icon-only button. It has no visible text, so the accessible name comes in as `label` and is also shown as a
// tooltip. The icon takes the text color of the button, which the caller may set through its class.
withDefaults(defineProps<{
  label: string
  icon: MaterialSymbolName
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
}>(), {
  type: 'button',
  disabled: false
})

const emit = defineEmits<{ click: [event: MouseEvent] }>()
</script>

<template>
  <button
    :type="type"
    :disabled="disabled"
    :aria-label="label"
    :title="label"
    class="md-state inline-flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-full disabled:cursor-not-allowed disabled:opacity-40"
    @click="emit('click', $event)"
  >
    <AppIcon :name="icon" />
  </button>
</template>
