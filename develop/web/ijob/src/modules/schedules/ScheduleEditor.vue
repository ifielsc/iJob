<script setup lang="ts">
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import {
  NButton,
  NCollapse,
  NCollapseItem,
  NDrawer,
  NDrawerContent,
  NForm,
  NInput,
  NInputNumber,
  NRadio,
  NRadioGroup,
  NSwitch,
  useMessage,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { describeError, isWriteOutcomeUnknown } from '../../shared/api/errors'
import { useEditor } from '../../shared/forms/use-editor'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import Select from '../../shared/ui/CompactSelect.vue'
import DateTimePicker from '../../shared/ui/DateTimePicker.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import { ParameterFormEditor } from '../parameters/public/components'
import { stringify } from 'lossless-json'
import EntityPicker from './EntityPicker.vue'
import SchedulePreview from './SchedulePreview.vue'
import { draftOf, issues, body, types, policies, type Schedule, type Metadata } from './model'
const props = defineProps<{ row?: Schedule; metadata: Metadata }>(),
  emit = defineEmits<{ close: []; saved: [] }>()
const runtime = useRuntime(),
  { t } = useI18n(),
  message = useMessage(),
  draft = reactive(draftOf(props.row)),
  root = ref<HTMLElement | null>(null),
  expanded = ref<string[]>(['variables']),
  selectOpen = ref(false),
  large = ref(false),
  closing = ref(false)
const uncertain = ref(false)
const parameterEditor = ref<InstanceType<typeof ParameterFormEditor> | null>(null)
const { changed, busy, error, submitted, close, focusError } = useEditor(
  () => draft,
  () => emit('close'),
  () => parameterEditor.value?.hasUnpublishedInput ?? false,
)
const problems = computed(() => issues(draft, props.metadata)),
  fieldError = (key: string) =>
    submitted.value && problems.value[key] ? t('schedules.' + problems.value[key]) : undefined
const controller = new AbortController()
function changeTarget(value: string | number) {
  draft.targetType = value as 'TASK' | 'JOB'
  draft.targetId = ''
}
async function requestClose() {
  if (closing.value) return
  closing.value = true
  try {
    await close()
  } finally {
    closing.value = false
  }
}
async function save() {
  if (props.row && !changed.value) return
  if (busy.value || uncertain.value || runtime.accessIssue.value) return
  submitted.value = true
  error.value = undefined
  try {
    if (!parameterEditor.value) return
    const values = parameterEditor.value.read()
    draft.variablesText = stringify(values.variables)!
    draft.parameterForm = values.parameterForm
  } catch {
    message.error(t('parameters.invalid'))
    expanded.value = ['variables', 'advanced']
    return
  }
  if (Object.keys(problems.value).length) {
    expanded.value = ['variables', 'advanced']
    await nextTick()
    focusError(root.value)
    return
  }
  busy.value = true
  try {
    await runtime.request('/api/schedules' + (props.row ? '/' + props.row.id : ''), {
      method: props.row ? 'PUT' : 'POST',
      body: body(draft),
      lossless: true,
      signal: controller.signal,
    })
    if (!controller.signal.aborted && !runtime.accessIssue.value) emit('saved')
  } catch (e) {
    if (!controller.signal.aborted) {
      if (isWriteOutcomeUnknown(e)) uncertain.value = true
      error.value = e
      message.error(() => h('span', { role: 'alert' }, describeError(e, t)))
    }
  } finally {
    busy.value = false
  }
}
onBeforeUnmount(() => {
  controller.abort()
  draft.variablesText = ''
})
</script>
<template>
  <NDrawer
    show
    width="min(520px, 100vw)"
    class="schedule-drawer editor-drawer"
    :mask-closable="false"
    :close-on-esc="!busy && !selectOpen && !large"
    @update:show="requestClose"
  >
    <NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="schedule-drawer-heading">
          <h2>
            {{ t(row ? 'schedules.edit' : 'schedules.new') }}<span v-if="row"> #{{ row.id }}</span>
          </h2>
          <DrawerCloseButton :disabled="busy" @close="requestClose" /></div
      ></template>
      <div ref="root">
        <p v-if="uncertain" role="alert">{{ t('eventBindings.unknown') }}</p>
        <p class="schedule-note">{{ t('schedules.zone') }}: {{ metadata.zoneId }}</p>
        <NForm
          :disabled="busy || uncertain"
          :show-feedback="false"
          novalidate
          @submit.prevent="save"
        >
          <div class="schedule-columns">
            <FormItem id="schedule-targetType" :label="t('schedules.targetType')" required
              ><Select
                id="schedule-targetType"
                :value="draft.targetType"
                :label="t('schedules.targetType')"
                :options="
                  ['TASK', 'JOB'].map((value) => ({ value, label: t('schedules.' + value) }))
                "
                @update:value="changeTarget"
                @show="selectOpen = $event"
            /></FormItem>
            <FormItem id="schedule-enabled" :label="t('schedules.enabled')"
              ><NSwitch v-model:value="draft.enabled" :aria-label="t('schedules.enabled')"
            /></FormItem>
          </div>
          <FormItem
            id="schedule-targetId"
            :label="t('schedules.target')"
            required
            :invalid="!!fieldError('targetId')"
            :message="fieldError('targetId')"
            ><EntityPicker
              id="schedule-targetId"
              v-model="draft.targetId"
              :selected-name="draft.targetType === row?.targetType ? row?.targetName : undefined"
              :selected-id="row?.targetId"
              :endpoint="draft.targetType === 'TASK' ? 'tasks' : 'jobs'"
              :label="t('schedules.target')"
              :invalid="!!fieldError('targetId')"
              :disabled="busy"
              @show="selectOpen = $event"
            />
            <p class="schedule-note">{{ t('schedules.targetHint') }}</p></FormItem
          >
          <FormItem id="schedule-type" :label="t('schedules.scheduleType')" required
            ><NRadioGroup v-model:value="draft.scheduleType" :name="t('schedules.scheduleType')"
              ><NRadio v-for="value in types" :key="value" :value="value">{{
                t('schedules.' + value)
              }}</NRadio></NRadioGroup
            ></FormItem
          >
          <FormItem
            v-if="draft.scheduleType === 'CRON'"
            id="schedule-cronExpr"
            :label="t('schedules.cronExpr')"
            required
            :invalid="!!fieldError('cronExpr')"
            :message="fieldError('cronExpr')"
            ><NInput
              v-model:value="draft.cronExpr"
              :input-props="{ id: 'schedule-cronExpr', 'aria-invalid': !!fieldError('cronExpr') }"
            />
            <p class="schedule-note">{{ t('schedules.cronHint') }}</p></FormItem
          >
          <FormItem
            v-if="draft.scheduleType === 'INTERVAL'"
            id="schedule-intervalSec"
            :label="t('schedules.intervalSec')"
            required
            :invalid="!!fieldError('intervalSec')"
            :message="fieldError('intervalSec')"
            ><NInputNumber
              v-model:value="draft.intervalSec"
              :min="1"
              :max="metadata.maxIntervalSec"
              :precision="0"
              :input-props="{
                id: 'schedule-intervalSec',
                'aria-invalid': !!fieldError('intervalSec'),
              }"
            />
            <p class="schedule-note">
              {{ t('schedules.bounds', { min: 1, max: metadata.maxIntervalSec }) }} ·
              {{ t('schedules.intervalHint') }}
            </p></FormItem
          >
          <div class="schedule-columns">
            <FormItem
              v-for="key in ['startTime', 'endTime'] as const"
              :id="'schedule-' + key"
              :key="key"
              :label="
                t(
                  draft.scheduleType === 'ONCE' && key === 'startTime'
                    ? 'schedules.onceTime'
                    : 'schedules.' + key,
                )
              "
              :required="draft.scheduleType === 'ONCE' && key === 'startTime'"
              :invalid="!!fieldError(key)"
              :message="fieldError(key)"
              ><DateTimePicker
                :id="'schedule-' + key"
                v-model="draft[key]"
                :label="
                  t(
                    draft.scheduleType === 'ONCE' && key === 'startTime'
                      ? 'schedules.onceTime'
                      : 'schedules.' + key,
                  )
                "
                :zone-id="metadata.zoneId"
                :required="draft.scheduleType === 'ONCE' && key === 'startTime'"
                :invalid="!!fieldError(key)"
            /></FormItem>
          </div>
          <FormItem
            id="schedule-calendarId"
            :label="t('schedules.calendarId')"
            :invalid="!!fieldError('calendarId')"
            :message="fieldError('calendarId')"
            ><EntityPicker
              id="schedule-calendarId"
              v-model="draft.calendarId"
              endpoint="calendars"
              :label="t('schedules.calendarId')"
              :disabled="busy"
              @show="selectOpen = $event"
          /></FormItem>
          <SchedulePreview :draft="draft" :metadata="metadata" :disabled="busy" />
          <NCollapse v-model:expanded-names="expanded">
            <NCollapseItem name="variables" :title="t('schedules.variablesText')"
              ><div :aria-invalid="!!fieldError('variablesText')">
                <ParameterFormEditor
                  ref="parameterEditor"
                  v-model="draft.variablesText"
                  v-model:parameter-form="draft.parameterForm"
                  compact-table
                  :target-type="draft.targetType"
                  :target-id="draft.targetId"
                  :auto-default="!row"
                  :disabled="busy"
                />
              </div>
              <p v-if="fieldError('variablesText')" role="alert" class="schedule-error">
                {{ fieldError('variablesText') }}
              </p>
              <p class="schedule-note">{{ t('parameters.scheduleHint') }}</p></NCollapseItem
            >
            <NCollapseItem name="advanced" :title="t('schedules.advanced')">
              <p class="schedule-note">{{ t('schedules.inheritHint') }}</p>
              <FormItem id="schedule-misfirePolicy" :label="t('schedules.misfirePolicy')"
                ><Select
                  id="schedule-misfirePolicy"
                  :value="draft.misfirePolicy"
                  :label="t('schedules.misfirePolicy')"
                  :options="[
                    { value: '', label: t('schedules.inherit') },
                    ...policies.map((value) => ({ value, label: t('schedules.' + value) })),
                  ]"
                  @update:value="draft.misfirePolicy = String($event)"
                  @show="selectOpen = $event"
              /></FormItem>

              <p class="schedule-note">
                {{
                  t('schedules.defaults', {
                    policy: t('schedules.' + metadata.defaultMisfirePolicy),
                    grace: metadata.defaultMisfireGraceSec,
                    count: metadata.defaultMaxCatchupCount,
                    window: metadata.maxCatchupWindowSec,
                  })
                }}
              </p>
              <p class="schedule-note">{{ t('schedules.catchupHint') }}</p>
              <div class="schedule-columns">
                <FormItem
                  v-for="key in ['misfireGraceSec', 'maxCatchupCount'] as const"
                  :id="'schedule-' + key"
                  :key="key"
                  :label="t('schedules.' + key)"
                  :invalid="!!fieldError(key)"
                  :message="fieldError(key)"
                  ><NInputNumber
                    v-model:value="draft[key]"
                    :placeholder="t('schedules.inherit')"
                    :min="key === 'misfireGraceSec' ? 0 : 1"
                    :max="key === 'misfireGraceSec' ? 2147483647 : metadata.maxCatchupCount"
                    :precision="0"
                    :input-props="{ id: 'schedule-' + key, 'aria-invalid': !!fieldError(key) }"
                /></FormItem>
              </div>
              <FormItem
                id="schedule-maxQueueDelaySec"
                :label="t('schedules.maxQueueDelaySec')"
                :invalid="!!fieldError('maxQueueDelaySec')"
                :message="fieldError('maxQueueDelaySec')"
                ><NInputNumber
                  v-model:value="draft.maxQueueDelaySec"
                  :min="1"
                  :max="metadata.maxIntervalSec"
                  :precision="0"
                  :input-props="{
                    id: 'schedule-maxQueueDelaySec',
                    'aria-invalid': !!fieldError('maxQueueDelaySec'),
                  }"
                />
                <p class="schedule-note">{{ t('schedules.queueHint') }}</p></FormItem
              >
            </NCollapseItem>
          </NCollapse>
          <RequestError :error="error" />
        </NForm>
      </div>
      <template #footer
        ><div class="schedule-footer">
          <span class="schedule-note">{{ t('schedules.saveHint') }}</span
          ><NButton :disabled="busy" @click="requestClose">{{ t('common.cancel') }}</NButton
          ><NButton
            type="primary"
            :loading="busy"
            :disabled="busy || uncertain || (!!row && !changed)"
            @click="save"
            >{{ t('common.save') }}</NButton
          >
        </div></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
