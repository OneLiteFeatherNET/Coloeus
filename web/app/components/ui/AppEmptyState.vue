<script setup lang="ts">
// An empty list or result. The text is the default slot; the optional action is the `action` slot.
</script>

<template>
  <div class="flex flex-col items-start gap-3 rounded-md border border-dashed border-outline-variant p-6">
    <p class="text-body-large text-on-surface-variant">
      <slot />
    </p>
    <slot name="action" />
  </div>
</template>
