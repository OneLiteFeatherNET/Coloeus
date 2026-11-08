<script setup lang="ts">
// A button with a 44 by 44 pixel touch target. Its text comes from the slot; it knows no route and no role.
withDefaults(defineProps<{
  type?: 'button' | 'submit' | 'reset'
  variant?: 'primary' | 'secondary' | 'danger'
  disabled?: boolean
  pressed?: boolean
}>(), {
  type: 'button',
  variant: 'primary',
  disabled: false,
  pressed: undefined
})

const emit = defineEmits<{ click: [event: MouseEvent] }>()
</script>

<template>
  <button
    :type="type"
    :disabled="disabled"
    :aria-pressed="pressed"
    class="inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-lg border px-4 py-2 font-medium disabled:cursor-not-allowed"
    :class="{
      'border-transparent bg-(--c-accent) text-(--c-accent-text)': variant === 'primary' && !disabled,
      'border-(--c-border) bg-(--c-surface) text-(--c-text)': variant === 'secondary' && !disabled,
      'border-transparent bg-(--c-danger) text-(--c-accent-text)': variant === 'danger' && !disabled,
      'border-(--c-border) bg-(--c-disabled-bg) text-(--c-disabled)': disabled,
      'aria-pressed:bg-(--c-accent) aria-pressed:text-(--c-accent-text)': pressed !== undefined
    }"
    @click="emit('click', $event)"
  >
    <slot />
  </button>
</template>
