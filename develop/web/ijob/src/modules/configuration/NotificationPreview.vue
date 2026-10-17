<script setup lang="ts">
import { computed, ref, shallowRef, watch, onBeforeUnmount } from 'vue'
import { NButton, NDrawer, NDrawerContent } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import { ApiError } from '../../shared/api/errors'
import { notificationLabel } from './notification-channels'
import { parsePreview, type PreviewOptions, type PreviewContent } from './notification-preview'
import PreviewMarkdown from './PreviewMarkdown.vue'
const props = defineProps<{ options: PreviewOptions }>()
defineEmits<{ close: [] }>()
const { t } = useI18n(),
  runtime = useRuntime()
const source = ref('check'),
  content = shallowRef<PreviewContent>(),
  error = shallowRef<unknown>(),
  loading = ref(false)
let request: AbortController | undefined
const sources = computed(() =>
  ['task', 'job', 'check', 'monitor'].map((value) => ({
    value,
    label: t('config.previewSource_' + value),
  })),
)
watch(
  () => [source.value, props.options, runtime.accessIssue.value] as const,
  async () => {
    request?.abort()
    content.value = undefined
    error.value = undefined
    if (runtime.accessIssue.value) {
      loading.value = false
      return
    }
    const current = new AbortController()
    request = current
    loading.value = true
    const selectedSource = source.value,
      selectedType = props.options.type
    try {
      const result = parsePreview(
        await runtime.request('/api/notification-preview', {
          method: 'POST',
          body: { ...props.options, source: selectedSource },
          signal: current.signal,
        }),
      )
      if (result.type !== selectedType || result.source !== selectedSource)
        throw new ApiError('protocol')
      if (!current.signal.aborted) content.value = result
    } catch (e) {
      if (!current.signal.aborted) error.value = e
    } finally {
      if (!current.signal.aborted) loading.value = false
    }
  },
  { immediate: true, deep: true },
)
onBeforeUnmount(() => request?.abort())
</script>
<template>
  <NDrawer
    :show="true"
    width="min(640px,100vw)"
    class="configuration-drawer notification-preview-drawer"
    @update:show="$emit('close')"
  >
    <NDrawerContent :title="t('config.previewContent')" closable :native-scrollbar="true">
      <FormItem id="notify-preview-source" :label="t('config.notificationSource')">
        <CompactSelect
          id="notify-preview-source"
          v-model:value="source"
          :label="t('config.notificationSource')"
          :options="sources"
        />
      </FormItem>
      <p class="configuration-note">
        {{ t(notificationLabel(options.type)) }} · {{ t('config.sampleContent') }}
      </p>
      <RequestError :error="error" />
      <p v-if="loading" role="status">{{ t('common.loading') }}</p>
      <template v-if="content">
        <p class="notification-preview-subject">
          <strong>{{ t('config.subject') }}：</strong>{{ content.subject }}
        </p>
        <iframe
          v-if="content.html"
          class="notification-preview-email"
          :title="t('config.previewContent')"
          sandbox=""
          referrerpolicy="no-referrer"
          :srcdoc="content.html"
        />
        <article v-else class="notification-preview-card" :data-channel="content.type">
          <template v-for="(part, i) in content.parts" :key="i">
            <h3 v-if="part.kind === 'heading'" class="notification-preview-heading">
              {{ part.text }}
            </h3>
            <PreviewMarkdown v-else-if="part.kind === 'markdown'" :text="part.text" />
            <p v-else class="notification-preview-plain">{{ part.text }}</p>
          </template>
          <p v-if="content.atAll || content.mentions.length" class="notification-preview-mentions">
            <span v-if="content.atAll">@{{ t('config.atAll') }} </span
            ><span v-for="mention in content.mentions" :key="mention">@{{ mention }} </span>
          </p>
        </article>
      </template>
      <p class="configuration-note">{{ t('config.notificationPreviewHint') }}</p>
      <template #footer
        ><NButton @click="$emit('close')">{{ t('common.close') }}</NButton></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
<style scoped>
.notification-preview-subject {
  margin: 16px 0 12px;
  overflow-wrap: anywhere;
}
.notification-preview-card {
  margin: 12px 0;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--panel);
  overflow-wrap: anywhere;
}
.notification-preview-heading {
  margin: 0 0 16px;
  font-size: 16px;
}
.notification-preview-plain {
  margin: 0 0 12px;
  white-space: pre-wrap;
  line-height: 1.7;
}
.notification-preview-email {
  display: block;
  width: 100%;
  height: 610px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: white;
}
.notification-preview-mentions {
  color: var(--primary);
  margin: 12px 0 0;
}
.notification-preview-card[data-channel='FEISHU'] {
  border-top: 4px solid var(--primary);
}
.notification-preview-card[data-channel='DISCORD'] {
  border-left: 4px solid var(--primary);
}
</style>
