<script setup lang="ts">
import { computed, useId } from 'vue'
import AppIcon from './AppIcon.vue'

// A labelled form control in an outlined text field style. The control gets the id, the `aria-describedby` and the
// invalid state through the slot; the supporting text and the error text are linked to it. The texts come in as props.
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
    <label :for="id" class="text-label-large text-on-surface">{{ label }}</label>
    <slot :id="id" :describedby="describedBy" :invalid="Boolean(error)" />
    <p v-if="hint" :id="hintId" class="text-body-small text-on-surface-variant">
      {{ hint }}
    </p>
    <p v-if="error" :id="errorId" class="flex items-start gap-1 text-body-small text-error">
      <AppIcon name="error" class="size-4" />
      <span>{{ error }}</span>
    </p>
  </div>
</template>
