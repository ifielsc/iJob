<!-- Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { NButton, NCheckbox, NInput, NModal, useDialog } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { record } from '../../shared/api/values'
import { formatUtc } from '../../shared/time/format'
import { htmlReport } from './report'
import RequestError from '../../shared/ui/RequestError.vue'
import {
  actionTotals,
  displayOrder,
  totals,
  bundle,
  plan,
  upload,
  download,
  json,
  differences,
  pointerLabel,
  type Bundle,
  type Plan,
  type Choice,
  type Step,
} from './model'
import PackageMappingEditor from './PackageMappingEditor.vue'
const props = defineProps<{ history: boolean; kind: 'TASK' | 'JOB' }>(),
  emit = defineEmits<{ close: []; imported: [] }>()
const runtime = useRuntime(),
  { t, te } = useI18n(),
  dialog = useDialog()
const fileInput = ref<HTMLInputElement>(),
  historyPage = ref(1)
const stage = ref(props.history ? 0 : 1),
  busy = ref(false),
  error = shallowRef<unknown>(),
  localError = ref(''),
  data = shallowRef<Bundle>(),
  current = shallowRef<Plan>(),
  seed = shallowRef<Plan>()
const content = ref(''),
  fileName = ref(''),
  choices = ref<Record<string, Choice>>({}),
  expanded = ref<string[]>([]),
  visited = ref<string[]>([]),
  invalid = ref<string[]>([]),
  reviewed = ref(false),
  search = ref(''),
  uncertain = ref(false),
  records = ref<
    {
      id: string
      status: string
      createdAt: string
      actor?: string
      startedAt?: string
      completedAt?: string
      completed: number
      total: number
    }[]
  >([])
let controller = new AbortController()
const orderedEntries = computed(() => displayOrder(data.value?.objects ?? []))
const entries = computed(
  () =>
    data.value?.objects.filter((e) => e.name.toLowerCase().includes(search.value.toLowerCase())) ??
    [],
)
const steps = computed(() => displayOrder(current.value?.steps ?? []))
const summary = computed(() => {
  const count = new Map<string, number>()
  for (const s of steps.value) count.set(s.status, (count.get(s.status) ?? 0) + 1)
  return [...count]
})
function stateKinds(state: string) {
  return totals(steps.value.filter((step) => step.status === state))
    .map(({ kind, count }) => t('packages.kinds.' + kind) + ' ' + count)
    .join(runtime.preferences.locale === 'zh-CN' ? '、' : ', ')
}
const actionCounts = computed(() => actionTotals(steps.value, stage.value === 4))
const historyPages = computed(() => Math.max(1, Math.ceil(records.value.length / 20)))
const historyRows = computed(() =>
  records.value.slice((historyPage.value - 1) * 20, historyPage.value * 20),
)
function time(value?: string | null) {
  return value
    ? formatUtc(value, runtime.preferences.locale, runtime.preferences.displayTimezone)
    : t('packages.unknownTime')
}
async function backToHistory() {
  stage.value = 0
  current.value = undefined
  uncertain.value = false
  await loadHistory()
}
async function request(path: string, body?: unknown) {
  return runtime.request('/api/job-packages' + path, {
    method: body === undefined ? 'GET' : 'POST',
    ...(body === undefined ? {} : { body }),
    signal: controller.signal,
    lossless: true,
    timeoutMs: 120000,
  })
}
async function perform(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = undefined
  localError.value = ''
  try {
    await action()
  } catch (e) {
    if (!controller.signal.aborted) error.value = e
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
function close() {
  if (busy.value) return
  if (content.value && stage.value !== 4) {
    dialog.warning({
      title: t('packages.confirmClose'),
      content: t('packages.closeNote'),
      positiveText: t('packages.leave'),
      negativeText: t('packages.stay'),
      onPositiveClick: () => emit('close'),
    })
  } else emit('close')
}
async function selectFile(event: Event) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0]
  input.value = ''
  if (!file) return
  await perform(async () => {
    try {
      content.value = await upload(file)
    } catch {
      localError.value = t('packages.invalidFile')
      return
    }
    fileName.value = file.name
    data.value = bundle(await request('/inspect', { content: content.value }))
    choices.value = {}
    invalid.value = []
    reviewed.value = false
    seed.value = plan(
      await request('/plans', { content: content.value, choices: {}, reviewedEnvironment: false }),
    )
    stage.value = 2
    expanded.value = []
    visited.value = []
  })
}
function seedStep(key: string) {
  return seed.value?.steps.find((s) => s.key === key)
}
function objectName(kind: string, name: string) {
  return ['PARAMETER_BINDING', 'NOTIFY_BINDING', 'SCHEDULE'].includes(kind) && name === kind
    ? t('packages.kinds.' + kind)
    : name
}
function change(key: string, choice: Choice | undefined) {
  if (choice) choices.value = { ...choices.value, [key]: choice }
  else {
    const copy = { ...choices.value }
    delete copy[key]
    choices.value = copy
  }
}
function toggle(key: string) {
  if (!visited.value.includes(key)) visited.value.push(key)
  expanded.value = expanded.value.includes(key)
    ? expanded.value.filter((k) => k !== key)
    : [...expanded.value, key]
}
function validity(key: string, value: boolean) {
  invalid.value = value
    ? [...new Set([...invalid.value, key])]
    : invalid.value.filter((k) => k !== key)
}
function issue(code: string) {
  const [key, ...rest] = code.split(':')
  const label = te('packages.issues.' + key) ? t('packages.issues.' + key) : code
  return label + (rest.length ? ' · ' + pointerLabel(rest.join(':')) : '')
}
function statuses(s: Step) {
  return s.issues.filter((i) => i !== 'REVIEW_ENVIRONMENT')
}
async function check() {
  await perform(async () => {
    current.value = plan(
      await request('/plans', {
        content: content.value,
        choices: choices.value,
        reviewedEnvironment: reviewed.value,
      }),
    )
    seed.value = current.value
    stage.value = 3
    expanded.value = []
  })
}
async function apply() {
  if (!current.value || !['READY', 'PARTIAL', 'APPLYING'].includes(current.value.status)) return
  await perform(async () => {
    uncertain.value = true
    stage.value = 4
    choices.value = {}
    current.value = plan(await request('/plans/' + current.value!.id + '/apply', {}))
    uncertain.value = false
    emit('imported')
  })
}
async function refresh() {
  if (!current.value) return
  await perform(async () => {
    current.value = plan(await request('/plans/' + current.value!.id))
    uncertain.value = false
    if (current.value.status === 'COMPLETED') emit('imported')
  })
}
function backToMapping() {
  stage.value = 2
  reviewed.value = false
}
async function loadHistory() {
  await perform(async () => {
    const result = await request('/records')
    if (!Array.isArray(result)) throw Error()
    records.value = result.map((v) => {
      const r = record(v)
      return {
        id: String(r.id),
        status: String(r.status),
        createdAt: String(r.createdAt),
        actor: typeof r.actor === 'string' ? r.actor : undefined,
        startedAt: typeof r.startedAt === 'string' ? r.startedAt : undefined,
        completedAt: typeof r.completedAt === 'string' ? r.completedAt : undefined,
        completed: Number(r.completed),
        total: Number(r.total),
      }
    })
  })
}
async function open(id: string) {
  await perform(async () => {
    current.value = plan(await request('/plans/' + id))
    stage.value = 4
  })
}
function report(asJson = false) {
  if (current.value && !asJson) {
    download(
      'import-' + current.value.id + '.html',
      new Blob(
        [
          htmlReport(current.value, {
            title:
              t(props.kind === 'TASK' ? 'packages.importTask' : 'packages.import') +
              ' · ' +
              t('packages.reportTitle'),
            locale: runtime.preferences.locale,
            timezone: runtime.preferences.displayTimezone,
            label: (key) => t('packages.' + key),
            time,
            issue,
          }),
        ],
        { type: 'text/html;charset=utf-8' },
      ),
    )
    return
  }
  if (current.value)
    download(
      'import-' + current.value.id + '.json',
      new Blob([json(current.value)], { type: 'application/json' }),
    )
}
watch(
  () => runtime.accessIssue.value,
  (issue) => {
    if (issue) {
      controller.abort()
      emit('close')
    }
  },
)
onBeforeUnmount(() => {
  controller.abort()
  controller = new AbortController()
  choices.value = {}
  content.value = ''
  dialog.destroyAll()
})
onMounted(() => {
  if (props.history) void loadHistory()
})
</script>
<template>
  <NModal
    :show="true"
    preset="card"
    class="package-modal"
    :title="
      t(
        stage === 0
          ? 'packages.history'
          : kind === 'TASK'
            ? 'packages.importTask'
            : 'packages.import',
      )
    "
    :mask-closable="false"
    :closable="!busy"
    :close-on-esc="false"
    @close="close"
    @esc="close"
  >
    <ol v-if="stage > 0" class="package-steps">
      <li
        v-for="(step, index) in ['upload', 'mapping', 'diff', 'result']"
        :key="step"
        :class="{ active: stage === index + 1 }"
        :aria-current="stage === index + 1 ? 'step' : undefined"
      >
        {{ index + 1 }} · {{ t('packages.' + step) }}
      </li>
    </ol>
    <RequestError :error="error" />
    <p v-if="localError" role="alert" class="package-issue">{{ localError }}</p>
    <p v-if="busy" role="status" class="package-note">{{ t('common.loading') }}</p>
    <div class="package-scroll">
      <template v-if="stage === 0">
        <p class="package-note">
          {{ t('packages.historyScope') }} ·
          {{ t('packages.historyTimeNote', { zone: runtime.preferences.displayTimezone }) }}
        </p>
        <p v-if="!records.length && !busy">{{ t('packages.noHistory') }}</p>
        <table v-else class="package-history-table">
          <thead>
            <tr>
              <th>{{ t('packages.recordId') }}</th>
              <th>{{ t('packages.operator') }}</th>
              <th>
                {{ t('packages.createdAt') }} / {{ t('packages.startedAt') }} /
                {{ t('packages.completedAt') }}
              </th>
              <th>{{ t('packages.status') }}</th>
              <th>{{ t('packages.action') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in historyRows" :key="r.id">
              <td>{{ r.id }}</td>
              <td>{{ r.actor ?? '—' }}</td>
              <td class="package-history-times">
                <span>{{ t('packages.createdAt') }}: {{ time(r.createdAt) }}</span
                ><span
                  >{{ t('packages.startedAt') }}:
                  {{
                    !r.startedAt && ['READY', 'BLOCKED'].includes(r.status)
                      ? t('packages.notStarted')
                      : time(r.startedAt)
                  }}</span
                ><span>{{ t('packages.completedAt') }}: {{ time(r.completedAt) }}</span>
              </td>
              <td>
                {{ t('packages.states.' + r.status) }}<br /><small>{{
                  t('packages.saved', { done: r.completed, total: r.total })
                }}</small>
              </td>
              <td>
                <NButton text type="primary" size="small" :disabled="busy" @click="open(r.id)">{{
                  t('packages.continue')
                }}</NButton>
              </td>
            </tr>
          </tbody>
        </table>
      </template>
      <template v-if="stage === 1">
        <p class="package-note">{{ t('packages.fileHint') }}</p>
        <div class="package-file-picker">
          <input
            ref="fileInput"
            type="file"
            hidden
            accept=".zip,.ijob.zip"
            :disabled="busy"
            :aria-label="t('packages.choose')"
            @change="selectFile"
          />
          <NButton type="primary" :disabled="busy" @click="fileInput?.click()">{{
            t('packages.choose')
          }}</NButton>
          <span>{{ fileName || t('packages.noFile') }}</span>
        </div>
      </template>
      <template v-if="stage === 2 && data"
        ><div class="package-summary">
          <strong>{{ fileName }}</strong
          ><span>{{ data.objects.length }} {{ t('packages.object') }}</span>
        </div>
        <p class="package-note">{{ t('packages.disabled') }}</p>
        <NInput
          v-model:value="search"
          :disabled="busy"
          :placeholder="t('packages.searchObjects')"
          :aria-label="t('packages.searchObjects')"
        />
        <div
          v-for="entry in orderedEntries"
          v-show="entries.includes(entry)"
          :key="entry.key"
          class="package-object"
        >
          <div class="package-object-heading">
            <small>{{ t('packages.kinds.' + entry.kind) }}</small
            ><strong>{{ objectName(entry.kind, entry.name) }}</strong
            ><span class="package-status">{{
              t(
                'packages.states.' +
                  (choices[entry.key]?.action ?? seedStep(entry.key)?.action ?? 'CREATE'),
              )
            }}</span
            ><NButton size="small" :disabled="busy" @click="toggle(entry.key)">{{
              t(expanded.includes(entry.key) ? 'packages.collapse' : 'packages.configure')
            }}</NButton>
          </div>
          <p
            v-for="code in choices[entry.key]
              ? []
              : (seedStep(entry.key)?.issues.filter((i) => i !== 'REVIEW_ENVIRONMENT') ?? [])"
            :key="code"
            class="package-issue"
          >
            {{ issue(code) }}
          </p>
          <PackageMappingEditor
            v-if="visited.includes(entry.key)"
            v-show="expanded.includes(entry.key)"
            :entry="entry"
            :seed="seedStep(entry.key)"
            :model-value="choices[entry.key]"
            :disabled="busy"
            @update:model-value="change(entry.key, $event)"
            @invalid="validity(entry.key, $event)"
          />
        </div>
        <NCheckbox v-model:checked="reviewed" :disabled="busy">{{
          t('packages.review')
        }}</NCheckbox>
      </template>
      <template v-if="stage === 3 || stage === 4"
        ><p v-if="current" class="package-note">
          {{
            t('packages.timezone', {
              source: current.sourceTimezone,
              target: current.targetTimezone,
            })
          }}
        </p>
        <p v-if="stage === 3" class="package-note">{{ t('packages.plannedCounts') }}</p>
        <div class="package-result-counts">
          <div v-for="item in actionCounts" :key="item.action">
            <span>{{ t('packages.states.' + item.action) }}</span
            ><strong>{{ item.count }}</strong>
          </div>
        </div>
        <div class="package-counts">
          <span v-for="[state, count] in summary" :key="state"
            >{{ t('packages.states.' + state) }} <strong>{{ count }}</strong
            ><span v-if="state === 'DONE'" class="package-kind-summary"
              >（{{ stateKinds(state) }}）</span
            ></span
          >
        </div>
        <p v-if="current?.status === 'BLOCKED'" class="package-issue" role="alert">
          {{ t('packages.blocked') }}
        </p>
        <p v-if="uncertain" class="package-issue" role="alert">{{ t('packages.uncertain') }}</p>
        <p v-else-if="current?.status === 'PARTIAL'" class="package-notice">
          {{ t('packages.partial') }}
        </p>
        <p v-else-if="current?.status === 'COMPLETED'" class="package-notice" role="status">
          {{ t('packages.completed') }}
        </p>
        <p v-if="stage === 4 && current" class="package-note">
          {{ t('packages.operator') }}: {{ current.actor ?? '—' }} · {{ t('packages.startedAt') }}:
          {{ time(current.startedAt) }} · {{ t('packages.completedAt') }}:
          {{ time(current.completedAt) }}
        </p>
        <p class="package-notice">{{ t('packages.disabled') }}</p>
        <div v-for="s in steps" :key="s.key" class="package-object">
          <div class="package-object-heading">
            <small>{{ t('packages.kinds.' + s.kind) }}</small
            ><strong>{{ objectName(s.kind, s.name) }}</strong
            ><span class="package-status">{{ t('packages.states.' + s.action) }}</span
            ><span class="package-status" :class="{ failed: s.issues.length > 0 }">{{
              t('packages.states.' + s.status)
            }}</span
            ><NButton v-if="stage === 3" text size="small" @click="toggle(s.key)">{{
              t('packages.diff')
            }}</NButton>
          </div>
          <p v-for="code in statuses(s)" :key="code" class="package-issue">{{ issue(code) }}</p>
          <small v-if="s.targetId">#{{ s.targetId }}</small>
          <div v-if="stage === 3 && expanded.includes(s.key)" class="package-diff">
            <table>
              <thead>
                <tr>
                  <th>{{ t('packages.field') }}</th>
                  <th>{{ t('packages.before') }}</th>
                  <th>{{ t('packages.after') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="d in differences(s.before, s.after)" :key="d.key">
                  <td>{{ te('packages.' + d.key) ? t('packages.' + d.key) : d.key }}</td>
                  <td class="package-before">
                    <pre>{{ d.before }}</pre>
                  </td>
                  <td class="package-after">
                    <pre>{{ d.after }}</pre>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!differences(s.before, s.after).length">{{ t('packages.noChanges') }}</p>
          </div>
        </div>
        <p v-if="current" class="package-note">{{ t('packages.planId', { id: current.id }) }}</p>
      </template>
    </div>
    <template #footer
      ><div class="package-footer">
        <template v-if="stage === 0 && historyPages > 1"
          ><NButton size="small" :disabled="historyPage <= 1 || busy" @click="historyPage--">{{
            t('packages.previousPage')
          }}</NButton
          ><span>{{ t('packages.historyPage', { page: historyPage, total: historyPages }) }}</span
          ><NButton
            size="small"
            :disabled="historyPage >= historyPages || busy"
            @click="historyPage++"
            >{{ t('packages.nextPage') }}</NButton
          ></template
        >
        <NButton v-if="stage === 4 && history" :disabled="busy" @click="backToHistory">{{
          t('packages.backHistory')
        }}</NButton>
        <NButton :disabled="busy" @click="close">{{
          t(stage === 4 ? 'packages.done' : 'packages.cancel')
        }}</NButton
        ><NButton v-if="stage === 2" :disabled="busy" @click="stage = 1">{{
          t('packages.back')
        }}</NButton
        ><NButton
          v-if="stage === 3 || (stage === 4 && data)"
          :disabled="busy"
          @click="backToMapping"
          >{{ t(stage === 3 ? 'packages.back' : 'packages.checkAgain') }}</NButton
        ><NButton v-if="stage === 0" :disabled="busy" @click="loadHistory">{{
          t('packages.refresh')
        }}</NButton
        ><NButton
          v-if="stage === 2"
          type="primary"
          :loading="busy"
          :disabled="!reviewed || !!invalid.length"
          @click="check"
          >{{ t('packages.next') }}</NButton
        ><NButton
          v-if="stage === 3"
          type="primary"
          :loading="busy"
          :disabled="current?.status !== 'READY'"
          @click="apply"
          >{{ t('packages.apply') }}</NButton
        ><NButton v-if="stage === 4" :disabled="busy" @click="report()">{{
          t('packages.report')
        }}</NButton
        ><NButton v-if="stage === 4" :disabled="busy" @click="report(true)">{{
          t('packages.reportJson')
        }}</NButton
        ><NButton v-if="stage === 4" :disabled="busy" @click="refresh">{{
          t('packages.refresh')
        }}</NButton
        ><NButton
          v-if="stage === 4 && current?.status !== 'COMPLETED'"
          type="primary"
          :loading="busy"
          :disabled="uncertain || current?.status === 'BLOCKED'"
          @click="apply"
          >{{ t('packages.retry') }}</NButton
        >
      </div></template
    >
  </NModal>
</template>
