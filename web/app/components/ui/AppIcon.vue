<script setup lang="ts">
import { computed } from 'vue'
import { materialSymbols, type MaterialSymbolName } from '~/assets/icons/material-symbols'

// A Material Symbol as inline SVG, colored by the current text color. It is decorative: the text next to it, or the
// accessible name of its button, says what it means, so the icon itself is hidden from assistive technology.
const props = defineProps<{ name: MaterialSymbolName }>()

const icon = computed(() => materialSymbols[props.name])
</script>

<template>
  <svg
    aria-hidden="true"
    focusable="false"
    class="inline-block size-6 shrink-0 fill-current"
    :viewBox="`0 0 ${icon.width} ${icon.height}`"
  >
    <path v-for="(d, index) in icon.paths" :key="index" :d="d" />
  </svg>
</template>
