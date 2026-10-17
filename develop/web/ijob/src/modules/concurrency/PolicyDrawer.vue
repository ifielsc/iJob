<script setup lang="ts">
import { randomUuid } from '../../shared/runtime/uuid'
import { pageData } from '../../shared/api/values'
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { pluginLabel } from '../../shared/plugins'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import { draftFingerprint } from '../../shared/forms/draft-fingerprint'
import IdentityText from '../../shared/ui/IdentityText.vue'
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  NButton,
  NDrawer,
  NDrawerContent,
  NInput,
  NForm,
  NSelect,
  NSwitch,
  useDialog,
  useMessage,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { ApiError, describeError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import {
  issues,
  limit,
  list,
  metadata,
  outcomeUnknown,
  plugin,
  resource,
  taskPolicy,
  versionConflict,
  type Draft,
  type Metadata,
  type Resource,
  type Target,
} from './model'
import { pendingWrites, targetKey, type Snapshot } from './memory'
import './concurrency.css'
const props = defineProps<{ target: Target; removeOnOpen?: boolean }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage()
const reader = computed(() => can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value)
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && reader.value)
const snapshot = shallowRef<Snapshot>(),
  latest = shallowRef<Snapshot | null>(),
  meta = shallowRef<Metadata>()
const candidateRows = ref<Resource[]>([]),
  selectedCandidates = ref<Resource[]>([])
const candidates = computed(() => [
  ...new Map([...selectedCandidates.value, ...candidateRows.value].map((r) => [r.id, r])).values(),
])
const candidateTotal = ref(0),
  candidateLoading = ref(false),
  candidateError = shallowRef<unknown>()
let candidatePage = 0,
  candidateKeyword = '',
  candidateAppend = false,
  candidateController: AbortController | undefined
async function loadCandidates(append = false) {
  candidateController?.abort()
  const current = new AbortController()
  candidateController = current
  candidateLoading.value = true
  candidateError.value = undefined
  candidateAppend = append
  const nextPage = append ? candidatePage + 1 : 1
  try {
    const result = pageData(
      await runtime.request(
        '/api/concurrency/resources/page?' +
          new URLSearchParams({
            keyword: candidateKeyword,
            pageNum: String(nextPage),
            pageSize: '20',
          }),
        { signal: current.signal },
      ),
      resource,
    )
    if (current.signal.aborted || identity !== runtime.session.revision) return
    candidateRows.value = append ? [...candidateRows.value, ...result.list] : result.list
    candidateTotal.value = result.total
    candidatePage = nextPage
  } catch (e) {
    if (!current.signal.aborted) candidateError.value = e
  } finally {
    if (!current.signal.aborted) candidateLoading.value = false
  }
}
function searchCandidates(keyword: string) {
  selectedCandidates.value = candidates.value.filter((r) => draft.resourceIds.includes(r.id))
  candidateKeyword = keyword
  void loadCandidates()
}
function moreCandidates(event: Event) {
  const el = event.target as HTMLElement
  if (
    !candidateLoading.value &&
    !candidateError.value &&
    candidateRows.value.length < candidateTotal.value &&
    el.scrollTop + el.clientHeight >= el.scrollHeight - 24
  )
    void loadCandidates(true)
}
const pluginMaximum = ref<number | null>(null)
const draft = reactive<Draft>({
  name: '',
  code: '',
  maximum: '',
  limited: false,
  enabled: true,
  description: '',
  resourceIds: [],
})
const initial = ref(''),
  errors = ref<Record<string, string>>({}),
  error = shallowRef<unknown>()
const busy = ref(false),
  loading = ref(false),
  unknown = ref(false),
  conflict = ref(false),
  deleting = ref(false),
  checked = ref(false),
  selectOpen = ref(false)
const missing = ref(false),
  form = ref<HTMLElement>(),
  closing = ref(false)
let controller: AbortController | undefined
const key = targetKey(props.target)
const identity = runtime.session.revision
const title = computed(() =>
  t(
    'concurrency.' +
      (props.target.kind === 'task'
        ? 'task'
        : props.target.kind === 'plugin'
          ? 'editPlugin'
          : props.target.id
            ? writer.value
              ? 'editResource'
              : 'viewResource'
            : 'add'),
  ),
)
const dirty = computed(() => JSON.stringify(draft) !== initial.value)
const unchanged = computed(
  () =>
    !(props.target.kind === 'resource' && !props.target.id) &&
    !!initial.value &&
    draftFingerprint(draft) === draftFingerprint(JSON.parse(initial.value)),
)
const blocked = computed(
  () =>
    busy.value ||
    loading.value ||
    unknown.value ||
    conflict.value ||
    missing.value ||
    !writer.value,
)
const active = computed(() => (latest.value ?? snapshot.value)?.active ?? 0)
const restrictive = computed(
  () =>
    props.target.kind === 'resource' &&
    snapshot.value &&
    (limit(draft.maximum)! < (snapshot.value.max ?? 0) ||
      (snapshot.value.enabled && !draft.enabled)),
)
const activityBlocked = computed(
  () => active.value > 0 && (props.target.kind !== 'resource' || restrictive.value),
)
const selectedResources = computed(() =>
  draft.resourceIds
    .map(
      (id) =>
        candidates.value.find((r) => r.id === id) ??
        snapshot.value?.resources.find((r) => r.id === id),
    )
    .filter((r): r is Resource => !!r),
)
const options = computed(() => {
  const merged = new Map((snapshot.value?.resources ?? []).map((r) => [r.id, r]))
  candidates.value.forEach((r) => merged.set(r.id, r))
  return [...merged.values()].map((r) => ({
    value: r.id,
    label: t('concurrency.resourceLabel', {
      name: r.name,
      maximum: r.maxConcurrency,
      disabled: r.enabled ? '' : t('concurrency.disabledSuffix'),
    }),
    disabled: !r.enabled,
  }))
})
function fill(s: Snapshot) {
  Object.assign(draft, {
    name: s.name,
    code: s.code,
    maximum: s.max == null ? '' : String(s.max),
    limited: s.max != null,
    enabled: s.enabled,
    description: s.description,
    resourceIds: s.resources.map((r) => r.id),
  })
  initial.value = JSON.stringify(draft)
}
function url() {
  const target = props.target
  return (
    '/api/concurrency/' +
    (target.kind === 'task'
      ? 'tasks/' + target.id
      : target.kind === 'plugin'
        ? 'plugin-policies/' + target.type
        : 'resources' + (target.id ? '/' + target.id : ''))
  )
}
async function readSnapshot(signal: AbortSignal): Promise<Snapshot | null> {
  const target = props.target
  const base: Snapshot = {
    name: '',
    code: '',
    max: null,
    enabled: true,
    description: '',
    version: '0',
    resources: [],
    pluginType: '',
    active: 0,
    references: 0,
  }
  if (target.kind === 'resource' && !target.id) {
    if (unknown.value) {
      let found: Resource | undefined
      try {
        found = resource(
          await runtime.request(
            '/api/concurrency/resources/by-code?' +
              new URLSearchParams({ code: draft.code.trim() }),
            { signal },
          ),
        )
      } catch (e) {
        if (!(e instanceof ApiError && e.status === 404)) throw e
      }
      if (found && found.code !== draft.code.trim()) throw new ApiError('protocol')
      return found
        ? {
            ...base,
            name: found.name,
            code: found.code,
            max: found.maxConcurrency,
            version: found.version,
            enabled: found.enabled,
            description: found.description,
            active: found.activeInstanceCount,
            references: found.referenceCount,
          }
        : null
    }
    return { ...base, code: 'con-' + randomUuid() }
  }
  let value: unknown
  try {
    value = await runtime.request(url(), { signal })
  } catch (e) {
    if (e instanceof ApiError && e.status === 404 && (unknown.value || conflict.value)) return null
    throw e
  }
  if (target.kind === 'resource') {
    const r = resource(value)
    if (r.id !== target.id) throw new ApiError('protocol')
    return {
      ...base,
      name: r.name,
      code: r.code,
      max: r.maxConcurrency,
      version: r.version,
      enabled: r.enabled,
      description: r.description,
      active: r.activeInstanceCount,
      references: r.referenceCount,
    }
  }
  if (target.kind === 'plugin') {
    const r = value == null ? null : plugin(value)
    if (r && r.pluginType !== target.type) throw new ApiError('protocol')
    return {
      ...base,
      name: target.type,
      pluginType: target.type,
      max: r?.maxConcurrency ?? null,
      version: r?.version ?? '0',
      active:
        r?.activeInstanceCount ??
        meta.value?.plugins.find((p) => p.type === target.type)?.activeInstanceCount ??
        0,
    }
  }
  const r = taskPolicy(value)
  if (r.taskId !== target.id) throw new ApiError('protocol')
  const [all, selected, policy] = await Promise.all([
    runtime.request('/api/concurrency/resources/page?pageNum=1&pageSize=20', { signal }),
    runtime.request(
      '/api/concurrency/resources/by-ids?' +
        new URLSearchParams({
          ids: [...new Set([...r.resources.map((v) => v.id), ...draft.resourceIds])].join(','),
        }),
      { signal },
    ),
    runtime.request('/api/concurrency/plugin-policies/' + r.pluginType, { signal }),
  ])
  const result = pageData(all, resource)
  selectedCandidates.value = list(selected, resource)
  candidateRows.value = result.list
  candidateTotal.value = result.total
  candidatePage = 1
  candidateKeyword = ''
  pluginMaximum.value = policy == null ? null : plugin(policy).maxConcurrency
  return {
    ...base,
    name: r.taskName,
    max: r.taskMaxConcurrency,
    version: r.version,
    resources: r.resources,
    pluginType: r.pluginType,
    active: r.activeInstanceCount,
  }
}
async function load(keep = false) {
  if (!reader.value || busy.value) return
  candidateController?.abort()
  candidateError.value = undefined
  candidateLoading.value = false
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = undefined
  checked.value = false
  try {
    meta.value = metadata(
      await runtime.request('/api/concurrency/metadata', { signal: current.signal }),
    )
    const value = await readSnapshot(current.signal)
    if (current.signal.aborted || identity !== runtime.session.revision) return
    if (keep) {
      latest.value = value
      checked.value = true
    } else if (value) {
      snapshot.value = value
      fill(value)
    }
    if (!value && !keep) missing.value = true
  } catch (e) {
    if (!current.signal.aborted) {
      error.value = e
      if (e instanceof ApiError && [403, 404].includes(e.status)) missing.value = true
    }
  } finally {
    if (!current.signal.aborted) loading.value = false
  }
}
const recovery = computed(() => unknown.value || conflict.value)
function comparisons(s: Snapshot | null | undefined) {
  if (!s) return [{ field: t('concurrency.policy'), value: t('concurrency.missing') }]
  return [
    { field: t('concurrency.version'), value: s.version },
    ...(props.target.kind === 'resource'
      ? [
          { field: t('concurrency.name'), value: s.name },
          { field: t('concurrency.code'), value: s.code },
          {
            field: t('concurrency.enabled'),
            value: t(s.enabled ? 'concurrency.on' : 'concurrency.off'),
          },
          { field: t('concurrency.description'), value: s.description || '—' },
        ]
      : []),
    {
      field: t('concurrency.maximum'),
      value:
        s.max == null
          ? t(props.target.kind === 'task' ? 'concurrency.unlimited' : 'concurrency.unconfigured')
          : String(s.max),
    },
    ...(props.target.kind === 'task'
      ? [
          {
            field: t('concurrency.shared'),
            value: s.resources.map((r) => r.name + ' · ' + r.id).join(', ') || '—',
          },
        ]
      : []),
  ]
}
const mine = computed(() =>
  comparisons(
    snapshot.value && {
      ...snapshot.value,
      name: draft.name,
      code: draft.code,
      enabled: draft.enabled,
      description: draft.description,
      max: props.target.kind === 'task' && !draft.limited ? null : (limit(draft.maximum) ?? null),
      resources: selectedResources.value,
    },
  ),
)
function confirmAction(content: string, action: () => void | Promise<void>) {
  dialog.warning({
    title: t('common.confirm'),
    content,
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: action,
  })
}
function reapply() {
  if (
    !checked.value ||
    !latest.value ||
    busy.value ||
    loading.value ||
    !writer.value ||
    (props.target.kind === 'resource' && !props.target.id)
  )
    return
  confirmAction(t('concurrency.reapplyHint'), () => {
    if (!latest.value || identity !== runtime.session.revision || !writer.value) return
    snapshot.value = latest.value
    latest.value = undefined
    unknown.value = false
    conflict.value = false
    deleting.value = false
    checked.value = false
    error.value = undefined
    pendingWrites(runtime).delete(key)
  })
}
async function submit(remove = false) {
  if (blocked.value || !snapshot.value || activityBlocked.value) return
  if (!remove) {
    errors.value = issues(draft, props.target.kind, candidates.value)
    if (Object.keys(errors.value).length) {
      await nextTick()
      form.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
      return
    }
  }
  if (
    remove &&
    (props.target.kind === 'task' ||
      snapshot.value.version === '0' ||
      (props.target.kind === 'resource' && snapshot.value.references > 0))
  )
    return
  const current = new AbortController()
  controller?.abort()
  controller = current
  busy.value = true
  error.value = undefined
  deleting.value = remove
  const target = props.target
  const method = remove ? 'DELETE' : target.kind === 'resource' && !target.id ? 'POST' : 'PUT'
  const body = remove
    ? undefined
    : target.kind === 'resource'
      ? {
          expectedVersion: snapshot.value.version === '0' ? undefined : snapshot.value.version,
          code: draft.code.trim(),
          name: draft.name.trim(),
          maxConcurrency: limit(draft.maximum),
          enabled: draft.enabled,
          description: draft.description || null,
        }
      : target.kind === 'plugin'
        ? { expectedVersion: snapshot.value.version, maxConcurrency: limit(draft.maximum) }
        : {
            expectedVersion: snapshot.value.version,
            taskMaxConcurrency: draft.limited ? limit(draft.maximum) : null,
            resourceIds: [...draft.resourceIds],
          }
  try {
    await runtime.request(url() + (remove ? '?expectedVersion=' + snapshot.value.version : ''), {
      method,
      body,
      signal: current.signal,
    })
    if (current.signal.aborted || identity !== runtime.session.revision) return
    pendingWrites(runtime).delete(key)
    message.success(t(remove ? 'concurrency.deleted' : 'concurrency.saved'))
    initial.value = JSON.stringify(draft)
    emit('saved')
    emit('close')
  } catch (e) {
    if (current.signal.aborted || identity !== runtime.session.revision) return
    error.value = e
    if (outcomeUnknown(e)) {
      unknown.value = true
      pendingWrites(runtime).set(key, {
        draft: JSON.parse(JSON.stringify(draft)) as Draft,
        snapshot: snapshot.value,
        deleting: remove,
      })
    } else {
      conflict.value = versionConflict(e)
      message.error(describeError(e, t))
    }
  } finally {
    if (!current.signal.aborted) busy.value = false
  }
}
function save() {
  if (unchanged.value) return
  if (props.target.kind === 'resource' && snapshot.value?.enabled && !draft.enabled)
    confirmAction(t('concurrency.resourceHint'), () => submit())
  else void submit()
}
function remove() {
  if (!snapshot.value || blocked.value) return
  confirmAction(
    t('concurrency.deletionHint', {
      name: snapshot.value.name,
      action: t(props.target.kind === 'plugin' ? 'concurrency.removeLimit' : 'concurrency.remove'),
      hint:
        props.target.kind === 'plugin'
          ? t('concurrency.pluginHint')
          : t('concurrency.resourceHint'),
    }),
    () => submit(true),
  )
}
async function allowClose() {
  if (!reader.value) return true
  if (busy.value) return false
  if ((!dirty.value || !writer.value) && !unknown.value) return true
  return new Promise<boolean>((resolve) =>
    dialog.warning({
      title: t('common.discard'),
      content: t(unknown.value ? 'concurrency.leaveUnknown' : 'common.discardHint'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
      onEsc: () => resolve(false),
      onMaskClick: () => resolve(false),
    }),
  )
}
async function close() {
  if (closing.value) return
  closing.value = true
  try {
    if (await allowClose()) emit('close')
  } finally {
    closing.value = false
  }
}
onBeforeRouteLeave(allowClose)
const unload = (e: BeforeUnloadEvent) => {
  if (writer.value && (dirty.value || busy.value || unknown.value)) e.preventDefault()
}
window.addEventListener('beforeunload', unload)
watch([() => runtime.session.revision, () => runtime.accessIssue.value, reader], () => {
  if (runtime.session.revision !== identity || !reader.value) {
    controller?.abort()
    snapshot.value = undefined
    latest.value = undefined
    candidateController?.abort()
    candidateRows.value = []
    selectedCandidates.value = []
    Object.assign(draft, { name: '', code: '', maximum: '', description: '', resourceIds: [] })
    pendingWrites(runtime)
    dialog.destroyAll()
    emit('close')
  }
})
onBeforeUnmount(() => {
  candidateController?.abort()
  controller?.abort()
  window.removeEventListener('beforeunload', unload)
})
const remembered = pendingWrites(runtime).get(key)
if (remembered) {
  snapshot.value = remembered.snapshot
  fill(remembered.snapshot)
  Object.assign(draft, remembered.draft)
  unknown.value = true
  deleting.value = remembered.deleting
  void load(true)
} else
  void load().then(() => {
    if (props.removeOnOpen && snapshot.value && writer.value) remove()
  })
</script>

<template>
  <NDrawer
    :show="true"
    class="concurrency-drawer editor-drawer"
    width="min(520px, 100vw)"
    :mask-closable="!writer"
    :close-on-esc="!busy && !selectOpen"
    :trap-focus="true"
    @update:show="close"
  >
    <NDrawerContent
      ><template #header
        ><div class="concurrency-drawer-title">
          <h2>{{ title }}</h2>
          <DrawerCloseButton :disabled="busy" @close="close" /></div
      ></template>
      <div ref="form" class="concurrency-form">
        <RequestError :error="error" />
        <p v-if="loading && !snapshot" role="status">{{ t('common.loading') }}</p>
        <NButton v-if="!snapshot && !loading" @click="load()">{{
          t('concurrency.review')
        }}</NButton>
        <NForm
          v-if="snapshot && reader"
          class="concurrency-fields"
          :show-feedback="false"
          novalidate
          @submit.prevent="save"
        >
          <h3>{{ t('unified.basic') }}</h3>
          <div v-if="target.kind !== 'resource'" class="concurrency-identity">
            <strong>{{ snapshot.name }}</strong
            ><small
              >{{ pluginLabel(snapshot.pluginType || '', t) }}
              <template v-if="target.kind === 'task'"> · ID {{ target.id }}</template> ·
              {{ t('concurrency.version') }} {{ snapshot.version }}</small
            >
          </div>
          <p v-if="target.kind === 'task'" class="concurrency-info">
            {{ t('concurrency.taskHint') }}
          </p>
          <template v-if="target.kind === 'resource'">
            <div class="unified-basic-row">
              <FormItem
                id="concurrency-name"
                :label="t('concurrency.name')"
                required
                :invalid="!!errors.name"
                :message="errors.name ? t('concurrency.' + errors.name) : undefined"
              >
                <NInput
                  v-model:value="draft.name"
                  :disabled="blocked"
                  :input-props="{ id: 'concurrency-name', 'aria-invalid': !!errors.name }"
                />
              </FormItem>
              <div class="resource-enabled concurrency-enabled">
                <label for="concurrency-enabled">{{ t('concurrency.enabled') }}</label>
                <NSwitch
                  id="concurrency-enabled"
                  v-model:value="draft.enabled"
                  :disabled="blocked"
                  :aria-label="t('concurrency.enabled')"
                />
              </div>
            </div>
            <FormItem
              id="concurrency-code"
              :label="t('concurrency.code')"
              required
              :invalid="!!errors.code"
              :message="errors.code ? t('concurrency.' + errors.code) : undefined"
            >
              <NInput
                v-model:value="draft.code"
                :disabled="blocked"
                :input-props="{ id: 'concurrency-code', 'aria-invalid': !!errors.code }"
              />
            </FormItem>
            <h3>{{ t('concurrency.policy') }}</h3>
          </template>
          <div v-if="target.kind === 'task'">
            <label for="concurrency-mode">{{ t('concurrency.taskMode') }}</label
            ><CompactSelect
              id="concurrency-mode"
              :label="t('concurrency.taskMode')"
              :value="draft.limited ? 'limited' : 'unlimited'"
              :options="[
                { value: 'limited', label: t('concurrency.limited') },
                { value: 'unlimited', label: t('concurrency.unlimited') },
              ]"
              :disabled="blocked || active > 0"
              @show="selectOpen = $event"
              @update:value="draft.limited = $event === 'limited'"
            />
          </div>
          <FormItem
            v-if="target.kind !== 'task' || draft.limited"
            id="concurrency-maximum"
            :label="t('concurrency.maximum')"
            required
            :invalid="!!errors.maximum"
            :message="errors.maximum ? t('concurrency.' + errors.maximum) : undefined"
          >
            <NInput
              v-model:value="draft.maximum"
              :allow-input="(value: string) => /^\d*$/.test(value)"
              :disabled="blocked || (target.kind !== 'resource' && active > 0)"
              :input-props="{
                id: 'concurrency-maximum',
                inputmode: 'numeric',
                'aria-invalid': !!errors.maximum,
              }"
            />
            <p class="concurrency-help">{{ t('concurrency.range') }}</p>
          </FormItem>
          <template v-if="target.kind === 'resource'">
            <p class="concurrency-help">{{ t('concurrency.resourceHint') }}</p>
            <FormItem
              id="concurrency-description"
              :label="t('concurrency.description')"
              :invalid="!!errors.description"
              :message="errors.description ? t('concurrency.' + errors.description) : undefined"
            >
              <NInput
                v-model:value="draft.description"
                type="textarea"
                :rows="3"
                :disabled="blocked"
                :input-props="{
                  id: 'concurrency-description',
                  'aria-invalid': !!errors.description,
                }"
              />
            </FormItem>
            <p class="concurrency-help">
              {{ t('concurrency.counts', { references: snapshot.references, active }) }}
            </p>
          </template>
          <template v-if="target.kind === 'task'">
            <div>
              <label for="concurrency-resources">{{ t('concurrency.shared') }}</label
              ><NSelect
                v-model:value="draft.resourceIds"
                multiple
                filterable
                remote
                :loading="candidateLoading"
                :options="options"
                :disabled="blocked || active > 0"
                :input-props="{
                  id: 'concurrency-resources',
                  'aria-label': t('concurrency.shared'),
                  'aria-invalid': !!errors.resources,
                }"
                @search="searchCandidates"
                @scroll="moreCandidates"
                @update:show="selectOpen = $event"
              />
              <RequestError :error="candidateError" />
              <NButton
                v-if="candidateError"
                :loading="candidateLoading"
                @click="loadCandidates(candidateAppend)"
                >{{ t('common.retry') }}</NButton
              >
              <p class="concurrency-help">{{ t('concurrency.sharedHint') }}</p>
              <p v-if="errors.resources" class="field-error">
                {{ t('concurrency.' + errors.resources) }}
              </p>
            </div>
            <section>
              <h3>{{ t('concurrency.layers') }}</h3>
              <table class="concurrency-table">
                <thead>
                  <tr>
                    <th>{{ t('concurrency.scope') }}</th>
                    <th>{{ t('concurrency.maximum') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>
                      {{ t('concurrency.plugin') }} ·
                      {{ pluginLabel(snapshot.pluginType || '', t) }}
                    </td>
                    <td>{{ pluginMaximum ?? t('concurrency.unconfigured') }}</td>
                  </tr>
                  <tr>
                    <td>{{ t('concurrency.thisTask') }}</td>
                    <td>{{ draft.limited ? draft.maximum || '—' : t('concurrency.unlimited') }}</td>
                  </tr>
                  <tr v-for="r in selectedResources" :key="r.id">
                    <td>
                      {{ r.name }}<span v-if="!r.enabled"> · {{ t('concurrency.off') }}</span>
                    </td>
                    <td>{{ r.maxConcurrency }}</td>
                  </tr>
                </tbody>
              </table>
              <p class="concurrency-help">{{ t('concurrency.layersHint') }}</p>
              <p class="concurrency-help">{{ t('concurrency.noTaskHint') }}</p>
            </section>
          </template>
          <p v-if="target.kind === 'plugin'" class="concurrency-help">
            {{ t('concurrency.pluginHint') }}
          </p>
          <p v-if="target.kind !== 'resource'" class="concurrency-help">
            {{ t('concurrency.active', { count: active }) }}
          </p>
          <p v-if="active > 0" class="concurrency-warning" role="status">
            {{
              t(target.kind === 'resource' ? 'concurrency.resourceBusy' : 'concurrency.policyBusy')
            }}
          </p>
          <p class="concurrency-help">{{ t('concurrency.activityHint') }}</p>
          <NButton
            v-if="active > 0 && !recovery"
            :loading="loading"
            :disabled="busy"
            @click="load(true)"
            >{{ t('concurrency.review') }}</NButton
          >
          <section v-if="recovery" class="concurrency-warning" role="alert">
            <strong>{{ t(unknown ? 'concurrency.unknown' : 'concurrency.conflict') }}</strong>
            <p>{{ t(unknown ? 'concurrency.unknownHint' : 'concurrency.conflictHint') }}</p>
            <NButton :loading="loading" :disabled="busy" @click="load(true)">{{
              t('concurrency.review')
            }}</NButton>
            <template v-if="checked"
              ><p v-if="unknown">{{ t('concurrency.unknownChecked') }}</p>
              <table class="concurrency-table">
                <thead>
                  <tr>
                    <th>{{ t('concurrency.field') }}</th>
                    <th>{{ t('concurrency.mine') }}</th>
                    <th>{{ t('concurrency.latest') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(item, index) in mine" :key="item.field">
                    <td>{{ item.field }}</td>
                    <td>{{ item.value }}</td>
                    <td>{{ comparisons(latest)[index]?.value ?? t('concurrency.missing') }}</td>
                  </tr>
                </tbody>
              </table>
              <p v-if="target.kind === 'resource' && !target.id">
                {{ t('concurrency.unknownCreate') }}
              </p>
              <NButton
                v-else-if="latest && !deleting"
                :disabled="loading || !writer"
                @click="reapply"
                >{{ t('concurrency.reapply') }}</NButton
              ></template
            >
          </section>
          <details class="unified-technical">
            <summary>{{ t('unified.technical') }}</summary>
            <IdentityText v-if="target.kind === 'resource' && target.id" :value="target.id" />
            <p>{{ t('concurrency.version') }} {{ snapshot.version }}</p>
            <p v-if="meta" class="concurrency-help">
              {{
                t('concurrency.metadata', {
                  seconds: meta.permitWaitTimeoutMillis / 1000,
                  retry: meta.retryIntervalMillis,
                  max: meta.maxRetryIntervalMillis,
                  jitter: meta.retryJitterMillis,
                })
              }}
            </p>
          </details>
        </NForm>
      </div>
      <template #footer
        ><div class="concurrency-footer">
          <NButton
            v-if="writer && snapshot && target.kind === 'plugin' && snapshot.version !== '0'"
            text
            type="error"
            :disabled="blocked || active > 0"
            :title="snapshot.references > 0 ? t('concurrency.referenced') : undefined"
            @click="remove"
            >{{
              t(target.kind === 'plugin' ? 'concurrency.removeLimit' : 'concurrency.remove')
            }}</NButton
          ><span /><NButton :disabled="busy" @click="close">{{
            t(writer && !unknown ? 'common.cancel' : 'common.close')
          }}</NButton
          ><NButton
            v-if="writer"
            type="primary"
            :loading="busy"
            :disabled="blocked || activityBlocked || !snapshot || unchanged"
            @click="save"
            >{{ t('common.save') }}</NButton
          >
        </div></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
