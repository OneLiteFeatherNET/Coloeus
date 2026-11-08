<script setup lang="ts">
// A card holds a related group of content. `elevated` lifts it with a shadow on a surface container, `filled` uses
// the highest container tone, `outlined` draws an outline on the surface. The tag defaults to a div.
withDefaults(defineProps<{
  variant?: 'elevated' | 'filled' | 'outlined'
  tag?: string
}>(), {
  variant: 'elevated',
  tag: 'div'
})
</script>

<template>
  <component
    :is="tag"
    class="flex flex-col gap-3 rounded-md p-4"
    :class="{
      'bg-surface-container-low shadow-elevation-1': variant === 'elevated',
      'bg-surface-container-highest': variant === 'filled',
      'border border-outline-variant bg-surface': variant === 'outlined'
    }"
  >
    <slot />
  </component>
</template>
