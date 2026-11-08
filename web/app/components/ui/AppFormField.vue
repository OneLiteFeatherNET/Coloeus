<script setup lang="ts">
import { computed, useId } from 'vue'

// A labelled form control. The control gets the id and the `aria-describedby` through the slot; the hint and the
// error are linked to it. The texts come in as props.
const props = defineProps<{
  label: string
  hint?: string
  error?: string
}>()

const id = useId()
const hintId = useId()
const errorId = useId()

const describedBy = computed(() => {
  const ids: string[] = []
  if (props.hint) {
    ids.push(hintId)
  }
  if (props.error) {
    ids.push(errorId)
  }
  return ids.length > 0 ? ids.join(' ') : undefined
})
</script>

<template>
  <div class="flex flex-col gap-1">
    <label :for="id" class="font-medium">{{ label }}</label>
    <slot :id="id" :describedby="describedBy" :invalid="Boolean(error)" />
    <p v-if="hint" :id="hintId" class="text-sm text-(--c-muted)">
      {{ hint }}
    </p>
    <p v-if="error" :id="errorId" class="text-sm font-medium text-(--c-danger)">
      {{ error }}
    </p>
  </div>
</template>
