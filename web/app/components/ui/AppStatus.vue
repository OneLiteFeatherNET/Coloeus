<script setup lang="ts">
// A status message in a polite live region. The region exists even when empty, so that screen readers announce
// the first message. The text comes in as the message prop. Error messages use the error container roles.
defineProps<{
  message?: string
  tone?: 'info' | 'success' | 'error'
}>()
</script>

<template>
  <div aria-live="polite" role="status" class="min-h-0">
    <p
      v-if="message"
      class="rounded-sm px-4 py-3 text-body-medium"
      :class="{
        'bg-tertiary-container text-on-tertiary-container': tone === 'success',
        'bg-error-container text-on-error-container': tone === 'error',
        'bg-surface-container-high text-on-surface': !tone || tone === 'info'
      }"
    >
      {{ message }}
    </p>
  </div>
</template>
