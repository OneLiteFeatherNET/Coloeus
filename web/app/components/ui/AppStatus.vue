<script setup lang="ts">
// A status message in a polite live region. The region exists even when empty, so that screen readers announce
// the first message. The text comes in as the message prop.
defineProps<{
  message?: string
  tone?: 'info' | 'success' | 'error'
}>()
</script>

<template>
  <div aria-live="polite" role="status" class="min-h-0">
    <p
      v-if="message"
      class="rounded-lg border px-4 py-3 font-medium"
      :class="{
        'border-(--c-success) bg-(--c-success-bg) text-(--c-success)': tone === 'success',
        'border-(--c-danger) bg-(--c-danger-bg) text-(--c-danger)': tone === 'error',
        'border-(--c-border) bg-(--c-surface) text-(--c-text)': !tone || tone === 'info'
      }"
    >
      {{ message }}
    </p>
  </div>
</template>
