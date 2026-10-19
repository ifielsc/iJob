<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import {
  computed,
  defineAsyncComponent,
  h,
  nextTick,
  onBeforeUnmount,
  reactive,
  ref,
  shallowRef,
  watch,
} from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  NButton,
  NDrawer,
  NDrawerContent,
  NForm,
  NInput,
  NInputNumber,
  NTag,
  NSelect,
  NSwitch,
  NCollapse,
  NCollapseItem,
  useDialog,
  useMessage,
} from 'naive-ui'
import { draftFingerprint } from '../../shared/forms/draft-fingerprint'
import { pluginLabel, pluginTypes } from '../../shared/plugins'
import { X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { ApiError, describeError } from '../../shared/api/errors'
import EditableTable from '../../shared/ui/EditableTable.vue'
import JsonFormEditor from '../../shared/ui/JsonFormEditor.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import {
  draftFor as taskDraftFor,
  issues,
  taskBody,
  taskRow,
  exactId,
  pageData,
  record,
  activeScript,
} from './model'
import type { TaskRow, Draft } from './model'
import ApiCallFields from './ApiCallFields.vue'
import RemoteStopFields from './RemoteStopFields.vue'
import DataCheckFields from './DataCheckFields.vue'
import PushFields from './PushFields.vue'
import EtlFields from './EtlFields.vue'
import LocalJarFields from './LocalJarFields.vue'
import MonitorFields from './MonitorFields.vue'
import BatchEtlFields from './BatchEtlFields.vue'
import { prepareEtlForms } from './etl-model'
import FileTransferFields from './FileTransferFields.vue'
import EventFields from './EventFields.vue'
import { parseConfig } from '../configuration/public/contracts'
const SqlEditor = defineAsyncComponent(() => import('../../shared/ui/SqlEditor.vue'))
const props = defineProps<{ row?: TaskRow }>(),
  emit = defineEmits<{ close: []; saved: [string] }>()
const runtime = useRuntime(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage()
function draftFor(row?: TaskRow) {
  const draft = taskDraftFor(row)
  prepareEtlForms(draft.etl)
  return draft
}
const current = shallowRef(props.row),
  draft = reactive(draftFor(props.row)),
  baseline = ref(JSON.stringify(draft))
const isLocalJar = computed(() => draft.pluginType === 'LOCAL_JAR'),
  localJarFields = ref<InstanceType<typeof LocalJarFields>>()
const isMonitor = computed(() => draft.pluginType === 'SERVER_MONITOR'),
  monitorFields = ref<InstanceType<typeof MonitorFields>>()
const isBatchEtl = computed(() => draft.pluginType === 'BATCH_ETL'),
  batchEtlFields = ref<InstanceType<typeof BatchEtlFields>>()
const isEtl = computed(() => draft.pluginType === 'ETL'),
  etlFields = ref<InstanceType<typeof EtlFields>>()
const isPush = computed(() => draft.pluginType === 'PUSH'),
  pushFields = ref<InstanceType<typeof PushFields>>()
const isTransfer = computed(() => draft.pluginType === 'FILE_TRANSFER'),
  transferFields = ref<InstanceType<typeof FileTransferFields>>()
const isCheck = computed(() => draft.pluginType === 'DATA_CHECK'),
  checkFields = ref<InstanceType<typeof DataCheckFields>>()
const isApi = computed(() => draft.pluginType === 'API_CALL')
const isEvent = computed(() => draft.pluginType === 'EVENT'),
  eventFields = ref<InstanceType<typeof EventFields>>(),
  uncertain = ref(false)
const serviceReady = ref(false),
  serviceLoading = ref(false),
  serviceError = ref(false)
const isScript = computed(() => draft.pluginType === 'SCRIPT'),
  scriptDraft = computed(() => activeScript(draft)),
  scriptValue = computed({
    get: () =>
      isApi.value ? draft.api.http.body : isScript.value ? scriptDraft.value.script : draft.script,
    set: (value: string) => {
      if (isApi.value) draft.api.http.body = value
      else if (isScript.value) scriptDraft.value.script = value
      else draft.script = value
    },
  }),
  resourceType = computed(() =>
    isEvent.value ||
    isTransfer.value ||
    isPush.value ||
    isEtl.value ||
    isBatchEtl.value ||
    isMonitor.value ||
    isLocalJar.value
      ? null
      : isApi.value
        ? 'SERVICE'
        : !isScript.value
          ? 'DB'
          : draft.executionMode === 'SSH'
            ? 'SSH'
            : null,
  ),
  resourceId = computed({
    get: () =>
      isApi.value ? draft.api.resourceId : isScript.value ? draft.sshResourceId : draft.resourceId,
    set: (value: string) => {
      if (isApi.value) draft.api.resourceId = value
      else if (isScript.value) draft.sshResourceId = value
      else draft.resourceId = value
    },
  }),
  engines = computed(() =>
    (draft.executionMode === 'SSH'
      ? ['SHELL', 'PYTHON']
      : ['SHELL', 'BAT', 'POWERSHELL', 'PYTHON']
    ).map((value) => ({
      value,
      label:
        value === 'SHELL'
          ? 'Shell'
          : value === 'POWERSHELL'
            ? 'PowerShell'
            : value === 'PYTHON'
              ? 'Python'
              : 'BAT',
    })),
  )
const groupInput = ref(''),
  groupError = ref('')
const groupValues = computed(() =>
  draft.groups ? draft.groups.split(',').map((g) => g.trim()) : [],
)
function addGroup() {
  if (busy.value) return false
  const value = groupInput.value.trim().toLowerCase()
  if (!value) {
    groupInput.value = ''
    return true
  }
  if (!/^[a-z0-9][a-z0-9._-]{0,63}$/.test(value)) {
    groupError.value = 'groupInvalid'
    return false
  }
  if (!groupValues.value.includes(value)) {
    if (groupValues.value.length >= 16) {
      groupError.value = 'groupLimit'
      return false
    }
    draft.groups = [...groupValues.value, value].join(', ')
  }
  groupInput.value = ''
  groupError.value = ''
  return true
}
function groupKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.isComposing) {
    event.preventDefault()
    event.stopPropagation()
    addGroup()
  }
}
function removeGroup(value: string) {
  if (busy.value) return
  draft.groups = groupValues.value.filter((g) => g !== value).join(', ')
  groupError.value = ''
}
watch(groupInput, () => {
  groupError.value = ''
})
const dirty = computed(() => JSON.stringify(draft) !== baseline.value || !!groupInput.value),
  changed = computed(
    () =>
      draftFingerprint(draft) !== draftFingerprint(JSON.parse(baseline.value)) ||
      !!groupInput.value,
  ),
  busy = ref(false),
  submitted = ref(false),
  expanded = ref<string[]>([]),
  large = ref(false),
  closing = ref(false)
const error = shallowRef<unknown>(null),
  latest = shallowRef<TaskRow>(),
  form = ref<HTMLElement | null>(null),
  sql = ref<{ focus: () => void }>()
const remoteStopFields = ref<{ focusError: (key: string) => void }>()
const controller = new AbortController()
watch(
  () => runtime.session.revision,
  () => {
    controller.abort()
    emit('close')
  },
)
const validation = computed(() => issues(draft)),
  fieldError = (key: string) =>
    submitted.value && validation.value[key] ? t('tasks.' + validation.value[key]) : undefined
const conflict = computed(() => error.value instanceof ApiError && error.value.kind === 'conflict')
function confirmDiscard() {
  return new Promise<boolean>((resolve) =>
    dialog.warning({
      title: t('common.discard'),
      content: t('common.discardHint'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
      onMaskClick: () => resolve(false),
      onEsc: () => resolve(false),
    }),
  )
}
async function allowClose() {
  return !busy.value && (!dirty.value || (await confirmDiscard()))
}
async function close() {
  if (large.value || closing.value || busy.value) return
  closing.value = true
  try {
    if (await allowClose()) emit('close')
  } finally {
    closing.value = false
  }
}
onBeforeRouteLeave(() =>
  runtime.accessIssue.value || !runtime.session.token ? true : allowClose(),
)
const unload = (e: BeforeUnloadEvent) => {
  if (dirty.value) e.preventDefault()
}
window.addEventListener('beforeunload', unload)
onBeforeUnmount(() => {
  controller.abort()
  resourceController?.abort()
  clearTimeout(resourceTimer)
  window.removeEventListener('beforeunload', unload)
})
let serviceGeneration = 0
async function loadService() {
  const generation = ++serviceGeneration,
    id = draft.api.resourceId
  serviceReady.value = false
  serviceError.value = false
  if (!isApi.value || !id) {
    serviceLoading.value = false
    return
  }
  serviceLoading.value = true
  try {
    const row = parseConfig(
      await runtime.request('/api/resources/' + id, { signal: controller.signal }),
      true,
    )
    if (
      generation !== serviceGeneration ||
      controller.signal.aborted ||
      !isApi.value ||
      id !== draft.api.resourceId
    )
      return
    if (row.type !== 'SERVICE' || !row.enabled) throw new ApiError('protocol')
    draft.api.protocol = row.config!.protocol!
    draft.api.authType = row.config!.authType!
    if (current.value?.pluginType === 'API_CALL' && id === draftFor(current.value).api.resourceId) {
      const original = JSON.parse(baseline.value)
      original.api.protocol = draft.api.protocol
      original.api.authType = draft.api.authType
      baseline.value = JSON.stringify(original)
    }
    serviceReady.value = true
  } catch {
    if (generation === serviceGeneration && !controller.signal.aborted) serviceError.value = true
  } finally {
    if (generation === serviceGeneration) serviceLoading.value = false
  }
}
watch(
  () => [isApi.value, draft.api.resourceId],
  () => void loadService(),
  { immediate: true, flush: 'sync' },
)
const resourceOptions = ref<{ label: string; value: string; disabled?: boolean }[]>([]),
  resourceBusy = ref(false),
  resourceName = ref(''),
  resourcePage = ref(1),
  resourceTotal = ref(0),
  selectOpen = ref(false)
let resourceController: AbortController | undefined,
  resourceTimer: ReturnType<typeof setTimeout> | undefined,
  resourceGeneration = 0
async function resources(append = false) {
  const generation = ++resourceGeneration
  resourceController?.abort()
  if (!resourceType.value) {
    resourceBusy.value = false
    return
  }
  resourceController = new AbortController()
  const signal = AbortSignal.any([controller.signal, resourceController.signal])
  resourceBusy.value = true
  try {
    const params = new URLSearchParams({
      type: resourceType.value,
      enabled: 'true',
      pageNum: String(resourcePage.value),
      pageSize: '20',
      name: resourceName.value,
    })
    const data = pageData(await runtime.request('/api/resources?' + params, { signal }), (v) => {
      const r = record(v)
      if (typeof r.name !== 'string') throw new ApiError('protocol')
      return { value: exactId(r.id), label: r.name }
    })
    if (generation !== resourceGeneration || signal.aborted) return
    resourceOptions.value = append
      ? [...new Map([...resourceOptions.value, ...data.list].map((x) => [x.value, x])).values()]
      : data.list
    resourceTotal.value = data.total
  } catch (e) {
    if (!signal.aborted && !runtime.accessIssue.value) message.error(describeError(e, t))
  } finally {
    if (generation === resourceGeneration) resourceBusy.value = false
  }
}
function searchResources(value: string) {
  resourceName.value = value
  resourcePage.value = 1
  clearTimeout(resourceTimer)
  resourceTimer = setTimeout(() => void resources(), 250)
}
function moreResources(event: Event) {
  const el = event.target as HTMLElement
  if (
    !resourceBusy.value &&
    resourcePage.value * 20 < resourceTotal.value &&
    el.scrollTop + el.clientHeight >= el.scrollHeight - 24
  ) {
    resourcePage.value++
    void resources(true)
  }
}
const resourceLabels = reactive(
  new Map<string, { value: string; label: string; disabled?: boolean }>(),
)
watch(resourceOptions, (rows) => {
  for (const row of rows) resourceLabels.set(row.value, row)
})
watch(
  () => [resourceId.value, resourceType.value] as const,
  ([originalId, type]) => {
    if (!type) return
    if (!originalId || resourceLabels.has(originalId)) return
    void runtime
      .request('/api/resources/' + originalId, { signal: controller.signal })
      .then((value) => {
        if (controller.signal.aborted || runtime.accessIssue.value) return
        const row = record(value)
        if (row.type === type && row.enabled === true && typeof row.name === 'string')
          resourceLabels.set(originalId, { value: originalId, label: row.name })
      })
      .catch(() => {
        /* List queries and the preserved identifier remain available for recovery. */
      })
  },
  { immediate: true },
)
const options = computed(() =>
  resourceId.value && !resourceOptions.value.some((o) => o.value === resourceId.value)
    ? [
        resourceLabels.get(resourceId.value) ?? {
          value: resourceId.value,
          label: t('tasks.missingResource', { id: resourceId.value }),
          disabled: true,
        },
        ...resourceOptions.value,
      ]
    : resourceOptions.value,
)
watch(
  resourceType,
  () => {
    clearTimeout(resourceTimer)
    resourceOptions.value = []
    resourcePage.value = 1
    resourceName.value = ''
    resourceTotal.value = 0
    void resources()
  },
  { immediate: true },
)
watch(
  () => draft.transaction,
  (value) => {
    if (value) draft.continueOnError = false
  },
)
type NumericKey = keyof Pick<
  Draft,
  'queryTimeoutSeconds' | 'retryTimes' | 'retryInterval' | 'timeout' | 'minRows' | 'maxRows'
>
const numbers = computed<NumericKey[]>(() =>
  isEvent.value && ['WAIT_EVENT', 'WAIT_APPROVAL'].includes(draft.event.action)
    ? ['retryTimes', 'retryInterval']
    : isScript.value ||
        isApi.value ||
        isEvent.value ||
        isCheck.value ||
        isTransfer.value ||
        isPush.value ||
        isEtl.value ||
        isBatchEtl.value ||
        isMonitor.value ||
        isLocalJar.value
      ? ['retryTimes', 'retryInterval', 'timeout']
      : ['queryTimeoutSeconds', 'timeout', 'retryTimes', 'retryInterval', 'minRows', 'maxRows'],
)
async function submit() {
  if (current.value && !changed.value) return
  if (busy.value || conflict.value || runtime.accessIssue.value || large.value || uncertain.value)
    return
  if (!addGroup()) {
    expanded.value = ['advanced']
    await nextTick()
    form.value?.querySelector<HTMLElement>('#task-groups')?.focus()
    return
  }
  submitted.value = true
  if (Object.keys(validation.value).length) {
    if (Object.keys(validation.value).some((k) => !['name', 'resourceId', 'script'].includes(k)))
      expanded.value = ['advanced']
    await nextTick()
    const key = Object.keys(validation.value)[0]
    if (key?.startsWith('localJar.')) await localJarFields.value?.focusError(key)
    else if (key?.startsWith('monitor.')) await monitorFields.value?.focusError(key)
    else if (key?.startsWith('batchEtl.')) await batchEtlFields.value?.focusError(key)
    else if (key?.startsWith('etl.')) await etlFields.value?.focusError(key)
    else if (key?.startsWith('push.')) await pushFields.value?.focusError(key)
    else if (key?.startsWith('transfer.')) await transferFields.value?.focusError(key)
    else if (key?.startsWith('check.')) await checkFields.value?.focusError(key)
    else if (key?.startsWith('remoteStop.')) remoteStopFields.value?.focusError(key)
    else if (key === 'script' || key === 'body') sql.value?.focus()
    else form.value?.querySelector<HTMLElement>('[id="task-' + key + '"]')?.focus()
    return
  }
  if (isApi.value && !serviceReady.value) {
    serviceError.value = true
    return
  }
  if (isMonitor.value && !monitorFields.value?.validateNotify()) {
    await monitorFields.value?.focusError('monitor.notifyConfigId')
    return
  }
  if (isCheck.value && !checkFields.value?.validateNotify()) {
    await checkFields.value?.focusError('check.notifyConfigId')
    return
  }
  busy.value = true
  if (isEvent.value && !(await eventFields.value?.validatePeople())) {
    busy.value = false
    return
  }
  if (controller.signal.aborted || runtime.accessIssue.value) {
    busy.value = false
    return
  }
  error.value = null
  try {
    const result = record(
      await runtime.request(
        current.value
          ? '/api/tasks/' + current.value.id + '?expectedRevision=' + current.value.currentRevision
          : '/api/tasks',
        {
          method: current.value ? 'PUT' : 'POST',
          body: taskBody(draft, current.value),
          signal: controller.signal,
        },
      ),
    )
    exactId(result.taskId)
    exactId(result.definitionId)
    if (!controller.signal.aborted && !runtime.accessIssue.value)
      emit('saved', exactId(result.revision))
  } catch (e) {
    if (!controller.signal.aborted && !runtime.accessIssue.value) {
      error.value = e
      if (
        !(e instanceof ApiError) ||
        ['network', 'timeout', 'protocol'].includes(e.kind) ||
        e.status >= 500
      )
        uncertain.value = true
      message.error(() => h('span', { role: 'alert' }, describeError(e, t)))
    }
  } finally {
    busy.value = false
  }
}
async function loadLatest() {
  if (!current.value || busy.value) return
  busy.value = true
  try {
    latest.value = taskRow(
      await runtime.request('/api/tasks/' + current.value.id, { signal: controller.signal }),
      true,
    )
  } catch (e) {
    if (!controller.signal.aborted) message.error(describeError(e, t))
  } finally {
    busy.value = false
  }
}
async function adopt() {
  if (!latest.value || !(await confirmDiscard())) return
  current.value = latest.value
  Object.assign(draft, draftFor(latest.value))
  groupInput.value = ''
  groupError.value = ''
  baseline.value = JSON.stringify(draft)
  if (isApi.value) void loadService()
  latest.value = undefined
  error.value = null
  submitted.value = false
}
async function verifyPublication() {
  if (busy.value) return
  busy.value = true
  try {
    if (current.value)
      latest.value = taskRow(
        await runtime.request('/api/tasks/' + current.value.id, { signal: controller.signal }),
        true,
      )
    else
      await runtime.request(
        '/api/tasks?' + new URLSearchParams({ name: draft.name, pageNum: '1', pageSize: '20' }),
        { signal: controller.signal },
      )
    if (!controller.signal.aborted) message.info(t('eventTask.verified'))
  } catch (e) {
    if (!controller.signal.aborted) error.value = e
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <NDrawer
    show
    width="min(640px, 100vw)"
    class="task-drawer editor-drawer"
    :mask-closable="false"
    :close-on-esc="!busy && !large && !selectOpen"
    @update:show="close"
  >
    <NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="task-drawer-heading">
          <div class="task-heading-identity">
            <span>{{ t(current ? 'tasks.edit' : 'tasks.create') }}</span
            ><small v-if="current" class="task-identity-id">#{{ current.id }}</small>
          </div>
          <DrawerCloseButton :disabled="busy" @close="close" /></div
      ></template>
      <div ref="form" class="task-form">
        <p class="task-note">
          {{
            current
              ? t('tasks.publishNext', { revision: current.currentRevision })
              : t('tasks.publishNew')
          }}
        </p>
        <RequestError :error="error" />
        <div v-if="uncertain" role="alert">
          <p>{{ t('eventTask.unknown') }}</p>
          <NButton :disabled="busy" @click="verifyPublication">{{ t('eventTask.verify') }}</NButton>
          <p v-if="latest">{{ latest.name }} · v{{ latest.currentRevision }}</p>
        </div>
        <section v-if="conflict" class="task-conflict" role="alert">
          <p>{{ t('tasks.conflict') }}</p>
          <NButton :loading="busy" @click="loadLatest">{{ t('tasks.latest') }}</NButton>
          <template v-if="latest"
            ><p>{{ t('tasks.latestHint') }}</p>
            <pre>{{ JSON.stringify(latest, null, 2) }}</pre>
            <NButton :disabled="busy" @click="adopt">{{ t('tasks.adopt') }}</NButton></template
          >
        </section>
        <NForm :disabled="busy" :show-feedback="false" novalidate @submit.prevent="submit">
          <h3>{{ t('unified.basic') }}</h3>
          <FormItem
            id="task-name"
            :label="t('tasks.name')"
            required
            :invalid="!!fieldError('name')"
            :message="fieldError('name')"
            ><NInput
              v-model:value="draft.name"
              :maxlength="128"
              :input-props="{ id: 'task-name', 'aria-invalid': !!fieldError('name') }"
          /></FormItem>
          <div class="task-plugin-row">
            <FormItem id="task-pluginType" :label="t('tasks.pluginType')" required>
              <NSelect
                v-model:value="draft.pluginType"
                filterable
                :virtual-scroll="false"
                :options="pluginTypes.map((value) => ({ value, label: pluginLabel(value, t) }))"
                :disabled="busy || !!current"
                :input-props="{
                  id: 'task-pluginType',
                  'aria-label': t('tasks.pluginType'),
                  role: 'combobox',
                  'aria-expanded': selectOpen,
                }"
                :node-props="() => ({ role: 'option' })"
                @update:show="selectOpen = $event"
              />
            </FormItem>
            <div class="task-inline">
              <label for="task-enabled">{{ t('tasks.enable') }}</label
              ><NSwitch
                id="task-enabled"
                v-model:value="draft.enabled"
                :aria-label="t('tasks.enable')"
              />
            </div>
          </div>
          <h3 v-if="isScript || draft.pluginType === 'SQL' || isApi">{{ t('unified.target') }}</h3>
          <template v-if="isScript">
            <div class="task-advanced-grid">
              <FormItem id="task-executionMode" :label="t('tasks.executionMode')" required>
                <NSelect
                  v-model:value="draft.executionMode"
                  filterable
                  :options="[
                    { value: 'LOCAL', label: t('tasks.localMode') },
                    { value: 'SSH', label: t('tasks.sshMode') },
                  ]"
                  :input-props="{
                    id: 'task-executionMode',
                    'aria-label': t('tasks.executionMode'),
                  }"
                  @update:show="selectOpen = $event"
                />
              </FormItem>
              <FormItem
                id="task-engine"
                :label="t('tasks.engine')"
                required
                :invalid="!!fieldError('engine')"
                :message="fieldError('engine')"
              >
                <NSelect
                  v-model:value="scriptDraft.engine"
                  filterable
                  :options="engines"
                  :input-props="{ id: 'task-engine', 'aria-label': t('tasks.engine') }"
                  @update:show="selectOpen = $event"
                />
              </FormItem>
            </div>
            <p class="task-note">
              {{ t(draft.executionMode === 'SSH' ? 'tasks.sshModeHelp' : 'tasks.localModeHelp') }}
            </p>
          </template>
          <FormItem
            v-if="resourceType"
            id="task-resourceId"
            :label="
              t(isApi ? 'apiCall.resource' : isScript ? 'tasks.sshResource' : 'tasks.resource')
            "
            required
            :invalid="!!fieldError('resourceId')"
            :message="fieldError('resourceId')"
          >
            <NSelect
              :value="resourceId || null"
              filterable
              remote
              :options="options"
              :loading="resourceBusy"
              :disabled="busy"
              :placeholder="
                t(
                  isApi
                    ? 'apiCall.choose'
                    : isScript
                      ? 'tasks.chooseSshResource'
                      : 'tasks.chooseResource',
                )
              "
              :input-props="{
                id: 'task-resourceId',
                'aria-label': t(
                  isApi ? 'apiCall.resource' : isScript ? 'tasks.sshResource' : 'tasks.resource',
                ),
                'aria-invalid': !!fieldError('resourceId'),
              }"
              @search="searchResources"
              @scroll="moreResources"
              @update:value="resourceId = $event"
              @update:show="selectOpen = $event"
            />
            <p v-if="isApi" class="task-note field-hint">{{ t('apiCall.authHelp') }}</p></FormItem
          >
          <FormItem
            v-if="isScript && scriptDraft.engine === 'PYTHON'"
            id="task-interpreter"
            :label="t('tasks.interpreter')"
            required
            :invalid="!!fieldError('interpreter')"
            :message="fieldError('interpreter')"
          >
            <NInput
              v-model:value="scriptDraft.interpreter"
              :input-props="{ id: 'task-interpreter', 'aria-invalid': !!fieldError('interpreter') }"
            />
          </FormItem>
          <div v-if="isApi && serviceError" role="alert">
            {{ t('apiCall.metadataFailed') }}
            <NButton text type="primary" :disabled="serviceLoading || busy" @click="loadService">{{
              t('apiCall.retry')
            }}</NButton>
          </div>
          <LocalJarFields
            v-if="isLocalJar"
            ref="localJarFields"
            v-model:draft="draft.localJar"
            :errors="validation"
            :submitted="submitted"
            :disabled="busy || uncertain"
            @select="selectOpen = $event"
          />
          <MonitorFields
            v-if="isMonitor"
            ref="monitorFields"
            v-model:draft="draft.monitor"
            :errors="validation"
            :submitted="submitted"
            :disabled="busy || uncertain"
            @select="selectOpen = $event"
          />
          <BatchEtlFields
            v-if="isBatchEtl"
            ref="batchEtlFields"
            v-model:draft="draft.batchEtl"
            :errors="validation"
            :submitted="submitted"
            :disabled="busy"
            :retries="draft.retryTimes ?? 0"
            @select="selectOpen = $event"
          />
          <EtlFields
            v-if="isEtl"
            ref="etlFields"
            v-model:draft="draft.etl"
            :errors="validation"
            :submitted="submitted"
            :disabled="busy || uncertain"
            @expanded="large = $event"
            @select="selectOpen = $event"
          />
          <PushFields
            v-if="isPush"
            ref="pushFields"
            v-model:draft="draft.push"
            :errors="validation"
            :submitted="submitted"
            :disabled="busy || uncertain"
            @expanded="large = $event"
            @select="selectOpen = $event"
          />
          <FileTransferFields
            v-if="isTransfer"
            ref="transferFields"
            v-model:draft="draft.transfer"
            :errors="validation"
            :submitted="submitted"
            :disabled="busy || uncertain"
            @select="selectOpen = $event"
          />
          <DataCheckFields
            v-if="isCheck"
            ref="checkFields"
            v-model:draft="draft.check"
            :errors="validation"
            :submitted="submitted"
            :disabled="busy || uncertain"
            @expanded="large = $event"
            @select="selectOpen = $event"
          />
          <EventFields
            v-if="isEvent"
            ref="eventFields"
            v-model:draft="draft.event"
            v-model:timeout="draft.timeout"
            :errors="validation"
            :submitted="submitted"
            :disabled="busy || uncertain"
            @expanded="large = $event"
            @select="selectOpen = $event"
          />
          <ApiCallFields
            v-if="isApi"
            v-model:draft="draft.api"
            :errors="validation"
            :submitted="submitted"
            :disabled="busy"
            @expanded="large = $event"
            @select="selectOpen = $event"
          />
          <FormItem
            v-if="
              !isEvent &&
              !isCheck &&
              !isTransfer &&
              !isPush &&
              !isEtl &&
              !isBatchEtl &&
              !isMonitor &&
              !isLocalJar
            "
            v-show="!isApi || draft.api.protocol !== 'WEB_SERVICE'"
            :id="isApi ? 'task-body' : 'task-script'"
            :label="t(isApi ? 'apiCall.body' : isScript ? 'tasks.script' : 'tasks.sql')"
            :required="!isApi"
            :invalid="!!fieldError(isApi ? 'body' : 'script')"
            :message="fieldError(isApi ? 'body' : 'script')"
            ><JsonFormEditor
              v-if="isApi"
              ref="sql"
              v-model="scriptValue"
              :title="t('apiCall.body')"
              :disabled="busy"
              :invalid="!!fieldError('body')"
              context-key="API_CALL_HTTP"
              @expanded="large = $event"
              @select="selectOpen = $event" /><SqlEditor
              v-else
              ref="sql"
              v-model="scriptValue"
              :language="isApi ? 'JSON' : isScript ? scriptDraft.engine : 'SQL'"
              :context-key="isApi ? 'API_CALL_HTTP' : isScript ? draft.executionMode : 'SQL'"
              :disabled="busy"
              :invalid="!!fieldError(isApi ? 'body' : 'script')"
              @expanded="large = $event"
          /></FormItem>
          <p v-if="draft.pluginType === 'SQL'" class="task-note">
            {{ t('jsonForm.sqlPluginHint') }}
          </p>
          <p v-if="isApi && ['GET', 'HEAD'].includes(draft.api.http.method)" class="task-note">
            {{ t('apiCall.noBody') }}
          </p>
          <NCollapse v-model:expanded-names="expanded"
            ><NCollapseItem name="advanced" :title="t('tasks.advanced')">
              <ApiCallFields
                v-if="isApi"
                v-model:draft="draft.api"
                :errors="validation"
                :submitted="submitted"
                :disabled="busy"
                advanced
                @select="selectOpen = $event"
              />
              <RemoteStopFields
                v-if="isApi"
                ref="remoteStopFields"
                v-model:draft="draft.api"
                :errors="validation"
                :submitted="submitted"
                :disabled="busy"
                @select="selectOpen = $event"
                @expanded="large = $event"
              />
              <p v-if="isApi && (draft.retryTimes ?? 0) > 0" class="task-note">
                {{ t('apiCall.retryHelp') }}
              </p>
              <div class="task-advanced-grid">
                <template v-if="isScript">
                  <FormItem
                    v-for="key in ['scriptCharset', 'outputCharset'] as const"
                    :id="'task-' + key"
                    :key="key"
                    :label="t('tasks.' + key)"
                    :invalid="!!fieldError(key)"
                    :message="fieldError(key)"
                  >
                    <NSelect
                      v-model:value="scriptDraft[key]"
                      filterable
                      tag
                      :options="
                        ['UTF-8', 'GBK', 'GB18030', 'UTF-16LE', 'ISO-8859-1'].map((value) => ({
                          value,
                          label: value,
                        }))
                      "
                      :input-props="{ id: 'task-' + key, 'aria-label': t('tasks.' + key) }"
                      @update:show="selectOpen = $event"
                    />
                  </FormItem>
                </template>
                <FormItem
                  v-for="key in numbers"
                  :id="'task-' + key"
                  :key="key"
                  :label="t('tasks.' + key)"
                  :invalid="!!fieldError(key)"
                  :message="fieldError(key)"
                  ><NInputNumber
                    v-model:value="draft[key]"
                    :precision="0"
                    :input-props="{ id: 'task-' + key, 'aria-invalid': !!fieldError(key) }"
                /></FormItem>
                <FormItem
                  v-if="
                    !isScript &&
                    !isApi &&
                    !isEvent &&
                    !isCheck &&
                    !isTransfer &&
                    !isPush &&
                    !isEtl &&
                    !isBatchEtl &&
                    !isMonitor &&
                    !isLocalJar
                  "
                  id="task-delimiter"
                  :label="t('tasks.delimiter')"
                  :invalid="!!fieldError('delimiter')"
                  :message="fieldError('delimiter')"
                  ><NInput
                    v-model:value="draft.delimiter"
                    :maxlength="16"
                    :input-props="{
                      id: 'task-delimiter',
                      'aria-invalid': !!fieldError('delimiter'),
                    }"
                /></FormItem>
                <FormItem
                  v-if="
                    !isScript &&
                    !isApi &&
                    !isEvent &&
                    !isCheck &&
                    !isTransfer &&
                    !isPush &&
                    !isEtl &&
                    !isBatchEtl &&
                    !isMonitor &&
                    !isLocalJar
                  "
                  id="task-resultVariable"
                  :label="t('tasks.resultVariable')"
                  :invalid="!!fieldError('resultVariable')"
                  :message="fieldError('resultVariable')"
                  ><NInput
                    v-model:value="draft.resultVariable"
                    :maxlength="128"
                    :input-props="{
                      id: 'task-resultVariable',
                      'aria-invalid': !!fieldError('resultVariable'),
                    }"
                /></FormItem>
                <div
                  v-if="
                    !isScript &&
                    !isApi &&
                    !isEvent &&
                    !isCheck &&
                    !isTransfer &&
                    !isPush &&
                    !isEtl &&
                    !isBatchEtl &&
                    !isMonitor &&
                    !isLocalJar
                  "
                  class="task-inline"
                >
                  <label for="task-transaction">{{ t('tasks.transaction') }}</label
                  ><NSwitch
                    id="task-transaction"
                    v-model:value="draft.transaction"
                    :aria-label="t('tasks.transaction')"
                  />
                </div>
                <div
                  v-if="
                    !isScript &&
                    !isApi &&
                    !isEvent &&
                    !isCheck &&
                    !isTransfer &&
                    !isPush &&
                    !isEtl &&
                    !isBatchEtl &&
                    !isMonitor &&
                    !isLocalJar
                  "
                  class="task-inline"
                >
                  <label for="task-continue">{{ t('tasks.continueOnError') }}</label
                  ><NSwitch
                    id="task-continue"
                    v-model:value="draft.continueOnError"
                    :disabled="busy || draft.transaction"
                    :aria-disabled="busy || draft.transaction"
                    :aria-label="t('tasks.continueOnError')"
                  />
                </div>
              </div>
              <p
                v-if="
                  !isScript &&
                  !isApi &&
                  !isEvent &&
                  !isCheck &&
                  !isTransfer &&
                  !isPush &&
                  !isEtl &&
                  !isBatchEtl &&
                  !isMonitor &&
                  !isLocalJar
                "
                class="task-note task-transaction-hint"
              >
                {{ t('tasks.continueHint') }}
              </p>
              <template v-if="isScript">
                <FormItem
                  id="task-workingDir"
                  :label="t('tasks.workingDir')"
                  :invalid="!!fieldError('workingDir')"
                  :message="fieldError('workingDir')"
                >
                  <NInput
                    v-model:value="scriptDraft.workingDir"
                    :input-props="{
                      id: 'task-workingDir',
                      'aria-invalid': !!fieldError('workingDir'),
                    }"
                  />
                </FormItem>
                <p class="task-note">
                  {{
                    t(
                      draft.executionMode === 'SSH'
                        ? 'tasks.sshDirectoryHelp'
                        : 'tasks.localDirectoryHelp',
                    )
                  }}
                </p>
                <EditableTable
                  :title="t('tasks.environment')"
                  :columns="[
                    { key: 'name', label: t('tasks.envName'), required: true },
                    { key: 'value', label: t('tasks.envValue') },
                    { key: 'actions', label: t('common.actions') },
                  ]"
                  :count="scriptDraft.env.length"
                  :add-label="t('tasks.addEnv')"
                  :disabled="busy"
                  @add="scriptDraft.env.push({ name: '', value: '' })"
                >
                  <tr v-for="(entry, index) in scriptDraft.env" :key="index">
                    <td>
                      <FormItem
                        :id="'task-env-name-' + index"
                        hide-label
                        :label="t('tasks.envName')"
                        :invalid="!!fieldError('env-name-' + index)"
                        :message="fieldError('env-name-' + index)"
                      >
                        <NInput
                          v-model:value="entry.name"
                          :input-props="{
                            id: 'task-env-name-' + index,
                            'aria-label': t('tasks.envName') + ' ' + (index + 1),
                            'aria-invalid': !!fieldError('env-name-' + index),
                          }"
                        />
                      </FormItem>
                    </td>
                    <td>
                      <FormItem
                        :id="'task-env-value-' + index"
                        hide-label
                        :label="t('tasks.envValue')"
                        :invalid="!!fieldError('env-value-' + index)"
                        :message="fieldError('env-value-' + index)"
                      >
                        <NInput
                          v-model:value="entry.value"
                          :input-props="{
                            id: 'task-env-value-' + index,
                            'aria-label': t('tasks.envValue') + ' ' + (index + 1),
                            'aria-invalid': !!fieldError('env-value-' + index),
                          }"
                        />
                      </FormItem>
                    </td>
                    <td>
                      <NButton
                        text
                        type="error"
                        :disabled="busy"
                        :aria-label="t('tasks.removeEnv', { index: index + 1 })"
                        @click="scriptDraft.env.splice(index, 1)"
                        >{{ t('apiCall.remove') }}</NButton
                      >
                    </td>
                  </tr>
                  <template #feedback
                    ><p class="task-note">{{ t('tasks.envHelp') }}</p></template
                  >
                </EditableTable>
              </template>
              <FormItem
                id="task-groups"
                :label="t('tasks.group')"
                :invalid="!!groupError || !!fieldError('groups')"
                :message="groupError ? t('tasks.' + groupError) : fieldError('groups')"
              >
                <div v-if="groupValues.length" class="task-group-tags">
                  <NTag v-for="group in groupValues" :key="group" size="small">
                    <span class="task-group-code">{{ group }}</span>
                    <NButton
                      quaternary
                      circle
                      size="tiny"
                      :disabled="busy"
                      :aria-label="t('tasks.removeGroup', { group })"
                      @click="removeGroup(group)"
                      ><X :size="12"
                    /></NButton>
                  </NTag>
                </div>
                <div class="task-group-input">
                  <NInput
                    v-model:value="groupInput"
                    :placeholder="t('tasks.groupPlaceholder')"
                    :input-props="{
                      id: 'task-groups',
                      'aria-invalid': !!groupError || !!fieldError('groups'),
                      'aria-describedby': 'task-groups-help',
                    }"
                    @keydown="groupKey"
                  />
                  <NButton :disabled="busy || !groupInput.trim()" @click="addGroup">{{
                    t('tasks.addGroup')
                  }}</NButton>
                </div>
              </FormItem>
              <p id="task-groups-help" class="task-note">{{ t('tasks.groupHelp') }}</p>
              <FormItem
                id="task-description"
                :label="t('tasks.description')"
                :invalid="!!fieldError('description')"
                :message="fieldError('description')"
                ><NInput
                  v-model:value="draft.description"
                  type="textarea"
                  :maxlength="500"
                  :input-props="{
                    id: 'task-description',
                    'aria-invalid': !!fieldError('description'),
                  }"
              /></FormItem> </NCollapseItem
          ></NCollapse>
          <p
            v-if="
              !expanded.length &&
              !isEvent &&
              !isCheck &&
              !isTransfer &&
              !isPush &&
              !isEtl &&
              !isBatchEtl &&
              !isMonitor &&
              !isLocalJar
            "
            class="task-note"
          >
            {{
              isEvent
                ? t(
                    draft.event.action === 'PUBLISH'
                      ? 'eventTask.publishHint'
                      : 'eventTask.waitingHint',
                  )
                : isApi
                  ? t('apiCall.advancedSummary', {
                      size: draft.api.maxResponseBytes,
                      retry: draft.retryTimes,
                    })
                  : isScript
                    ? t('tasks.scriptSummary', {
                        encoding: scriptDraft.scriptCharset || 'UTF-8',
                        retries: draft.retryTimes,
                      })
                    : t('tasks.summary', {
                        transaction: t(draft.transaction ? 'tasks.on' : 'tasks.off'),
                        seconds: draft.queryTimeoutSeconds,
                        retries: draft.retryTimes,
                      })
            }}
          </p>
        </NForm>
      </div>
      <template #footer
        ><div class="task-footer">
          <NButton :disabled="busy" @click="close">{{ t('common.cancel') }}</NButton
          ><NButton
            type="primary"
            :loading="busy"
            :disabled="
              busy ||
              (!!current && !changed) ||
              conflict ||
              uncertain ||
              (isEvent && draft.event.blocked) ||
              (isCheck && draft.check.blocked) ||
              (isTransfer && draft.transfer.blocked) ||
              (isPush && draft.push.blocked) ||
              (isEtl && draft.etl.blocked) ||
              (isBatchEtl && draft.batchEtl.blocked) ||
              (isMonitor && draft.monitor.blocked) ||
              (isLocalJar && draft.localJar.blocked)
            "
            @click="submit"
            >{{ t('tasks.save') }}</NButton
          >
        </div></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
