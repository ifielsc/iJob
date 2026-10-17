<script setup lang="ts">
import { h, nextTick, reactive, ref } from 'vue'
import { NButton, NDrawer, NDrawerContent, NForm, NInput, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { ApiError, describeError } from '../../shared/api/errors'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import { X } from '@lucide/vue'
import type { ConfigRow } from './contracts'
import { useEditor } from '../../shared/forms/use-editor'
const props = defineProps<{ row: ConfigRow }>(),
  emit = defineEmits<{ close: []; sent: [] }>()
const { t } = useI18n(),
  runtime = useRuntime(),
  message = useMessage(),
  form = ref<HTMLElement | null>(null)
const draft = reactive({ subject: '', content: '' })
const { busy, submitted, close, focusError } = useEditor(
  () => draft,
  () => emit('close'),
)
const closing = ref(false)
async function requestClose() {
  if (busy.value || closing.value) return
  closing.value = true
  try {
    await close()
  } finally {
    closing.value = false
  }
}
async function send() {
  if (busy.value || runtime.accessIssue.value) return
  submitted.value = true
  if (!draft.subject.trim() || !draft.content.trim()) {
    await nextTick()
    focusError(form.value)
    return
  }
  busy.value = true
  try {
    const result = await runtime.request<{ status: string }>(
      '/api/notify-configs/' + props.row.id + '/test-send',
      { method: 'POST', body: { ...draft } },
    )
    if (result?.status !== 'DELIVERED') throw new ApiError('protocol')
    emit('sent')
  } catch (e) {
    message.error(() => h('span', { role: 'alert' }, describeError(e, t)), { duration: 5000 })
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <NDrawer
    class="notification-drawer"
    :theme-overrides="{ headerPadding: '12px 22px' }"
    :show="true"
    width="min(520px,100vw)"
    :mask-closable="false"
    :close-on-esc="true"
    @update:show="requestClose"
    ><NDrawerContent :native-scrollbar="true" :title="t('config.testSend') + ' · ' + row.name">
      <template #header
        ><div class="notification-editor-title">
          <h2>{{ t('config.testSend') }}</h2>
          <NButton
            class="notification-editor-close"
            quaternary
            circle
            :disabled="busy"
            :aria-label="t('common.close')"
            @click="requestClose"
            ><X :size="18"
          /></NButton></div
      ></template>
      <div ref="form" class="configuration-editor">
        <strong>{{ row.name }}</strong>
        <p class="configuration-note">{{ t('config.sendHint') }}</p>
        <p v-if="row.type !== 'EMAIL'" class="notification-protection">
          {{ t('config.rateTestHint') }}
        </p>
        <NForm :disabled="busy" :show-feedback="false" novalidate @submit.prevent="send">
          <FormItem
            id="test-subject"
            :label="t('config.subject')"
            required
            :missing="submitted && !draft.subject.trim()"
            ><NInput
              v-model:value="draft.subject"
              :maxlength="255"
              :input-props="{
                id: 'test-subject',
                'aria-invalid': (submitted && !draft.subject.trim()) || undefined,
              }"
          /></FormItem>
          <FormItem
            id="test-content"
            :label="t('config.content')"
            required
            :missing="submitted && !draft.content.trim()"
            ><NInput
              v-model:value="draft.content"
              type="textarea"
              :maxlength="10000"
              :rows="5"
              :input-props="{
                id: 'test-content',
                'aria-invalid': (submitted && !draft.content.trim()) || undefined,
              }"
          /></FormItem>
        </NForm>
        <p class="configuration-note">{{ t('config.testUnknown') }}</p>
      </div>
      <template #footer
        ><div class="configuration-footer">
          <NButton :disabled="busy" @click="requestClose">{{ t('common.cancel') }}</NButton
          ><NButton type="primary" :loading="busy" @click="send">{{ t('config.send') }}</NButton>
        </div></template
      >
    </NDrawerContent></NDrawer
  >
</template>
