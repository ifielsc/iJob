<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, ref } from 'vue'
import { NInputNumber, NSwitch } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import ApiCallFields from './ApiCallFields.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import JsonFormEditor from '../../shared/ui/JsonFormEditor.vue'
import type { ApiDraft } from './api-call-model'
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
}>()
const draft = defineModel<ApiDraft>('draft', { required: true })
const emit = defineEmits<{ expanded: [boolean]; select: [boolean] }>()
const { t } = useI18n()
const root = ref<HTMLElement>()
const bodyEditor = ref<{ focus: () => void }>()
defineExpose({
  focusError: (key: string) => {
    if (key === 'remoteStop.body') bodyEditor.value?.focus()
    else
      root.value?.querySelector<HTMLElement>('[id="task-' + key.replace('.', '-') + '"]')?.focus()
  },
})
const request = computed(() => ({
  ...draft.value,
  http: draft.value.remoteStop.http,
  soap: draft.value.remoteStop.soap,
}))
const errors = computed(() =>
  Object.fromEntries(
    Object.entries(props.errors)
      .filter(([key]) => key.startsWith('remoteStop.'))
      .map(([key, value]) => [key.substring(11), value]),
  ),
)
const error = (key: string) =>
  props.submitted && errors.value[key] ? t('apiCall.' + errors.value[key]) : undefined
</script>
<template>
  <section ref="root" class="remote-stop-fields">
    <div class="task-inline">
      <label for="task-remoteStop-enabled">{{ t('apiCall.remoteStop') }}</label>
      <NSwitch
        id="task-remoteStop-enabled"
        v-model:value="draft.remoteStop.enabled"
        :disabled="disabled"
      />
    </div>
    <p class="task-note">{{ t('apiCall.remoteStopHelp') }}</p>
    <template v-if="draft.remoteStop.enabled">
      <p class="task-note">
        {{ t('apiCall.remoteStopCorrelation') }} <code v-pre>${ijob.apiCall.operationId}</code>
      </p>
      <ApiCallFields
        :draft="request"
        :errors="errors"
        :submitted="submitted"
        :disabled="disabled"
        id-prefix="task-remoteStop"
        control-request
        @select="emit('select', $event)"
      />
      <FormItem
        v-if="draft.protocol !== 'WEB_SERVICE'"
        id="task-remoteStop-body"
        :label="t('apiCall.body')"
        :invalid="!!error('body')"
        :message="error('body')"
      >
        <JsonFormEditor
          ref="bodyEditor"
          v-model="draft.remoteStop.http.body"
          :title="t('apiCall.remoteStopBody')"
          :disabled="disabled"
          :invalid="!!error('body')"
          context-key="API_CALL_REMOTE_STOP"
          @expanded="emit('expanded', $event)"
          @select="emit('select', $event)"
        />
      </FormItem>
      <FormItem
        id="task-remoteStop-timeoutSeconds"
        :label="t('apiCall.remoteStopTimeout')"
        :invalid="!!error('timeoutSeconds')"
        :message="error('timeoutSeconds')"
      >
        <NInputNumber
          v-model:value="draft.remoteStop.timeoutSeconds"
          :disabled="disabled"
          :min="1"
          :max="30"
          :precision="0"
          :input-props="{ id: 'task-remoteStop-timeoutSeconds' }"
        />
      </FormItem>
    </template>
  </section>
</template>
<style scoped>
.remote-stop-fields {
  display: grid;
  gap: 16px;
  min-width: 0;
  border-top: 1px solid var(--border);
  padding-top: 16px;
}
.remote-stop-fields code {
  overflow-wrap: anywhere;
}
</style>
