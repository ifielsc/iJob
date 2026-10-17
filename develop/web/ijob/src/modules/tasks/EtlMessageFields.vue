<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NCollapse, NCollapseItem, NInput, NSelect } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { record } from './model'
import EtlField from './EtlField.vue'
import EtlTable from './EtlTable.vue'
import { messageFields, type EtlDraft } from './etl-model'
const props = defineProps<{
  mode: 'source' | 'limits'
  disabled: boolean
  errors: Record<string, string>
  submitted: boolean
}>()
const draft = defineModel<EtlDraft>('draft', { required: true })
const emit = defineEmits<{ select: [boolean] }>()
const { t } = useI18n(),
  runtime = useRuntime()
const vhost = ref(''),
  resourceError = ref(false)
watch(
  () => [draft.value.source.type, draft.value.source.resourceId],
  async ([type, id], _, cleanup) => {
    vhost.value = ''
    resourceError.value = false
    if (type !== 'RABBITMQ' || !id || props.mode !== 'source') return
    const controller = new AbortController()
    cleanup(() => controller.abort())
    try {
      const response = record(
        await runtime.request('/api/resources/' + id, { signal: controller.signal }),
      )
      const data = response,
        config = record(data.config)
      if (!controller.signal.aborted && data.type === 'RABBITMQ')
        vhost.value = String(config.virtualHost ?? '')
    } catch {
      if (!controller.signal.aborted) resourceError.value = true
    }
  },
  { immediate: true },
)
const metadata = computed(() =>
  (draft.value.source.type === 'KAFKA'
    ? ['topic', 'partition', 'offset', 'key', 'timestamp', 'headers']
    : ['queue', 'exchange', 'routingKey', 'messageId', 'timestamp', 'headers', 'redelivered']
  ).map((value) => ({ label: value, value })),
)
const expanded = ref<string[]>([])
watch(
  () => [props.submitted, props.errors],
  () => {
    if (props.submitted && props.errors['etl.message.metadataColumns'])
      expanded.value = [...new Set([...expanded.value, 'metadata'])]
    if (props.submitted && messageFields.slice(5).some((f) => props.errors['etl.message.' + f.key]))
      expanded.value = [...new Set([...expanded.value, 'capacity'])]
  },
  { deep: true, immediate: true },
)
const error = (key: string) =>
  props.submitted && props.errors['etl.message.' + key]
    ? t('etl.' + props.errors['etl.message.' + key])
    : undefined
</script>
<template>
  <template v-if="mode === 'source'">
    <div v-if="draft.source.type === 'RABBITMQ'" class="message-vhost">
      <label>{{ t('etl.messageVhost') }}</label>
      <NInput
        :value="vhost"
        readonly
        :placeholder="t('etl.messageNoResource')"
        :input-props="{ 'aria-label': t('etl.messageVhost') }"
      />
      <p v-if="resourceError" class="etl-error">{{ t('etl.messageResourceError') }}</p>
    </div>
    <h3>{{ t('etl.messageFormat') }}</h3>
    <div class="task-advanced-grid">
      <div>
        <label>{{ t('etl.messageFormat') }}</label
        ><NInput value="JSON" readonly :input-props="{ 'aria-label': t('etl.messageFormat') }" />
      </div>
      <div>
        <label>{{ t('etl.messageEncoding') }}</label
        ><NInput value="UTF-8" readonly :input-props="{ 'aria-label': t('etl.messageEncoding') }" />
      </div>
    </div>
    <p class="etl-info">{{ t('etl.messageJsonHelp') }}</p>
    <NCollapse v-model:expanded-names="expanded">
      <NCollapseItem
        data-etl-key="message.metadataColumns"
        name="metadata"
        :title="t('etl.messageMetadata')"
      >
        <p class="task-note">{{ t('etl.messageMetadataHelp') }}</p>
        <EtlTable
          :title="t('etl.metadataColumns')"
          :count="draft.metadataColumns.length"
          :add-label="t('etl.addColumn')"
          :disabled="disabled"
          :columns="[
            { key: 'key', label: t('etl.messageMetaKey') },
            { key: 'field', label: t('etl.messageMetaField') },
            { key: 'actions', label: t('common.actions'), width: '70px' },
          ]"
          @add="draft.metadataColumns.push({ key: '', field: '' })"
        >
          <tr v-for="(row, index) in draft.metadataColumns" :key="index">
            <td>
              <NSelect
                v-model:value="row.key"
                :options="metadata"
                :disabled="disabled"
                :input-props="{ 'aria-label': t('etl.messageMetaKey') + ' ' + (index + 1) }"
                @update:show="emit('select', $event)"
              />
            </td>
            <td>
              <NInput
                v-model:value="row.field"
                :disabled="disabled"
                :input-props="{ 'aria-label': t('etl.messageMetaField') + ' ' + (index + 1) }"
              />
            </td>
            <td>
              <NButton
                text
                type="error"
                :disabled="disabled"
                @click="draft.metadataColumns.splice(index, 1)"
                >{{ t('tasks.delete') }}</NButton
              >
            </td>
          </tr>
        </EtlTable>
        <p v-if="error('metadataColumns')" class="etl-error" role="alert">
          {{ error('metadataColumns') }}
        </p>
      </NCollapseItem>
    </NCollapse>
  </template>
  <template v-else>
    <h3>{{ t('etl.messageLimits') }}</h3>
    <div class="task-advanced-grid">
      <EtlField
        v-for="field in messageFields.slice(0, 3)"
        :key="field.key"
        :field="field"
        section="message"
        :value="draft.message[field.key]"
        :disabled="disabled"
        :error="error(field.key)"
        @update:value="draft.message[field.key] = $event"
      />
    </div>
    <p class="task-note">{{ t('etl.messageEndHelp') }}</p>
    <h3>{{ t('etl.messageBatch') }}</h3>
    <div class="task-advanced-grid">
      <EtlField
        v-for="field in messageFields.slice(3, 5)"
        :key="field.key"
        :field="field"
        section="message"
        :value="draft.message[field.key]"
        :disabled="disabled"
        :error="error(field.key)"
        @update:value="draft.message[field.key] = $event"
      />
    </div>
    <p class="task-note">{{ t('etl.messageFlushHelp') }}</p>
    <h3>{{ t('etl.messageFailure') }}</h3>
    <NInput
      :value="t('etl.messageFailurePolicy')"
      readonly
      :input-props="{ 'aria-label': t('etl.messageFailure') }"
    />
    <p class="etl-info">{{ t('etl.messageConfirmHelp') }}</p>
    <NCollapse v-model:expanded-names="expanded">
      <NCollapseItem name="capacity" :title="t('etl.messageCapacity')">
        <div class="task-advanced-grid">
          <EtlField
            v-for="field in messageFields.slice(5)"
            :key="field.key"
            :field="field"
            section="message"
            :value="draft.message[field.key]"
            :disabled="disabled"
            :error="error(field.key)"
            @update:value="draft.message[field.key] = $event"
          />
        </div>
      </NCollapseItem>
    </NCollapse>
    <p class="task-note">{{ t('etl.messageTargetHelp') }}</p>
  </template>
</template>
<style scoped>
label {
  display: block;
  margin-bottom: 6px;
}
.message-vhost {
  margin: 16px 0;
}
</style>
