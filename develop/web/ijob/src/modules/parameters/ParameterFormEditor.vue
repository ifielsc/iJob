<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NButton, NCheckbox, NModal, NInput, NSelect, useDialog, useMessage } from 'naive-ui'
import { stringify, parse } from 'lossless-json'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { describeError } from '../../shared/api/errors'
import { pageData, exactId, record } from '../../shared/api/values'
import RequestError from '../../shared/ui/RequestError.vue'
import FieldValue from './FieldValue.vue'
import ParameterTable from './ParameterTable.vue'
import {
  copy,
  textOf,
  fromTemplate,
  rowsOf,
  readRows,
  template,
  type Template,
  type Row,
  type Form,
} from './model'
const props = withDefaults(
  defineProps<{
    modelValue?: string
    parameterForm?: Form | null
    targetType?: 'TASK' | 'JOB'
    targetId?: string
    autoDefault?: boolean
    disabled?: boolean
    execution?: boolean
    externalActions?: boolean
    unbounded?: boolean
    compactTable?: boolean
    showBindings?: boolean
  }>(),
  { modelValue: '{}', parameterForm: null, targetType: undefined, targetId: undefined },
)
const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:parameterForm': [value: Form | null]
}>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  message = useMessage(),
  dialog = useDialog()
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && !runtime.accessIssue.value)
const controller = new AbortController(),
  rows = ref<Row[]>([]),
  templates = ref<Template[]>([]),
  selected = ref<string | null>(props.parameterForm?.templateId ?? null)
const busy = ref(false),
  loading = ref(true),
  error = ref<unknown>(),
  advanced = ref(false),
  jsonText = ref(props.modelValue),
  name = ref(''),
  saveOpen = ref(false),
  useValues = ref(false),
  page = ref(1),
  total = ref(0),
  invalid = ref(false)
const parameterTable = ref<InstanceType<typeof ParameterTable>>()
const active = ref(0)
const current = computed(() => rows.value[active.value])
watch(
  () => rows.value.length,
  (length) => {
    active.value = Math.max(0, Math.min(active.value, length - 1))
  },
)
function modified(row: Row) {
  return (
    row.included !== (!!row.field.hasDefault || !!row.field.required) ||
    row.text !==
      (row.field.hasDefault
        ? textOf(row.field.defaultValue, row.field.type)
        : row.field.type === 'BOOLEAN'
          ? 'false'
          : '')
  )
}
function restore() {
  if (!current.value || props.disabled || busy.value) return
  current.value.text = textOf(current.value.field.defaultValue, current.value.field.type)
  current.value.included = true
}
const initialRows = ref('')
const hasEdits = computed(() => advanced.value || stringify(rows.value) !== initialRows.value)
let loadRevision = 0
try {
  rows.value = rowsOf(props.modelValue, props.parameterForm)
} catch (e) {
  error.value = e
  advanced.value = true
}
initialRows.value = stringify(rows.value)!
const fields = computed(() => rows.value.map((r) => r.field))
const options = computed(() => templates.value.map((v) => ({ label: v.name, value: v.id })))
function templatePage(delta: number) {
  page.value += delta
  void loadTemplates()
}
async function loadTemplates() {
  error.value = undefined
  try {
    const data = pageData(
      await runtime.request(`/api/parameter-templates?pageNo=${page.value}&pageSize=100`, {
        signal: controller.signal,
        lossless: true,
      }),
      template,
    )
    templates.value = data.list
    total.value = data.total
  } catch (e) {
    if (!controller.signal.aborted) error.value = e
  }
}
function form(): Form {
  return { templateId: selected.value, fields: copy(fields.value) }
}
function publish() {
  emit('update:parameterForm', advanced.value ? null : form())
  try {
    const text = advanced.value ? jsonText.value : stringify(readRows(rows.value), null, 2)!
    JSONcheck(text)
    emit('update:modelValue', text)
  } catch {
    /* Keep the draft; read() blocks submission. */
  }
}
function JSONcheck(text: string) {
  rowsOf(text)
}
function read() {
  if (busy.value || loading.value || error.value || runtime.accessIssue.value)
    throw Error('loading')
  try {
    const values = advanced.value ? record(parse(jsonText.value)) : readRows(rows.value)
    invalid.value = false
    return { variables: values, parameterForm: advanced.value ? null : form() }
  } catch (e) {
    invalid.value = true
    if (!advanced.value) {
      const index = rows.value.findIndex((row) => {
        try {
          readRows([row])
          return false
        } catch {
          return true
        }
      })
      if (index >= 0) {
        active.value = index
        void parameterTable.value?.reveal(index)
      }
    }
    throw e
  }
}
const hasUnpublishedInput = computed(() => {
  try {
    if (advanced.value) JSONcheck(jsonText.value)
    else readRows(rows.value)
    return false
  } catch {
    return advanced.value
      ? jsonText.value !== props.modelValue
      : stringify(rows.value) !== initialRows.value
  }
})
function openSave() {
  saveOpen.value = true
}
defineExpose({
  hasUnpublishedInput,
  read,
  openSave,
  pending: computed(() => busy.value || loading.value),
  canSave: computed(() => writer.value && !props.disabled && !busy.value && !loading.value),
})
watch(rows, publish, { deep: true })
watch(jsonText, () => {
  if (advanced.value) publish()
})
async function applyTemplate(id: string | null = selected.value) {
  if (busy.value || props.disabled) return
  const seq = ++loadRevision
  error.value = undefined
  invalid.value = false
  if (!id) {
    selected.value = null
    rows.value = []
    advanced.value = false
    initialRows.value = stringify(rows.value)!
    publish()
    return
  }
  busy.value = true
  error.value = undefined
  try {
    const current = template(
      await runtime.request('/api/parameter-templates/' + id, {
        signal: controller.signal,
        lossless: true,
      }),
    )
    if (seq !== loadRevision || controller.signal.aborted) return
    selected.value = current.id
    advanced.value = false
    rows.value = fromTemplate(current)
    initialRows.value = stringify(rows.value)!
    active.value = 0
    publish()
  } catch (e) {
    if (!controller.signal.aborted) error.value = e
  } finally {
    busy.value = false
  }
}
function chooseTemplate(id: string | null) {
  if (id === selected.value || busy.value || loading.value || props.disabled) return
  if (!hasEdits.value) {
    void applyTemplate(id)
    return
  }
  dialog.warning({
    title: t('parameters.template'),
    content: t('parameters.replaceDraft'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => applyTemplate(id),
  })
}
function reload() {
  dialog.warning({
    title: t('parameters.reload'),
    content: t('parameters.reloadHint'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => applyTemplate(),
  })
}
async function loadDefault() {
  const seq = ++loadRevision
  if (!props.autoDefault || !props.targetId || !props.targetType) return
  loading.value = true
  error.value = undefined
  try {
    const data = await runtime.request(
      `/api/parameter-templates/bindings/${props.targetType}/${props.targetId}`,
      { signal: controller.signal, lossless: true },
    )
    if (seq !== loadRevision || controller.signal.aborted) return
    if (data) {
      const current = template(data)
      selected.value = current.id
      rows.value = fromTemplate(current)
      initialRows.value = stringify(rows.value)!
      if (!templates.value.some((x) => x.id === current.id)) templates.value.push(current)
      publish()
    }
  } catch (e) {
    if (seq === loadRevision && !controller.signal.aborted) error.value = e
  } finally {
    if (seq === loadRevision) loading.value = false
  }
}
watch(
  () => [props.targetType, props.targetId],
  () => {
    if (props.autoDefault) {
      selected.value = null
      rows.value = []
      initialRows.value = stringify(rows.value)!
      jsonText.value = '{}'
      advanced.value = false
      publish()
    }
    void loadDefault()
  },
)
onMounted(async () => {
  await loadTemplates()
  await loadDefault()
  loading.value = false
})
onBeforeUnmount(() => {
  controller.abort()
  rows.value = []
  jsonText.value = ''
})
function toggle() {
  try {
    if (advanced.value) {
      rows.value = rowsOf(jsonText.value, form())
      advanced.value = false
    } else {
      jsonText.value = stringify(readRows(rows.value), null, 2)!
      advanced.value = true
    }
    publish()
  } catch {
    invalid.value = true
  }
}
async function saveTemplate() {
  if (!writer.value || busy.value || !name.value.trim()) return
  busy.value = true
  error.value = undefined
  try {
    const currentFields = advanced.value
      ? rowsOf(jsonText.value).map((r) => r.field)
      : copy(fields.value)
    const values = useValues.value
      ? advanced.value
        ? record(parse(jsonText.value))
        : readRows(rows.value)
      : {}
    for (const f of currentFields) {
      if (f.sensitive || !useValues.value) {
        f.hasDefault = false
        delete f.defaultValue
      } else {
        f.hasDefault = Object.hasOwn(values, f.name)
        f.defaultValue = values[f.name]
      }
    }
    const id = exactId(
      await runtime.request('/api/parameter-templates', {
        method: 'POST',
        body: { name: name.value.trim(), fields: currentFields },
        signal: controller.signal,
        lossless: true,
      }),
    )
    selected.value = id
    saveOpen.value = false
    message.success(t('parameters.saved'))
    await loadTemplates()
    publish()
  } catch (e) {
    error.value = e
  } finally {
    busy.value = false
  }
}
async function bind(id: string | null) {
  if (!writer.value || !props.targetId || !props.targetType) return
  busy.value = true
  try {
    await runtime.request(
      `/api/parameter-templates/bindings/${props.targetType}/${props.targetId}`,
      { method: 'PUT', body: { templateId: id }, signal: controller.signal },
    )
    message.success(t('parameters.saved'))
  } catch (e) {
    message.error(describeError(e, t))
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div
    class="parameter-form"
    :class="{ 'parameter-form-unbounded': unbounded }"
    :aria-busy="loading || busy"
  >
    <details class="parameter-system-help">
      <summary>{{ t('jsonForm.systemParameters') }}</summary>
      <p>{{ t('jsonForm.businessHelp') }}</p>
      <code>ijob.business.date · ijob.business.time · ijob.business.zoneId</code>
      <p>{{ t('jsonForm.utcHelp') }}</p>
      <code>ijob.trigger.date · ijob.trigger.time</code>
    </details>
    <RequestError :error="error" />
    <div class="parameter-toolbar">
      <NSelect
        :value="selected"
        :options="options"
        filterable
        clearable
        :disabled="disabled || busy || loading"
        :placeholder="t('parameters.template')"
        :aria-label="t('parameters.template')"
        @update:value="chooseTemplate"
      />
      <NButton :disabled="disabled || !selected || busy || loading" @click="reload">{{
        t('parameters.reloadShort')
      }}</NButton>
    </div>
    <div v-if="total > 100" class="parameter-toolbar">
      <NButton :disabled="page === 1 || busy || loading" @click="templatePage(-1)">{{
        t('parameters.previousTemplates')
      }}</NButton>
      <NButton :disabled="page * 100 >= total || busy || loading" @click="templatePage(1)">{{
        t('parameters.nextTemplates')
      }}</NButton>
    </div>
    <p v-if="parameterForm && !autoDefault" class="parameter-note">
      {{ t('parameters.savedForm') }}
    </p>
    <div class="parameter-section-heading">
      <h3>
        {{ t('parameters.list') }} <small>{{ rows.length }}</small>
      </h3>
      <NButton text size="small" :disabled="disabled || busy || loading" @click="toggle">{{
        t(advanced ? 'parameters.form' : 'parameters.advanced')
      }}</NButton>
    </div>
    <template v-if="!advanced">
      <ParameterTable
        ref="parameterTable"
        :unbounded="unbounded"
        :compact="compactTable"
        :fields="fields"
        :active="active"
        :values="rows.map((r) => (r.included ? r.text : ''))"
        execution
        @select="active = $event"
      >
        <template #badge="{ index }"
          ><span v-if="modified(rows[index]!)" class="parameter-changed">{{
            t('parameters.modified')
          }}</span></template
        >
        <template #action="{ index }"
          ><NCheckbox
            v-model:checked="rows[index]!.included"
            :disabled="disabled || busy || loading || rows[index]!.field.required"
            :aria-label="t('parameters.include') + ' ' + rows[index]!.field.name"
            :aria-disabled="disabled || busy || loading || !!rows[index]!.field.required"
        /></template>
      </ParameterTable>
      <section v-if="current" class="parameter-detail parameter-value-detail">
        <div class="parameter-section-heading">
          <h3>
            {{ current.field.name }}
            <span class="parameter-type">{{ t('parameters.' + current.field.type) }}</span>
          </h3>
          <NButton
            v-if="current.field.hasDefault && !current.field.sensitive"
            text
            type="primary"
            size="small"
            :disabled="disabled || busy || loading"
            @click="restore"
            >{{ t('parameters.restore') }}</NButton
          >
        </div>
        <div class="parameter-value-line">
          <span class="parameter-value-label"
            >{{ t('parameters.currentValue')
            }}<span v-if="current.field.required" class="parameter-required"> *</span></span
          >
          <div
            class="parameter-value-control"
            :class="{ 'is-structured': ['OBJECT', 'ARRAY'].includes(current.field.type) }"
          >
            <FieldValue
              v-model="current.text"
              :field="current.field"
              :disabled="disabled || busy || loading || !current.included"
              :label="current.field.name || t('parameters.value')"
            />
          </div>
          <span
            v-if="current.field.description"
            class="parameter-note parameter-field-description"
            >{{ current.field.description }}</span
          >
        </div>
        <div class="parameter-toolbar parameter-note">
          <span v-if="current.field.hasDefault && !current.field.sensitive">{{
            current.field.hasDefault
              ? t('parameters.defaultValue') +
                ': ' +
                textOf(current.field.defaultValue, current.field.type)
              : t('parameters.noDefault')
          }}</span>
          <span v-if="current.field.minimum != null"
            >{{ t('parameters.min') }}: {{ current.field.minimum }}</span
          >
          <span v-if="current.field.maximum != null"
            >{{ t('parameters.max') }}: {{ current.field.maximum }}</span
          >
          <span v-if="current.field.maxLength != null"
            >{{ t('parameters.maxLength') }}: {{ current.field.maxLength }}</span
          >
        </div>
      </section>
      <EmptyState
        v-else-if="!loading"
        compact
        :message="
          t(
            showBindings
              ? 'parameters.emptyManagement'
              : execution
                ? 'parameters.empty'
                : 'parameters.emptySchedule',
          )
        "
      />
    </template>
    <NInput
      v-else
      v-model:value="jsonText"
      type="textarea"
      :autosize="{ minRows: 4, maxRows: 10 }"
      :disabled="disabled || busy"
      :input-props="{ 'aria-label': t('parameters.advanced') }"
    />
    <p v-if="invalid" role="alert" class="parameter-error">{{ t('parameters.invalid') }}</p>
    <p v-if="fields.some((f) => f.sensitive)" class="parameter-note">
      {{ t('parameters.secretHint') }}
    </p>
    <NButton
      v-if="writer && !execution && !externalActions"
      type="primary"
      :disabled="disabled || busy || loading"
      @click="saveOpen = true"
      >{{ t('parameters.saveTemplate') }}</NButton
    >
    <div v-if="showBindings && writer && targetId && targetType" class="parameter-toolbar">
      <NButton text :disabled="disabled || !selected || busy || loading" @click="bind(selected)">{{
        t('parameters.bind')
      }}</NButton
      ><NButton text :disabled="disabled || busy || loading" @click="bind(null)">{{
        t('parameters.unbind')
      }}</NButton>
    </div>
    <NModal
      v-model:show="saveOpen"
      preset="dialog"
      :title="t('parameters.saveTemplate')"
      :show-icon="false"
      :mask-closable="false"
      :closable="!busy"
      :close-on-esc="!busy"
    >
      <div class="parameter-save-form">
        <NInput
          v-model:value="name"
          :maxlength="128"
          :disabled="busy"
          :placeholder="t('parameters.name')"
          :input-props="{ 'aria-label': t('parameters.name') }"
        />
        <NCheckbox v-model:checked="useValues" :disabled="busy">{{
          t('parameters.useValues')
        }}</NCheckbox>
        <p class="parameter-note">{{ t('parameters.secretHint') }}</p>
        <RequestError :error="error" />
      </div>
      <template #action
        ><NButton :disabled="busy" @click="saveOpen = false">{{ t('common.cancel') }}</NButton
        ><NButton
          type="primary"
          :loading="busy"
          :disabled="!name.trim() || busy"
          @click="saveTemplate"
          >{{ t('common.save') }}</NButton
        ></template
      >
    </NModal>
  </div>
</template>
