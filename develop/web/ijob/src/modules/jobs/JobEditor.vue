<script setup lang="ts">
import { randomUuid } from '../../shared/runtime/uuid'
import { hasActiveConditionDraft } from './condition-drafts'
import { draftFingerprint } from '../../shared/forms/draft-fingerprint'
import { parse as parseLossless } from 'lossless-json'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NCheckbox,
  NInput,
  NInputNumber,
  NSelect,
  NModal,
  NSwitch,
  useDialog,
  useMessage,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { X } from '@lucide/vue'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { ApiError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import LineTabs from '../../shared/ui/LineTabs.vue'
import { EntityPicker } from '../schedules/public/components'
import { exactId, record } from '../../shared/api/values'
import { formatUtc } from '../../shared/time/format'
import JobCanvas from './JobCanvas.vue'
import ConditionEditor from './ConditionEditor.vue'
import {
  freshDraft,
  jobDetail,
  jobBody,
  issues,
  json,
  renameNode,
  type JobRow,
  type JobNode,
  type JobEdge,
  type Issue,
} from './model'
import { connectionProblem, isTextTarget } from './graph-tools'
import './jobs.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  route = useRoute(),
  router = useRouter(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage()
const draft = ref(freshDraft()),
  row = ref<JobRow>(),
  publishedAt = ref(''),
  currentVersion = ref(false),
  baseline = ref(''),
  loading = ref(false),
  busy = ref(false),
  error = shallowRef<unknown>(),
  metadata = ref<Record<string, unknown>>(),
  checked = ref(false),
  localIssues = ref<Issue[]>([]),
  serverIssues = ref<{ path: string; errorCode: string; message?: string }[]>([]),
  references = ref<Record<string, unknown>[]>([]),
  uncertain = ref(false)
const selectedKey = ref(''),
  selectedEdge = ref(''),
  panel = ref(!window.matchMedia('(max-width: 640px)').matches),
  panelWidth = ref(360),
  maximized = ref(false),
  expandedCondition = ref(false),
  direction = ref<'LR' | 'TB'>('LR'),
  tab = ref('job'),
  nodeKeyDraft = ref(''),
  connection = ref({ from: '', to: '' }),
  canvas = ref<InstanceType<typeof JobCanvas>>(),
  root = ref<HTMLElement>(),
  conditionErrors = ref<Record<string, boolean>>({}),
  conditionDrafts = ref<Record<string, Record<string, string>>>({})
const editing = computed(() => route.name === 'job-editor' || route.name === 'job-edit-existing'),
  historical = computed(() => route.name === 'job-version'),
  allowed = computed(
    () => editing.value && can(user.value, 'CONFIGURATION_WRITE') && !runtime.accessIssue.value,
  ),
  disabled = computed(
    () => !allowed.value || busy.value || loading.value || !metadata.value || uncertain.value,
  )
const serializedDraft = computed(() => json(editableDraft()))
const baselineFingerprint = computed(() =>
  baseline.value ? draftFingerprint(parseLossless(baseline.value)) : '',
)
const currentFingerprint = computed(() => draftFingerprint(parseLossless(serializedDraft.value)))
const dirty = computed(
  () =>
    editing.value &&
    !!baseline.value &&
    (draftSnapshot() !== baseline.value ||
      hasActiveConditionDraft(draft.value.edges, conditionDrafts.value)),
)
const changed = computed(
  () =>
    !!baseline.value &&
    (currentFingerprint.value !== baselineFingerprint.value ||
      hasActiveConditionDraft(draft.value.edges, conditionDrafts.value)),
)
function draftSnapshot() {
  return serializedDraft.value
}
function editableDraft() {
  // Include editable graph data, never asynchronous reference display metadata.
  return {
    ...draft.value,
    nodes: draft.value.nodes.map((n) => ({
      nodeId: n.nodeId,
      name: n.name,
      kind: n.kind,
      nodeType: n.nodeType,
      refId: n.refId,
      policy: n.policy,
      gateway: n.gateway,
      position: n.position,
    })),
  }
}
function conditionDraft(id: string) {
  return (conditionDrafts.value[id] ??= {})
}
const selected = computed(() => draft.value.nodes.find((n) => n.nodeId === selectedKey.value)),
  edge = computed(() => draft.value.edges.find((e) => e.id === selectedEdge.value)),
  outgoing = computed(() => draft.value.edges.filter((e) => e.fromNodeId === selectedKey.value)),
  nodeOptions = computed(() =>
    draft.value.nodes.map((n) => ({ value: n.nodeId, label: n.name + ' · ' + n.nodeId })),
  )
const nodeNames = computed(() => new Map(draft.value.nodes.map((n) => [n.nodeId, n.name])))
const connectionOptions = computed(() =>
  draft.value.edges.map((e) => ({
    value: e.id,
    label:
      (nodeNames.value.get(e.fromNodeId) ?? e.fromNodeId) +
      ' → ' +
      (nodeNames.value.get(e.toNodeId) ?? e.toNodeId) +
      (e.routeKey ? ' · ' + e.routeKey : ''),
  })),
)
const searchRevision = ref(0)
const searchOptions = computed(() => [
  {
    type: 'group' as const,
    label: t('jobs.node'),
    key: 'nodes',
    children: nodeOptions.value.map((o) => ({ ...o, value: 'n:' + o.value })),
  },
  {
    type: 'group' as const,
    label: t('jobs.connections'),
    key: 'edges',
    children: connectionOptions.value.map((o) => ({ ...o, value: 'e:' + o.value })),
  },
])
function findItem(value: string) {
  searchRevision.value++
  if (value.startsWith('n:')) pickNode(value.slice(2))
  else pickEdge(value.slice(2))
}
function pickNode(key: string) {
  select(key)
  void nextTick(() => canvas.value?.locate([key]))
}
function pickEdge(id: string) {
  selectEdge(id)
  const e = draft.value.edges.find((e) => e.id === id)
  if (e) void nextTick(() => canvas.value?.locate([e.fromNodeId, e.toNodeId]))
}
function locateSelected() {
  const keys = selectedKey.value
    ? [selectedKey.value]
    : edge.value
      ? [edge.value.fromNodeId, edge.value.toNodeId]
      : []
  if (keys.length) void canvas.value?.locate(keys)
}
function setPorts(source: string, target: string) {
  draft.value.edges.forEach((e) => {
    e.sourceHandle = source
    e.targetHandle = target
  })
}
let resizeAbort: AbortController | undefined
function resizePanel(event: PointerEvent) {
  resizeAbort?.abort()
  resizeAbort = new AbortController()
  const start = event.clientX,
    width = panelWidth.value
  window.addEventListener(
    'pointermove',
    (e) => {
      panelWidth.value = Math.max(320, Math.min(480, width + start - e.clientX))
    },
    { signal: resizeAbort.signal },
  )
  window.addEventListener('pointerup', () => resizeAbort?.abort(), {
    once: true,
    signal: resizeAbort.signal,
  })
}
const history = ref<string[]>([]),
  future = ref<string[]>([])
let historyTimer: ReturnType<typeof setTimeout> | undefined,
  restoring = false
function historySnapshot() {
  return json({
    draft: draft.value,
    errors: conditionErrors.value,
    literals: conditionDrafts.value,
  })
}
function recordHistory() {
  clearTimeout(historyTimer)
  if (restoring || loading.value || !editing.value || !baseline.value) return
  const snapshot = historySnapshot()
  if (history.value.at(-1) !== snapshot) {
    history.value.push(snapshot)
    if (history.value.length > 40) history.value.shift()
    future.value = []
  }
}
watch(
  [serializedDraft, conditionErrors, conditionDrafts],
  () => {
    if (!restoring && !loading.value) {
      clearTimeout(historyTimer)
      historyTimer = setTimeout(recordHistory, 300)
    }
  },
  { deep: true },
)
async function restoreHistory(snapshot: string) {
  restoring = true
  // Draft IDs and condition literals are already strings. Restore UI numbers
  // (positions, priorities, timeout) as numbers, not LosslessNumber objects.
  const state = JSON.parse(snapshot) as {
    draft: typeof draft.value
    errors: typeof conditionErrors.value
    literals: typeof conditionDrafts.value
  }
  draft.value = state.draft
  conditionErrors.value = state.errors
  conditionDrafts.value = state.literals
  if (!draft.value.nodes.some((n) => n.nodeId === selectedKey.value)) selectedKey.value = ''
  if (!draft.value.edges.some((e) => e.id === selectedEdge.value)) selectedEdge.value = ''
  await nextTick()
  restoring = false
}
function undo() {
  if (disabled.value) return
  recordHistory()
  if (history.value.length < 2) return
  future.value.push(history.value.pop()!)
  void restoreHistory(history.value.at(-1)!)
}
function redo() {
  if (disabled.value || !future.value.length) return
  clearTimeout(historyTimer)
  const snapshot = future.value.pop()!
  history.value.push(snapshot)
  void restoreHistory(snapshot)
}
function shortcut(event: KeyboardEvent) {
  if (
    isTextTarget(event.target) ||
    document.querySelector('.n-dialog, .n-modal') ||
    disabled.value ||
    event.repeat
  )
    return
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
    event.preventDefault()
    if (event.shiftKey) redo()
    else undo()
  } else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'y') {
    event.preventDefault()
    redo()
  }
}
let deleting = false
async function removeSelected(keys: string[] = []) {
  if (deleting || disabled.value) return
  deleting = true
  try {
    if (keys.length > 1) {
      const ids = new Set(keys),
        connected = draft.value.edges.filter((e) => ids.has(e.fromNodeId) || ids.has(e.toNodeId))
      if (
        await confirm(
          t('jobs.removeNode'),
          t('jobs.removeMany', { count: keys.length, edges: connected.length }),
        )
      ) {
        draft.value.nodes = draft.value.nodes.filter((n) => !ids.has(n.nodeId))
        draft.value.edges = draft.value.edges.filter((e) => !connected.includes(e))
        selectedKey.value = ''
        selectedEdge.value = ''
      }
    } else if (selectedKey.value) await removeNode()
    else if (selectedEdge.value) await removeEdge(selectedEdge.value)
  } finally {
    deleting = false
  }
}
const policies = (values: string[]) => values.map((value) => ({ value, label: t('jobs.' + value) }))
let controller = new AbortController(),
  refController: AbortController | undefined
function confirm(title: string, content: string) {
  if (document.querySelector('.n-dialog')) return Promise.resolve(false)
  return new Promise<boolean>((resolve) =>
    dialog.warning({
      title,
      content,
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
async function allowLeave() {
  return (
    !busy.value && (!dirty.value || (await confirm(t('common.discard'), t('common.discardHint'))))
  )
}
onBeforeRouteLeave(() => (runtime.accessIssue.value ? true : allowLeave()))
onBeforeRouteUpdate(() => (runtime.accessIssue.value ? true : allowLeave()))
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) e.preventDefault()
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => {
  clearTimeout(historyTimer)
  resizeAbort?.abort()
  controller.abort()
  refController?.abort()
  window.removeEventListener('beforeunload', beforeUnload)
  dialog.destroyAll()
  message.destroyAll()
})
watch([() => runtime.session.revision, () => runtime.accessIssue.value], () => {
  controller.abort()
  refController?.abort()
  history.value = []
  future.value = []
  clearTimeout(historyTimer)
  draft.value = freshDraft()
  row.value = undefined
  baseline.value = ''
  references.value = []
  serverIssues.value = []
  dialog.destroyAll()
})
watch(
  () => selected.value?.nodeId,
  (key) => (nodeKeyDraft.value = key ?? ''),
)
watch(
  () => json(draft.value),
  () => {
    checked.value = false
    serverIssues.value = []
    localIssues.value = []
  },
)
async function load() {
  controller.abort()
  refController?.abort()
  controller = new AbortController()
  const signal = controller.signal
  loading.value = true
  history.value = []
  future.value = []
  clearTimeout(historyTimer)
  error.value = undefined
  metadata.value = undefined
  baseline.value = ''
  row.value = undefined
  uncertain.value = false
  selectedKey.value = ''
  selectedEdge.value = ''
  conditionErrors.value = {}
  tab.value = 'job'
  conditionDrafts.value = {}
  draft.value = freshDraft()
  try {
    let layout = false
    if (route.params.id) {
      const id = exactId(route.params.id),
        definition = route.params.definitionId ? exactId(route.params.definitionId) : undefined
      const detail = jobDetail(
        await runtime.request('/api/jobs/' + id + (definition ? '/versions/' + definition : ''), {
          signal,
          lossless: true,
        }),
      )
      if (signal.aborted) return
      draft.value = detail.draft
      row.value = detail.row
      publishedAt.value = detail.publishedAt
      currentVersion.value = detail.current
      layout = detail.hasLayout
    }
    if (editing.value) {
      metadata.value = record(await runtime.request('/api/jobs/metadata', { signal }))
      if (signal.aborted) return
    }
    await nextTick()
    if (!layout && draft.value.nodes.length) await canvas.value?.autoLayout()
    baseline.value = draftSnapshot()
    history.value = [historySnapshot()]
  } catch (e) {
    if (!signal.aborted && !runtime.accessIssue.value) error.value = e
  } finally {
    if (!signal.aborted) loading.value = false
  }
}
watch(
  () => [route.params.id, route.params.definitionId, route.name],
  () => void load(),
  { immediate: true },
)
function clearSelection() {
  selectedKey.value = ''
  selectedEdge.value = ''
  tab.value = 'job'
}
function select(key: string) {
  selectedKey.value = key
  selectedEdge.value = ''
  tab.value = 'node'
  panel.value = true
  connection.value.from = key
}
function selectEdge(id: string) {
  selectedEdge.value = id
  selectedKey.value = ''
  tab.value = 'node'
  panel.value = true
}
function move(key: string, position: { x: number; y: number }) {
  const node = draft.value.nodes.find((n) => n.nodeId === key)
  if (node) node.position = { ...position }
}
function add(kind: 'TASK' | 'JOB' | 'GATEWAY') {
  if (disabled.value || draft.value.nodes.length >= 500) return
  const key = 'node-' + randomUuid().slice(0, 8)
  const node: JobNode = {
    nodeId: key,
    name: t(
      kind === 'GATEWAY' ? 'jobs.addGateway' : kind === 'JOB' ? 'jobs.addJob' : 'jobs.addTask',
    ),
    kind: kind === 'GATEWAY' ? 'GATEWAY' : 'EXECUTION',
    position: canvas.value?.centerPosition() ?? { x: 40, y: 80 },
    ...(kind === 'GATEWAY'
      ? { gateway: { mode: 'EXCLUSIVE' as const } }
      : {
          nodeType: kind,
          refId: '',
          policy: { executeCondition: 'ALL_SUCCESS' as const, failStrategy: 'STOP' as const },
        }),
  }
  draft.value.nodes.push(node)
  select(key)
  void nextTick(() => canvas.value?.locate([key]))
}
function connect(from: string, to: string, sourceHandle = 'right', targetHandle = 'left') {
  if (disabled.value || !from || !to) return
  const problem = connectionProblem(draft.value.edges, from, to)
  if (problem) {
    message.error(t('jobs.' + problem))
    return
  }
  const gateway = draft.value.nodes.find((n) => n.nodeId === from)?.kind === 'GATEWAY',
    outgoing = draft.value.edges.filter((e) => e.fromNodeId === from),
    fallback = gateway && !outgoing.some((e) => e.defaultRoute)
  const e: JobEdge = {
    id: randomUuid(),
    fromNodeId: from,
    toNodeId: to,
    conditionText: '',
    sourceHandle,
    targetHandle,
    ...(gateway
      ? {
          routeKey: fallback ? 'fallback' : 'route-' + (outgoing.length + 1),
          priority: (outgoing.length + 1) * 10,
          defaultRoute: fallback,
          conditionText: fallback ? '' : json({ op: 'EXISTS', left: { var: 'input.value' } }),
        }
      : {}),
  }
  draft.value.edges.push(e)
  selectEdge(e.id)
}
async function removeNode() {
  if (!selected.value || disabled.value) return
  const node = selected.value
  if (
    !(await confirm(
      t('jobs.removeNode'),
      t('jobs.removeNodeCount', {
        name: node.name,
        count: draft.value.edges.filter(
          (e) => e.fromNodeId === node.nodeId || e.toNodeId === node.nodeId,
        ).length,
      }),
    ))
  )
    return
  draft.value.nodes = draft.value.nodes.filter((n) => n !== node)
  draft.value.edges = draft.value.edges.filter(
    (e) => e.fromNodeId !== node.nodeId && e.toNodeId !== node.nodeId,
  )
  selectedKey.value = ''
  tab.value = 'job'
}
async function removeEdge(id: string) {
  if (disabled.value) return
  const item = draft.value.edges.find((e) => e.id === id)
  if (
    !item ||
    !(await confirm(
      t('jobs.removeEdge'),
      `${nodeNames.value.get(item.fromNodeId)} → ${nodeNames.value.get(item.toNodeId)}${item.routeKey ? ' · ' + item.routeKey + ' · ' + t('jobs.removeRouteCondition') : ''}`,
    ))
  )
    return
  draft.value.edges = draft.value.edges.filter((e) => e.id !== id)
  delete conditionErrors.value[id]
  if (selectedEdge.value === id) selectedEdge.value = ''
}
function changeDefault(e: JobEdge, value: boolean) {
  if (disabled.value) return
  if (value)
    draft.value.edges
      .filter((v) => v.fromNodeId === e.fromNodeId)
      .forEach((v) => (v.defaultRoute = v === e))
  else e.defaultRoute = false
  if (!value && !e.conditionText)
    e.conditionText = json({ op: 'EXISTS', left: { var: 'input.value' } })
}
function rename() {
  if (!selected.value || disabled.value) return
  const old = selectedKey.value
  if (renameNode(draft.value, old, nodeKeyDraft.value)) selectedKey.value = nodeKeyDraft.value
  else message.error(t('jobs.renameInvalid'))
}
async function referenceChanged() {
  refController?.abort()
  const node = selected.value
  if (!node || node.kind !== 'EXECUTION' || !node.refId || !editing.value) return
  const requestedId = node.refId
  refController = new AbortController()
  const signal = refController.signal
  try {
    const v = record(
      await runtime.request(
        '/api/' +
          (node.nodeType === 'TASK' ? 'tasks/' : 'jobs/') +
          requestedId +
          (node.nodeType === 'JOB' ? '/execution-context' : ''),
        { signal, lossless: true },
      ),
    )
    if (signal.aborted || node.refId !== requestedId) return
    node.currentRefName = String(v.name ?? '')
    node.currentRefRevision = exactId(v.currentRevision ?? v.revision)
    node.referenceAvailable = true
  } catch {
    if (!signal.aborted) node.referenceAvailable = false
  }
}
function setReference(id: string) {
  if (!selected.value) return
  selected.value.refName = ''
  selected.value.refRevision = undefined
  selected.value.refDefinitionId = undefined
  selected.value.refId = id
  selected.value.currentRefName = ''
  selected.value.currentRefRevision = undefined
  void referenceChanged()
}
function locate(path: string) {
  const n = /nodes\[(\d+)\]/.exec(path),
    e = /edges\[(\d+)\]/.exec(path)
  if (n && draft.value.nodes[Number(n[1])]) select(draft.value.nodes[Number(n[1])]!.nodeId)
  else if (e && draft.value.edges[Number(e[1])]) selectEdge(draft.value.edges[Number(e[1])]!.id)
  else {
    tab.value = 'job'
    panel.value = true
  }
  const field = path.split('.').at(-1)
  const selector = n
    ? field === 'refId'
      ? '#node-ref'
      : field === 'nodeId'
        ? '#node-key'
        : '#node-name'
    : e
      ? '.job-condition [aria-invalid="true"], .job-condition input, .job-condition .cm-content'
      : '#job-' + field
  void nextTick(() => root.value?.querySelector<HTMLElement>(selector)?.focus())
}
async function validate() {
  if (disabled.value) return false
  localIssues.value = issues(draft.value)
  for (const [id, invalid] of Object.entries(conditionErrors.value))
    if (invalid && draft.value.edges.some((e) => e.id === id && !e.defaultRoute))
      localIssues.value.push({
        path: `edges[${draft.value.edges.findIndex((e) => e.id === id)}].condition`,
        key: 'conditionInvalid',
      })
  if (localIssues.value.length) {
    locate(localIssues.value[0]!.path)
    return false
  }
  busy.value = true
  error.value = undefined
  try {
    const result = record(
      await runtime.request('/api/jobs/validate', {
        method: 'POST',
        body: {
          jobId: row.value?.id,
          expectedRevision: row.value?.currentRevision,
          definition: jobBody(draft.value),
        },
        lossless: true,
        signal: controller.signal,
      }),
    )
    if (controller.signal.aborted) return false
    if (!Array.isArray(result.issues) || !Array.isArray(result.references))
      throw new ApiError('protocol')
    serverIssues.value = result.issues as typeof serverIssues.value
    references.value = result.references as typeof references.value
    checked.value = result.valid === true
    if (!checked.value) locate(serverIssues.value[0]?.path ?? 'definition')
    else message.success(t('jobs.valid'))
    return checked.value
  } catch (e) {
    if (!controller.signal.aborted) error.value = e
    return false
  } finally {
    busy.value = false
  }
}
async function publish() {
  if (row.value && !changed.value) return
  if (!(await validate())) return
  const snapshot = json(draft.value)
  const text =
    t('jobs.publishHint') +
    '\n' +
    references.value
      .map((r) => `${String(r.name)} · #${String(r.refId)} · v${String(r.revision)}`)
      .join('\n') +
    '\n\n' +
    t('jobs.referenceHint')
  if (
    !(await confirm(t('jobs.publishTitle'), text)) ||
    snapshot !== json(draft.value) ||
    disabled.value
  )
    return
  busy.value = true
  error.value = undefined
  try {
    const r = record(
      await runtime.request(
        '/api/jobs' +
          (row.value ? '/' + row.value.id + '?expectedRevision=' + row.value.currentRevision : ''),
        {
          method: row.value ? 'PUT' : 'POST',
          body: jobBody(draft.value),
          lossless: true,
          signal: controller.signal,
        },
      ),
    )
    if (controller.signal.aborted) return
    const id = exactId(r.jobId),
      revision = exactId(r.revision)
    baseline.value = draftSnapshot()
    busy.value = false
    message.success(t('jobs.saved', { revision }))
    await router.replace(jobLink('/jobs/' + id))
  } catch (e) {
    if (!controller.signal.aborted) {
      error.value = e
      if (
        !(e instanceof ApiError) ||
        ['network', 'timeout', 'protocol'].includes(e.kind) ||
        e.status >= 500
      )
        uncertain.value = true
    }
  } finally {
    busy.value = false
  }
}
function formatted(value: string) {
  try {
    return formatUtc(value, runtime.preferences.locale, runtime.preferences.displayTimezone)
  } catch {
    return '—'
  }
}
async function reload() {
  if (await allowLeave()) await load()
}
function nested() {
  const n = selected.value
  if (n?.nodeType !== 'JOB' || !n.refDefinitionId) return
  void router.push({
    name: 'job-version',
    params: { id: n.refId, definitionId: n.refDefinitionId },
    query: { parent: route.fullPath, back: listBack.value },
  })
}
const parent = computed(() =>
  typeof route.query.parent === 'string' &&
  /^\/jobs\/[1-9]\d*(\/versions\/[1-9]\d*)?(\?.*)?$/.test(route.query.parent)
    ? route.query.parent
    : undefined,
)
const listBack = computed(() =>
  typeof route.query.back === 'string' && /^\/jobs(?:\?[^#]*)?$/.test(route.query.back)
    ? route.query.back
    : '/jobs',
)
function jobLink(path: string) {
  return { path, query: { back: listBack.value } }
}
</script>
<template>
  <section
    ref="root"
    class="jobs-page job-editor"
    :class="{ 'job-maximized': maximized }"
    :style="{ '--job-property-width': panelWidth + 'px' }"
    @keydown="shortcut"
  >
    <header class="job-heading">
      <div>
        <NButton text @click="router.push(parent ?? listBack)">{{
          t(parent ? 'jobs.returnParent' : 'jobs.back')
        }}</NButton>
        <h1>{{ draft.name || t(editing ? 'jobs.create' : 'jobs.details') }}</h1>
        <p v-if="row">
          {{ t('jobs.job') }} #{{ row.id }} · v{{ row.currentRevision }}
          <span v-if="dirty">· {{ t('jobs.localDraft') }}</span>
        </p>
      </div>
      <div class="job-actions">
        <NButton @click="maximized = !maximized">{{
          t(maximized ? 'jobs.restoreCanvas' : 'jobs.maximizeCanvas')
        }}</NButton>
        <NButton v-if="row" @click="router.push(jobLink('/jobs/' + row.id + '/versions'))">{{
          t('jobs.versions')
        }}</NButton
        ><template v-if="editing"
          ><NButton :disabled="disabled" :loading="busy" @click="validate">{{
            t('jobs.validate')
          }}</NButton
          ><NButton
            type="primary"
            :disabled="disabled || (!!row && !changed)"
            :loading="busy"
            @click="publish"
            >{{ t('jobs.publish') }}</NButton
          ></template
        ><NButton
          v-else-if="row && !historical && can(user, 'CONFIGURATION_WRITE')"
          type="primary"
          @click="router.push(jobLink('/jobs/' + row!.id + '/edit'))"
          >{{ t('tasks.editAction') }}</NButton
        ><NButton :disabled="busy" @click="panel = !panel">{{
          t(panel ? 'jobs.closeProperties' : 'jobs.showProperties')
        }}</NButton>
      </div>
    </header>
    <RequestError :error="error" />
    <div
      v-if="uncertain || (error instanceof ApiError && error.kind === 'conflict')"
      class="job-notice"
      role="alert"
    >
      {{ t(uncertain ? 'jobs.uncertain' : 'jobs.conflict') }}
      <NButton text @click="reload">{{ t('jobs.reload') }}</NButton>
    </div>
    <p v-if="loading">{{ t('jobs.loading') }}</p>
    <NButton v-if="error && (!row || (editing && !metadata))" @click="reload">{{
      t('jobs.retry')
    }}</NButton>
    <div v-if="!loading && (!error || row)" class="job-notice">
      {{ t(editing ? 'jobs.referenceHint' : historical ? 'jobs.historicalHint' : 'jobs.readOnly')
      }}<span v-if="historical">
        · v{{ row?.currentRevision }} · {{ t('jobs.definition') }} #{{ row?.currentDefinitionId }} ·
        {{ formatted(publishedAt) }}
        <span v-if="currentVersion">({{ t('jobs.current') }})</span></span
      >
    </div>
    <div v-if="localIssues.length || serverIssues.length" class="job-issues" role="alert">
      <strong>{{ t('jobs.issue') }}</strong
      ><button v-for="(issue, i) in localIssues" :key="'l' + i" @click="locate(issue.path)">
        {{ issue.path }} — {{ t('jobs.' + issue.key) }}</button
      ><button v-for="(issue, i) in serverIssues" :key="'s' + i" @click="locate(issue.path)">
        {{ issue.path }} —
        {{
          issue.message || t(issue.path === 'expectedRevision' ? 'jobs.conflict' : 'jobs.invalid')
        }}
        ({{ issue.errorCode }})
      </button>
    </div>
    <div class="job-workspace" :class="{ 'panel-open': panel }">
      <div class="job-graph-panel">
        <div class="job-toolbar">
          <NSelect
            :key="searchRevision"
            :node-props="() => ({ role: 'option' })"
            class="job-search"
            filterable
            clearable
            :value="null"
            :options="searchOptions"
            :placeholder="t('jobs.searchGraph')"
            :aria-label="t('jobs.searchGraph')"
            @update:value="(value) => value && findItem(value)"
          />
          <NButton size="small" :disabled="!selectedKey && !selectedEdge" @click="locateSelected">{{
            t('jobs.locateSelected')
          }}</NButton>
          <NButton
            v-if="editing"
            size="small"
            :disabled="disabled || history.length < 2"
            @click="undo"
            >{{ t('jobs.undo') }}</NButton
          >
          <NButton
            v-if="editing"
            size="small"
            :disabled="disabled || !future.length"
            @click="redo"
            >{{ t('jobs.redo') }}</NButton
          >

          <template v-if="editing"
            ><NButton size="small" :disabled="disabled" @click="add('TASK')"
              >＋ {{ t('jobs.addTask') }}</NButton
            ><NButton size="small" :disabled="disabled" @click="add('JOB')"
              >＋ {{ t('jobs.addJob') }}</NButton
            ><NButton size="small" :disabled="disabled" @click="add('GATEWAY')"
              >◇ {{ t('jobs.addGateway') }}</NButton
            ></template
          ><NButton
            size="small"
            :disabled="loading || busy || !draft.nodes.length"
            @click="canvas?.autoLayout(direction)"
            >{{ t('jobs.layout') }}</NButton
          ><NSelect
            v-model:value="direction"
            class="job-direction"
            :node-props="() => ({ role: 'option' })"
            :aria-label="t('jobs.layoutDirection')"
            :options="[
              { value: 'LR', label: t('jobs.horizontal') },
              { value: 'TB', label: t('jobs.vertical') },
            ]"
          />
        </div>
        <JobCanvas
          ref="canvas"
          :key="route.fullPath"
          :nodes="draft.nodes"
          :edges="draft.edges"
          :readonly="disabled"
          :selected="selectedKey"
          :selected-edge="selectedEdge"
          @select="select"
          @edge="selectEdge"
          @clear="clearSelection"
          @connect="connect"
          @move="move"
          @ports="setPorts"
          @remove="removeSelected"
          @invalid="message.error(t('jobs.' + $event))"
        />
        <p class="job-note">{{ t(editing ? 'jobs.moveHint' : 'jobs.historicalHint') }}</p>
      </div>
      <aside v-if="panel" class="job-properties" :aria-label="t('jobs.properties')">
        <div
          class="job-panel-resizer"
          role="separator"
          aria-orientation="vertical"
          :aria-label="t('jobs.resizeProperties')"
          :aria-valuemin="320"
          :aria-valuemax="480"
          :aria-valuenow="panelWidth"
          tabindex="0"
          @pointerdown.prevent="resizePanel"
          @keydown.left.prevent="panelWidth = Math.min(480, panelWidth + 16)"
          @keydown.right.prevent="panelWidth = Math.max(320, panelWidth - 16)"
        />
        <div class="job-property-heading">
          <h2>
            {{
              t(edge ? 'jobs.selectedEdge' : selected ? 'jobs.nodeProperties' : 'jobs.properties')
            }}
          </h2>
          <NButton quaternary circle :aria-label="t('common.close')" @click="panel = false"
            ><X :size="18"
          /></NButton>
        </div>
        <LineTabs
          id="job-property-tab"
          :label="t('jobs.properties')"
          :value="tab"
          :tabs="[
            { value: 'node', label: t('jobs.node') },
            { value: 'job', label: t('jobs.job') },
          ]"
          panel-id="job-property-content"
          @change="tab = $event"
        />
        <div
          id="job-property-content"
          role="tabpanel"
          :aria-labelledby="'job-property-tab-' + tab"
          class="job-property-content"
        >
          <dl v-if="tab === 'job' && !editing" class="job-readonly-facts">
            <dt>{{ t('jobs.name') }}</dt>
            <dd>{{ draft.name || '—' }}</dd>
            <dt>{{ t('jobs.timeout') }}</dt>
            <dd>{{ draft.timeout ?? '—' }}</dd>
            <dt>{{ t('jobs.enabled') }}</dt>
            <dd>{{ t(draft.enabled ? 'config.enabled' : 'config.disabled') }}</dd>
            <dt>{{ t('jobs.groups') }}</dt>
            <dd>{{ draft.groups || '—' }}</dd>
            <dt>{{ t('jobs.description') }}</dt>
            <dd>{{ draft.description || '—' }}</dd>
          </dl>
          <template v-else-if="tab === 'job'">
            <div class="job-field">
              <label for="job-name">{{ t('jobs.name') }} *</label
              ><NInput
                v-model:value="draft.name"
                :disabled="disabled"
                :input-props="{
                  id: 'job-name',
                  'aria-invalid': localIssues.some((e) => e.path === 'name'),
                }"
                :maxlength="128"
              />
            </div>
            <div class="job-two">
              <div class="job-field">
                <label for="job-timeout">{{ t('jobs.timeout') }}</label
                ><NInputNumber
                  v-model:value="draft.timeout"
                  :disabled="disabled"
                  :min="1"
                  :max="2147483647"
                  :input-props="{ id: 'job-timeout' }"
                />
              </div>
              <div class="job-field job-enabled-row">
                <label for="job-enabled">{{ t('jobs.enabled') }}</label
                ><NSwitch
                  id="job-enabled"
                  v-model:value="draft.enabled"
                  :disabled="disabled"
                  :aria-label="t('jobs.enabled')"
                />
              </div>
            </div>
            <div class="job-field">
              <label for="job-groups">{{ t('jobs.groups') }}</label
              ><NInput
                v-model:value="draft.groups"
                :disabled="disabled"
                :input-props="{ id: 'job-groups' }"
              />
              <p class="job-note">{{ t('jobs.groupHint') }}</p>
            </div>
            <div class="job-field">
              <label for="job-description">{{ t('jobs.description') }}</label
              ><NInput
                v-model:value="draft.description"
                type="textarea"
                :disabled="disabled"
                :maxlength="500"
                :input-props="{ id: 'job-description' }"
              />
            </div>
          </template>
          <template v-else>
            <div class="job-field">
              <label>{{ t('jobs.nodePicker') }}</label
              ><NSelect
                :node-props="() => ({ role: 'option' })"
                :value="selectedKey || null"
                :options="nodeOptions"
                filterable
                :aria-label="t('jobs.nodePicker')"
                @update:value="pickNode"
              />
            </div>
            <div class="job-field">
              <label>{{ t('jobs.routePicker') }}</label
              ><NSelect
                :node-props="() => ({ role: 'option' })"
                :value="selectedEdge || null"
                :options="connectionOptions"
                filterable
                :aria-label="t('jobs.routePicker')"
                @update:value="pickEdge"
              />
            </div>
            <template v-if="selected && !editing">
              <dl class="job-readonly-facts">
                <dt>{{ t('jobs.nodeName') }}</dt>
                <dd>{{ selected.name }}</dd>
                <dt>{{ t('jobs.nodeKey') }}</dt>
                <dd>{{ selected.nodeId }}</dd>
                <template v-if="selected.kind === 'EXECUTION'">
                  <dt>{{ t('jobs.target') }}</dt>
                  <dd>
                    {{ selected.refName || '—' }} · {{ selected.nodeType }} #{{ selected.refId
                    }}<span v-if="selected.refRevision"> · v{{ selected.refRevision }}</span>
                  </dd>
                  <template v-if="selected.policy"
                    ><dt>{{ t('jobs.executeCondition') }}</dt>
                    <dd>{{ t('jobs.' + selected.policy.executeCondition) }}</dd>
                    <dt>{{ t('jobs.failStrategy') }}</dt>
                    <dd>{{ t('jobs.' + selected.policy.failStrategy) }}</dd></template
                  >
                </template>
              </dl>
              <NButton
                v-if="selected.nodeType === 'JOB' && selected.refDefinitionId"
                text
                type="primary"
                @click="nested"
                >{{ t('jobs.openNested') }}</NButton
              >
              <div v-if="selected.kind === 'GATEWAY'" class="job-readonly-routes">
                <NButton
                  v-for="item in outgoing"
                  :key="item.id"
                  text
                  type="primary"
                  @click="selectEdge(item.id)"
                  >{{ item.routeKey }} · {{ item.priority }} →
                  {{ draft.nodes.find((n) => n.nodeId === item.toNodeId)?.name
                  }}{{ item.defaultRoute ? ' · ' + t('jobs.defaultRoute') : '' }}</NButton
                >
              </div>
            </template>
            <template v-if="selected && editing">
              <div class="job-field">
                <label for="node-name">{{ t('jobs.nodeName') }} *</label
                ><NInput
                  v-model:value="selected.name"
                  :disabled="disabled"
                  :maxlength="128"
                  :input-props="{ id: 'node-name' }"
                />
              </div>
              <div class="job-field">
                <label for="node-key">{{ t('jobs.nodeKey') }} *</label>
                <div class="job-inline">
                  <NInput
                    v-model:value="nodeKeyDraft"
                    :disabled="disabled"
                    :maxlength="64"
                    :input-props="{ id: 'node-key' }"
                  /><NButton
                    v-if="editing"
                    :disabled="disabled || nodeKeyDraft === selected.nodeId"
                    @click="rename"
                    >{{ t('jobs.rename') }}</NButton
                  >
                </div>
              </div>
              <template v-if="selected.kind === 'EXECUTION'">
                <div class="job-field">
                  <label for="node-ref">{{ t('jobs.target') }} · {{ selected.nodeType }} *</label
                  ><EntityPicker
                    id="node-ref"
                    :key="selected.nodeId"
                    :model-value="selected.refId ?? ''"
                    :endpoint="selected.nodeType === 'TASK' ? 'tasks' : 'jobs'"
                    :label="t('jobs.target')"
                    :selected-id="selected.refId"
                    :selected-name="
                      editing ? selected.currentRefName || selected.refName : selected.refName
                    "
                    :disabled="disabled"
                    @update:model-value="setReference"
                  />
                  <p v-if="selected.refRevision" class="job-note">
                    {{ t('jobs.publishedRef') }}: {{ selected.refName }} · v{{
                      selected.refRevision
                    }}
                  </p>
                  <p v-if="editing && selected.currentRefRevision" class="job-note">
                    {{ t('jobs.currentRef') }}: {{ selected.currentRefName }} · v{{
                      selected.currentRefRevision
                    }}
                  </p>
                  <p v-if="editing && selected.referenceAvailable === false" class="job-error">
                    {{ t('jobs.noCurrentRef') }}
                    <NButton text @click="referenceChanged">{{ t('jobs.retry') }}</NButton>
                  </p>
                </div>
                <div v-if="selected.policy" class="job-two">
                  <div class="job-field">
                    <label>{{ t('jobs.executeCondition') }}</label
                    ><NSelect
                      v-model:value="selected.policy.executeCondition"
                      :node-props="() => ({ role: 'option' })"
                      :disabled="disabled"
                      :options="policies(['ALL_SUCCESS', 'ANY_SUCCESS', 'ALWAYS'])"
                      :aria-label="t('jobs.executeCondition')"
                    />
                  </div>
                  <div class="job-field">
                    <label>{{ t('jobs.failStrategy') }}</label
                    ><NSelect
                      v-model:value="selected.policy.failStrategy"
                      :node-props="() => ({ role: 'option' })"
                      :disabled="disabled"
                      :options="policies(['STOP', 'SKIP'])"
                      :aria-label="t('jobs.failStrategy')"
                    />
                  </div>
                </div>
                <p class="job-note">{{ t('jobs.policyHint') }}</p>
                <NButton
                  v-if="selected.nodeType === 'JOB' && selected.refDefinitionId"
                  text
                  type="primary"
                  @click="nested"
                  >{{ t('jobs.openNested') }}</NButton
                >
              </template>
              <template v-else
                ><p class="job-note">{{ t('jobs.gatewayHint') }}</p>
                <div
                  class="job-route-table"
                  :class="{ 'bounded-data-scroll': outgoing.length > 6 }"
                >
                  <table>
                    <thead>
                      <tr>
                        <th>{{ t('jobs.routeKey') }}</th>
                        <th>{{ t('jobs.priority') }}</th>
                        <th>{{ t('jobs.targetNode') }}</th>
                        <th>{{ t('jobs.defaultRoute') }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="item in outgoing" :key="item.id" @click="selectEdge(item.id)">
                        <td>
                          <NButton text type="primary" @click="selectEdge(item.id)">{{
                            item.routeKey
                          }}</NButton>
                        </td>
                        <td>{{ item.priority }}</td>
                        <td>{{ draft.nodes.find((n) => n.nodeId === item.toNodeId)?.name }}</td>
                        <td>{{ item.defaultRoute ? '✓' : '—' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div></template
              >
              <NButton v-if="editing" type="error" text :disabled="disabled" @click="removeNode">{{
                t('jobs.removeNode')
              }}</NButton>
            </template>
            <dl v-if="edge && !editing" class="job-readonly-facts">
              <dt>{{ t('jobs.connections') }}</dt>
              <dd>
                {{ draft.nodes.find((n) => n.nodeId === edge!.fromNodeId)?.name }} →
                {{ draft.nodes.find((n) => n.nodeId === edge!.toNodeId)?.name }}
              </dd>
              <template
                v-if="draft.nodes.find((n) => n.nodeId === edge!.fromNodeId)?.kind === 'GATEWAY'"
              >
                <dt>{{ t('jobs.routeKey') }}</dt>
                <dd>{{ edge.routeKey }}</dd>
                <dt>{{ t('jobs.priority') }}</dt>
                <dd>{{ edge.priority }}</dd>
                <dt>{{ t('jobs.defaultRoute') }}</dt>
                <dd>{{ edge.defaultRoute ? '✓' : '—' }}</dd>
                <template v-if="!edge.defaultRoute"
                  ><dt>{{ t('jobs.condition') }}</dt>
                  <dd>{{ edge.conditionText || '—' }}</dd></template
                >
              </template>
            </dl>
            <template v-if="edge && editing">
              <p>
                {{ draft.nodes.find((n) => n.nodeId === edge!.fromNodeId)?.name }} →
                {{ draft.nodes.find((n) => n.nodeId === edge!.toNodeId)?.name }}
              </p>
              <template
                v-if="draft.nodes.find((n) => n.nodeId === edge!.fromNodeId)?.kind === 'GATEWAY'"
              >
                <div class="job-two">
                  <div class="job-field">
                    <label>{{ t('jobs.routeKey') }} *</label
                    ><NInput
                      v-model:value="edge.routeKey"
                      :disabled="disabled"
                      :maxlength="64"
                      :aria-label="t('jobs.routeKey')"
                    />
                  </div>
                  <div class="job-field">
                    <label>{{ t('jobs.priority') }} *</label
                    ><NInputNumber
                      :value="edge.priority ?? 0"
                      :disabled="disabled"
                      :min="0"
                      :max="10000"
                      :aria-label="t('jobs.priority')"
                      @update:value="edge.priority = $event ?? 0"
                    />
                  </div>
                </div>
                <NCheckbox
                  :checked="edge.defaultRoute"
                  :disabled="disabled"
                  @update:checked="changeDefault(edge!, $event)"
                  >{{ t('jobs.defaultRoute') }}</NCheckbox
                >
                <p class="job-note">{{ t('jobs.gatewayHint') }}</p>
                <template v-if="!edge.defaultRoute"
                  ><h3>{{ t('jobs.condition') }}</h3>
                  <NButton size="small" @click="expandedCondition = true">{{
                    t('jobs.expandCondition')
                  }}</NButton>
                  <ConditionEditor
                    v-if="!expandedCondition"
                    :key="edge.id"
                    v-model="edge.conditionText"
                    :drafts="conditionDraft(edge.id)"
                    :disabled="disabled"
                    @update:drafts="conditionDrafts[edge!.id] = $event"
                    @invalid="conditionErrors[edge!.id] = $event"
                  />
                  <p class="job-note">{{ t('jobs.conditionHint') }}</p></template
                >
              </template>
              <NButton
                v-if="editing"
                type="error"
                text
                :disabled="disabled"
                @click="removeEdge(edge.id)"
                >{{ t('jobs.removeEdge') }}</NButton
              >
            </template>
            <p v-if="!selected && !edge" class="job-note">{{ t('jobs.noSelection') }}</p>
          </template>
          <template v-if="editing"
            ><h3>{{ t('jobs.connections') }}</h3>
            <div class="job-two">
              <div class="job-field">
                <label>{{ t('jobs.source') }}</label
                ><NSelect
                  v-model:value="connection.from"
                  :node-props="() => ({ role: 'option' })"
                  :options="nodeOptions"
                  filterable
                  :disabled="disabled"
                  :aria-label="t('jobs.source')"
                />
              </div>
              <div class="job-field">
                <label>{{ t('jobs.targetNode') }}</label
                ><NSelect
                  v-model:value="connection.to"
                  :node-props="() => ({ role: 'option' })"
                  :options="nodeOptions"
                  filterable
                  :disabled="disabled"
                  :aria-label="t('jobs.targetNode')"
                />
              </div>
            </div>
            <NButton
              :disabled="disabled || !connection.from || !connection.to"
              @click="connect(connection.from, connection.to)"
              >{{ t('jobs.connect') }}</NButton
            ></template
          >
        </div>
      </aside>
    </div>
    <NModal
      v-model:show="expandedCondition"
      preset="card"
      class="job-condition-modal"
      :title="t('jobs.condition')"
      :style="{ width: 'min(800px, calc(100vw - 32px))' }"
    >
      <ConditionEditor
        v-if="expandedCondition && edge"
        :key="edge.id"
        v-model="edge.conditionText"
        :drafts="conditionDraft(edge.id)"
        :disabled="disabled"
        @update:drafts="conditionDrafts[edge!.id] = $event"
        @invalid="conditionErrors[edge!.id] = $event"
      />
    </NModal>
  </section>
</template>
