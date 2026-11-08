<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import type { MaterialSymbolName } from '~/assets/icons/material-symbols'

// A chip marks one short fact, such as a status or a tag. The text is the default slot; the optional icon is decorative.
defineProps<{ icon?: MaterialSymbolName }>()
</script>

<template>
  <span class="inline-flex min-h-7 items-center gap-1 rounded-sm border border-outline px-3 py-1 text-label-large text-on-surface-variant">
    <AppIcon v-if="icon" :name="icon" class="size-4" />
    <slot />
  </span>
</template>
