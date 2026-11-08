<script setup lang="ts">
// The heading of a page with an optional introduction and actions. The title is a prop; no text is its own.
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
    <div class="flex flex-col gap-1">
      <h1 class="text-2xl font-bold">
        {{ title }}
      </h1>
      <p v-if="description" class="text-(--c-muted)">
        {{ description }}
      </p>
    </div>
    <div v-if="$slots.actions" class="flex flex-wrap gap-3">
      <slot name="actions" />
    </div>
  </header>
</template>
