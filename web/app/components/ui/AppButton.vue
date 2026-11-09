<script setup lang="ts">
import { computed } from 'vue'

// A Material Design 3 button with a 44 by 44 pixel touch target. Its text comes from the slot; it knows no route and
// no role. `filled` is the default; `tonal`, `outlined` and `text` are the lower-emphasis variants; `danger` is the
// filled error role for destructive actions. `pressed` turns the button into a toggle that reports aria-pressed.
export type AppButtonVariant = 'filled' | 'tonal' | 'outlined' | 'text' | 'danger'

const props = withDefaults(defineProps<{
  type?: 'button' | 'submit' | 'reset'
  variant?: AppButtonVariant
  disabled?: boolean
  pressed?: boolean
}>(), {
  type: 'button',
  variant: 'filled',
  disabled: false,
  pressed: undefined
})

const emit = defineEmits<{ click: [event: MouseEvent] }>()

const variantClasses = computed(() => {
  if (props.disabled) {
    return props.variant === 'text' ? 'text-on-surface/38' : 'bg-on-surface/12 text-on-surface/38'
  }
  switch (props.variant) {
    case 'tonal':
      return 'bg-secondary-container text-on-secondary-container'
    case 'outlined':
      return 'border border-outline text-primary'
    case 'text':
      return 'px-3 text-primary'
    case 'danger':
      return 'bg-error text-on-error'
    default:
      return 'bg-primary text-on-primary'
  }
})
</script>

<template>
  <button
    :type="type"
    :disabled="disabled"
    :aria-pressed="pressed === undefined ? undefined : pressed ? 'true' : 'false'"
    class="md-state inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center gap-2 rounded-full px-6 whitespace-nowrap text-label-large disabled:cursor-not-allowed"
    :class="[variantClasses, { 'aria-pressed:bg-secondary-container aria-pressed:text-on-secondary-container': pressed !== undefined && !disabled }]"
    @click="emit('click', $event)"
  >
    <slot />
  </button>
</template>
