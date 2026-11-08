<script setup lang="ts">
import { de } from '~/i18n/de'

// The layout of the login page: no navigation, because nobody is signed in there.
</script>

<template>
  <div class="flex min-h-dvh flex-col">
    <a href="#main" class="sr-only-focusable rounded-lg bg-(--c-accent) px-4 py-2 text-(--c-accent-text)">
      {{ de.app.skipToContent }}
    </a>
    <main id="main" tabindex="-1" class="mx-auto flex w-full max-w-xl flex-1 flex-col justify-center gap-6 px-4 py-10">
      <slot />
    </main>
  </div>
</template>
