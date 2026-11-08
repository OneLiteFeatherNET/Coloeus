<script setup lang="ts">
// An empty list or result. The text is the default slot; the optional action is the `action` slot.
</script>

<template>
  <div class="flex flex-col items-start gap-3 rounded-lg border border-dashed border-(--c-border) p-6">
    <p class="text-(--c-muted)">
      <slot />
    </p>
    <slot name="action" />
  </div>
</template>
