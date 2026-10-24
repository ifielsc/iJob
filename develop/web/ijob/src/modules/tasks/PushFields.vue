<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { defineAsyncComponent, nextTick, ref } from 'vue'
import { NCollapse, NCollapseItem, NInput, NSelect } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import IntegerInput from '../../shared/ui/IntegerInput.vue'
import PushResourcePicker from './PushResourcePicker.vue'
import { pushLimits, type PushDraft, type PushLimit } from './push-model'
const SqlEditor = defineAsyncComponent(() => import('../../shared/ui/SqlEditor.vue'))
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
}>()
const draft = defineModel<PushDraft>('draft', { required: true })
const emit = defineEmits<{ select: [boolean]; expanded: [boolean] }>()
const { t } = useI18n(),
  root = ref<HTMLElement>(),
  expanded = ref<string[]>([]),
  sql = ref<{ focus: () => void }>()
const keys = Object.keys(pushLimits) as PushLimit[]
const error = (k: string) =>
  props.submitted && props.errors['push.' + k] ? t('push.' + props.errors['push.' + k]) : undefined
async function focusError(key: string) {
  if (keys.some((k) => key === 'push.' + k)) expanded.value = ['limits']
  await nextTick()
  if (key === 'push.sql') sql.value?.focus()
  else root.value?.querySelector<HTMLElement>('[id="task-' + key + '"]')?.focus()
}
defineExpose({ focusError })
</script>
<template>
  <section ref="root" class="push-fields">
    <p v-if="draft.blocked" role="alert">{{ t('push.unsupported') }}</p>
    <template v-else>
      <section class="transfer-group">
        <h3>{{ t('push.source') }}</h3>
        <FormItem
          id="task-push.sourceResourceId"
          :label="t('push.sourceResourceId')"
          required
          :invalid="!!error('sourceResourceId')"
          :message="error('sourceResourceId')"
        >
          <PushResourcePicker
            id="task-push.sourceResourceId"
            v-model:value="draft.sourceResourceId"
            type="DB"
            :label="t('push.sourceResourceId')"
            :disabled="disabled"
            @select="emit('select', $event)"
          />
        </FormItem>
        <FormItem
          id="task-push.sql"
          :label="t('push.sql')"
          required
          :invalid="!!error('sql')"
          :message="error('sql')"
        >
          <SqlEditor
            ref="sql"
            v-model="draft.sql"
            context-key="PUSH"
            :query-context="{ resourceId: draft.sourceResourceId, mode: 'PUSH' }"
            :disabled="disabled"
            :invalid="!!error('sql')"
            @expanded="emit('expanded', $event)"
          />
          <p class="task-note">{{ t('push.sqlHelp') }}</p>
        </FormItem>
      </section>
      <section class="transfer-group">
        <h3>{{ t('push.target') }}</h3>
        <div class="task-advanced-grid">
          <FormItem id="task-push.type" :label="t('push.type')" required
            ><NSelect
              v-model:value="draft.type"
              filterable
              :disabled="disabled"
              :options="[
                { value: 'KAFKA', label: 'Kafka' },
                { value: 'RABBITMQ', label: 'RabbitMQ' },
              ]"
              :input-props="{ id: 'task-push.type', 'aria-label': t('push.type') }"
              @update:show="emit('select', $event)"
          /></FormItem>
          <FormItem
            id="task-push.resourceId"
            :label="t('push.resourceId')"
            required
            :invalid="!!error('resourceId')"
            :message="error('resourceId')"
            ><PushResourcePicker
              id="task-push.resourceId"
              :key="draft.type"
              v-model:value="draft.targets[draft.type].resourceId"
              :type="draft.type"
              :label="t('push.resourceId')"
              :disabled="disabled"
              @select="emit('select', $event)"
          /></FormItem>
        </div>
        <FormItem
          v-if="draft.type === 'KAFKA'"
          id="task-push.topic"
          label="Topic"
          required
          :invalid="!!error('topic')"
          :message="error('topic')"
          ><NInput
            v-model:value="draft.targets.KAFKA.topic"
            :disabled="disabled"
            :input-props="{ id: 'task-push.topic' }"
        /></FormItem>
        <template v-else
          ><div class="task-advanced-grid">
            <FormItem
              v-for="key in ['exchange', 'routingKey'] as const"
              :id="'task-push.' + key"
              :key="key"
              :label="t('push.' + key)"
              :required="key === 'routingKey'"
              :invalid="!!error(key)"
              :message="error(key)"
              ><NInput
                v-model:value="draft.targets.RABBITMQ[key]"
                :disabled="disabled"
                :input-props="{ id: 'task-push.' + key }"
            /></FormItem>
          </div>
          <p class="task-note">{{ t('push.exchangeHelp') }}</p></template
        >
      </section>
      <section class="transfer-group">
        <h3>{{ t('push.format') }}</h3>
        <div class="task-advanced-grid">
          <FormItem id="task-push.serializer" :label="t('push.serializer')"
            ><NInput value="JSON" readonly :input-props="{ id: 'task-push.serializer' }"
          /></FormItem>
          <FormItem id="task-push.compression" :label="t('push.compression')"
            ><NSelect
              v-model:value="draft.compression"
              filterable
              :disabled="disabled"
              :options="[
                { value: 'none', label: t('push.none') },
                { value: 'gzip', label: 'GZIP' },
              ]"
              :input-props="{ id: 'task-push.compression', 'aria-label': t('push.compression') }"
              @update:show="emit('select', $event)"
          /></FormItem>
        </div>
        <p v-if="draft.compression === 'gzip'" class="task-note">{{ t('push.gzipHelp') }}</p>
      </section>
      <p class="task-note">{{ t('push.partialHelp') }}</p>
      <NCollapse v-model:expanded-names="expanded"
        ><NCollapseItem name="limits" :title="t('push.limits')"
          ><div class="task-advanced-grid">
            <FormItem
              v-for="key in keys"
              :id="'task-push.' + key"
              :key="key"
              :label="t('push.' + key)"
              :invalid="!!error(key)"
              :message="error(key)"
              ><IntegerInput
                :id="'task-push.' + key"
                v-model:value="draft.limits[key]"
                :label="t('push.' + key)"
                :disabled="disabled"
                :invalid="!!error(key)"
              />
              <p class="task-note">{{ pushLimits[key][1] }}–{{ pushLimits[key][2] }}</p></FormItem
            >
          </div>
          <p class="task-note">{{ t('push.limitsHelp') }}</p></NCollapseItem
        ></NCollapse
      >
    </template>
  </section>
</template>
<style scoped>
.push-fields {
  display: grid;
  gap: 16px;
  min-width: 0;
}
.transfer-group {
  display: grid;
  gap: 16px;
}
.transfer-group h3 {
  margin: 0;
  font-size: 13px;
  border-left: 2px solid var(--primary);
  padding-left: 8px;
}
.task-note {
  margin: 6px 0 0;
}
</style>
