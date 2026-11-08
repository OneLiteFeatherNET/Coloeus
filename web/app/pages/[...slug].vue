<script setup lang="ts">
import AppPageHeader from '~/components/ui/AppPageHeader.vue'
import { de } from '~/i18n/de'

useHead({ title: de.titles.notFound })
</script>

<template>
  <div class="flex flex-col gap-6">
  <AppPageHeader :title="de.app.notFoundTitle" :description="de.app.notFoundText" />
  <NuxtLink to="/" class="inline-flex min-h-11 items-center font-medium underline">
    {{ de.app.backToStart }}
  </NuxtLink>
  </div>
</template>
