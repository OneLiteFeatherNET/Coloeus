<script setup lang="ts">
import type { MaterialSymbolName } from '~/assets/icons/material-symbols'

// The primary navigation. Below the `lg` window size it is a bar at the bottom of the screen; from `lg` on it is a
// rail on the left. The items come in as props and each one is drawn by the default slot, so the block knows no router.
export interface AppNavigationItem {
  key: string
  label: string
  icon: MaterialSymbolName
  to: string
  active?: boolean
}

defineProps<{
  label: string
  items: readonly AppNavigationItem[]
}>()
</script>

<template>
  <nav
    :aria-label="label"
    class="fixed inset-x-0 bottom-0 z-10 border-t border-outline-variant bg-surface-container lg:static lg:w-24 lg:border-t-0 lg:border-e"
  >
    <ul class="flex justify-around gap-1 px-2 py-2 lg:flex-col lg:items-center lg:justify-start lg:gap-2 lg:py-4">
      <li v-for="item in items" :key="item.key" class="flex-1 lg:flex-none">
        <slot :item="item" />
      </li>
    </ul>
  </nav>
</template>
