<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppButton from '~/components/ui/AppButton.vue'
import AppDialog from '~/components/ui/AppDialog.vue'
import AppFormField from '~/components/ui/AppFormField.vue'
import AppStatus from '~/components/ui/AppStatus.vue'
import { de } from '~/i18n/de'
import type { ClientView, RegisterClientRequest } from '~/types/api'
import { errorMessage } from '~/utils/errors'
import { createSubmitKey } from '~/utils/idempotency'
import { checkDisplayName, isUuid } from '~/utils/validation'
import { useStaff } from '~/composables/useStaff'

// Registers a client (a server) with its UUID and display name. Grants are set in a second step.
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [], done: [client: ClientView] }>()

const staff = useStaff()
const submitKey = createSubmitKey()
const clientId = ref('')
const displayName = ref('')
const busy = ref(false)
const failure = ref('')

watch(() => props.open, (open) => {
  if (open) {
    clientId.value = ''
    displayName.value = ''
    failure.value = ''
    submitKey.reset()
  }
})

const idError = computed(() => clientId.value === '' || isUuid(clientId.value) ? undefined : de.clients.clientIdInvalid)
const nameError = computed(() => displayName.value === '' || checkDisplayName(displayName.value) ? undefined : de.clients.displayNameInvalid)
const valid = computed(() => clientId.value !== '' && idError.value === undefined
  && displayName.value.trim() !== '' && nameError.value === undefined)

async function submit() {
  if (!valid.value || busy.value) {
    return
  }
  const body: RegisterClientRequest = {
    clientId: clientId.value.toLowerCase(),
    displayName: displayName.value.trim()
  }
  busy.value = true
  failure.value = ''
  try {
    const client = await staff.api.registerClient(body, submitKey.forRequest(JSON.stringify(body)))
    submitKey.reset()
    staff.announce(de.clients.registered)
    emit('done', client)
    emit('close')
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AppDialog :open="open" :title="de.clients.registerTitle" @close="emit('close')">
    <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.clients.clientId" :hint="de.clients.clientIdHint" :error="idError">
        <input
          :id="id"
          v-model="clientId"
          type="text"
          autocomplete="off"
          :aria-invalid="invalid"
          :aria-describedby="describedby"
          class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3 font-mono"
        >
      </AppFormField>
      <AppFormField v-slot="{ id, describedby, invalid }" :label="de.clients.displayName" :hint="de.clients.displayNameHint" :error="nameError">
        <input
          :id="id"
          v-model="displayName"
          type="text"
          maxlength="200"
          :aria-invalid="invalid"
          :aria-describedby="describedby"
          class="min-h-11 rounded-lg border border-(--c-border) bg-(--c-surface) px-3"
        >
      </AppFormField>
      <AppStatus v-if="failure" :message="failure" tone="error" />
      <div class="flex flex-wrap justify-end gap-3">
        <AppButton variant="outlined" @click="emit('close')">
          {{ de.common.cancel }}
        </AppButton>
        <AppButton type="submit" :disabled="!valid || busy">
          {{ busy ? de.common.submitting : de.clients.register }}
        </AppButton>
      </div>
    </form>
  </AppDialog>
</template>
