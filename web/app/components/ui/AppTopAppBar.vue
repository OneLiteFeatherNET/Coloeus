<script setup lang="ts">
// The top app bar of a page: the title or brand in the default slot, and actions on the right in the `actions` slot.
// It wraps on narrow screens, so nothing is cut off at 360 pixels.
</script>

<template>
  <header class="sticky top-0 z-10 flex min-h-16 flex-wrap items-center justify-between gap-3 bg-surface px-4 py-2 text-on-surface md:px-6">
    <div class="flex min-w-0 items-center gap-3">
      <slot />
    </div>
    <div v-if="$slots.actions" class="flex flex-wrap items-center gap-3 text-body-medium">
      <slot name="actions" />
    </div>
  </header>
</template>
