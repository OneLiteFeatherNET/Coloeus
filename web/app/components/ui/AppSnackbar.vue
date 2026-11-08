<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import AppIconButton from './AppIconButton.vue'

// A snackbar for a success message. The live region is always in the page, so that the first message is announced;
// the message and the dismiss label come in as props. The snackbar sits above the navigation bar on narrow screens.
defineProps<{
  message?: string
  dismissLabel: string
}>()

const emit = defineEmits<{ dismiss: [] }>()
</script>

<template>
  <div
    role="status"
    aria-live="polite"
    class="pointer-events-none fixed inset-x-0 bottom-20 z-20 flex justify-center px-4 lg:bottom-6"
  >
    <div
      v-if="message"
      class="pointer-events-auto flex max-w-xl items-center gap-3 rounded-xs bg-inverse-surface py-2 ps-4 pe-2 text-body-medium text-inverse-on-surface shadow-elevation-3"
    >
      <AppIcon name="check-circle" class="size-5 shrink-0" />
      <p class="min-w-0 flex-1 py-2">
        {{ message }}
      </p>
      <AppIconButton :label="dismissLabel" icon="close" class="text-inverse-on-surface" @click="emit('dismiss')" />
    </div>
  </div>
</template>
