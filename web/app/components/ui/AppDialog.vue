<script setup lang="ts">
import { nextTick, ref, useId, watch } from 'vue'

// A modal dialog on the native <dialog> element: the browser traps focus and closes on Escape. The element that
// opened the dialog gets the focus back when it closes. Texts come in as props and slots.
const props = defineProps<{
  open: boolean
  title: string
  description?: string
}>()

const emit = defineEmits<{ close: [] }>()

const dialog = ref<HTMLDialogElement | null>(null)
const titleId = useId()
const descriptionId = useId()
let opener: HTMLElement | null = null

watch(() => props.open, async (open) => {
  await nextTick()
  const element = dialog.value
  if (!element) {
    return
  }
  if (open) {
    opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    if (typeof element.showModal === 'function') {
      element.showModal()
    } else {
      element.setAttribute('open', '')
    }
  } else {
    if (typeof element.close === 'function') {
      element.close()
    } else {
      element.removeAttribute('open')
    }
    opener?.focus()
    opener = null
  }
})

function onCancel(event: Event) {
  event.preventDefault()
  emit('close')
}
</script>

<template>
  <dialog
    ref="dialog"
    class="m-auto w-[min(100%-2rem,36rem)] rounded-xl border border-(--c-border) bg-(--c-surface) p-0 text-(--c-text) backdrop:bg-black/50"
    :aria-labelledby="titleId"
    :aria-describedby="description ? descriptionId : undefined"
    @cancel="onCancel"
  >
    <div class="flex flex-col gap-4 p-6">
      <h2 :id="titleId" class="text-xl font-semibold">
        {{ title }}
      </h2>
      <p v-if="description" :id="descriptionId" class="text-(--c-muted)">
        {{ description }}
      </p>
      <slot />
    </div>
    <div v-if="$slots.footer" class="flex flex-wrap justify-end gap-3 border-t border-(--c-border) px-6 py-4">
      <slot name="footer" />
    </div>
  </dialog>
</template>
