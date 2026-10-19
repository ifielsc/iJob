<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { NButton, NDrawer, NDrawerContent } from 'naive-ui'
import { X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { formatUtc } from '../../shared/time/format'
import IdentityText from '../../shared/ui/IdentityText.vue'
import TimeEvidence from '../../shared/ui/TimeEvidence.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import Pagination from '../../shared/ui/CompactPagination.vue'
import { pageData } from '../../shared/api/values'
import { runRow, terminal } from '../../shared/runtime/contracts'
import { logSnapshot } from '../../shared/runtime/log-snapshot'
import type { TaskRow, Run, Log } from './model'
const props = withDefaults(
    defineProps<{
      row: Pick<TaskRow, 'id' | 'name'>
      triggerId?: string
      targetType?: 'TASK' | 'JOB'
    }>(),
    { targetType: 'TASK', triggerId: undefined },
  ),
  emit = defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  router = useRouter(),
  { user } = useIdentity(),
  { t } = useI18n()
const canLog = computed(() => can(user.value, 'SENSITIVE_READ') && !runtime.accessIssue.value)
const page = ref(1),
  size = ref(20),
  total = ref(0),
  rows = ref<Run[]>([]),
  selected = ref(props.triggerId),
  trigger = shallowRef<Run>(),
  instance = shallowRef<Run>(),
  logs = ref<Log[]>([]),
  logLimit = ref(500),
  logTruncated = ref(false),
  busy = ref(false),
  error = shallowRef<unknown>(null)
let controller: AbortController | undefined,
  timer: ReturnType<typeof setTimeout> | undefined,
  generation = 0
function stop() {
  generation++
  controller?.abort()
  clearTimeout(timer)
  busy.value = false
}
onBeforeUnmount(stop)
watch(
  () => runtime.accessIssue.value,
  (issue) => {
    if (issue) {
      stop()
      logs.value = []
      rows.value = []
      trigger.value = undefined
      instance.value = undefined
      emit('close')
    }
  },
)
const current = computed(() => instance.value ?? trigger.value)
async function refresh() {
  stop()
  const requestGeneration = generation
  controller = new AbortController()
  const signal = controller.signal
  busy.value = true
  error.value = null
  try {
    if (selected.value) {
      const nextTrigger = runRow(
        await runtime.request('/api/triggers/' + selected.value, { signal }),
      )
      if (signal.aborted) return
      trigger.value = nextTrigger
      if (nextTrigger.rootInstanceId) {
        const nextInstance = runRow(
          await runtime.request('/api/instances/' + nextTrigger.rootInstanceId, { signal }),
        )
        if (signal.aborted) return
        if (instance.value && instance.value.id !== nextInstance.id) logs.value = []
        instance.value = nextInstance
        if (canLog.value) {
          const snapshot = logSnapshot(
            await runtime.request(
              '/api/instances/' + nextInstance.id + '/logs/snapshot?limit=' + logLimit.value,
              { signal },
            ),
          )
          if (signal.aborted) return
          logs.value = snapshot.rows
          logTruncated.value = snapshot.truncated
        }
      }
    } else {
      const params = new URLSearchParams({
        targetType: props.targetType,
        targetId: props.row.id,
        pageNum: String(page.value),
        pageSize: String(size.value),
      })
      const data = pageData(await runtime.request('/api/triggers?' + params, { signal }), runRow)
      if (signal.aborted) return
      rows.value = data.list
      total.value = data.total
    }
  } catch (e) {
    if (!signal.aborted && !runtime.accessIssue.value) error.value = e
  } finally {
    if (requestGeneration === generation) {
      busy.value = false
      if (
        selected.value &&
        current.value &&
        !error.value &&
        (canLog.value || !terminal.has(current.value.status))
      )
        timer = setTimeout(() => void refresh(), terminal.has(current.value.status) ? 10000 : 2000)
    }
  }
}
function loadMoreLogs() {
  logLimit.value = Math.min(5000, logLimit.value + 500)
  void refresh()
}
function open(id: string) {
  stop()
  trigger.value = undefined
  instance.value = undefined
  logs.value = []
  logLimit.value = 500
  logTruncated.value = false
  selected.value = id
  void refresh()
}
function back() {
  stop()
  selected.value = undefined
  trigger.value = undefined
  instance.value = undefined
  logs.value = []
  void refresh()
}
watch([page, size, () => runtime.preferences.locale], () => void refresh())
void refresh()
function time(value?: string) {
  if (!value) return '—'
  try {
    return formatUtc(value, runtime.preferences.locale, runtime.preferences.displayTimezone)
  } catch {
    return '—'
  }
}
function setSize(value: number) {
  size.value = value
  page.value = 1
}
function openCenter() {
  if (!instance.value) return
  void router.push('/runs/instances/' + instance.value.id)
  emit('close')
}
</script>
<template>
  <NDrawer show :width="640" class="task-drawer" @update:show="emit('close')"
    ><NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="task-drawer-heading">
          <span>{{ t(selected ? 'tasks.result' : 'tasks.records') }}</span
          ><NButton quaternary circle :aria-label="t('common.close')" @click="emit('close')"
            ><X :size="18"
          /></NButton></div
      ></template>
      <div class="task-results">
        <div class="task-toolbar">
          <strong>{{ row.name }}</strong
          ><NButton size="small" :loading="busy" @click="refresh">{{ t('tasks.refresh') }}</NButton>
        </div>
        <RequestError :error="error" />
        <template v-if="selected">
          <NButton v-if="!triggerId" size="small" @click="back">{{ t('tasks.back') }}</NButton>
          <h3
            v-if="current"
            :class="current.status === 'SUCCESS' ? 'task-enabled' : ''"
            role="status"
          >
            {{ t('tasks.status_' + current.status) }}
          </h3>
          <p v-else>{{ t('tasks.loading') }}</p>
          <dl class="task-meta">
            <dt>{{ t('tasks.trigger') }}</dt>
            <dd><IdentityText :value="selected" /></dd>
            <dt>{{ t('tasks.definition') }}</dt>
            <dd>
              {{
                (targetType === 'JOB' ? current?.jobDefinitionId : current?.taskDefinitionId) ?? '—'
              }}
            </dd>
            <dt>{{ t('tasks.instance') }}</dt>
            <dd>
              <IdentityText v-if="instance" :value="instance.id" /><span v-else>{{
                t('tasks.noInstance')
              }}</span>
            </dd>
            <dt>{{ t('tasks.created') }}</dt>
            <dd>{{ time(trigger?.createTime) }}</dd>
            <dt>{{ t('tasks.start') }}</dt>
            <dd>{{ time(instance?.startTime) }}</dd>
            <dt>{{ t('tasks.end') }}</dt>
            <dd>{{ time(instance?.endTime) }}</dd>
          </dl>
          <p v-if="current?.message" class="task-run-message">{{ current.message }}</p>
          <NButton
            v-if="instance && can(user, 'SUMMARY_READ')"
            type="primary"
            @click="openCenter"
            >{{ t('runs.open') }}</NButton
          >
          <h3>{{ t('tasks.logs') }}</h3>
          <template v-if="canLog"
            ><div class="task-logs" tabindex="0" :aria-label="t('tasks.logs')">
              <div v-for="log in logs" :key="log.id" class="run-log-line">
                <IdentityText :value="log.id" :label="t('unified.logId')" /><span
                  ><TimeEvidence :value="log.logTime" milliseconds /> {{ log.level }}
                  {{ log.content }}</span
                >
              </div>
              <span v-if="!logs.length">{{ t('tasks.noLogs') }}</span>
            </div>
            <div class="task-toolbar">
              <span class="task-note">{{ t('tasks.loaded', { count: logs.length }) }}</span
              ><NButton v-if="instance" size="small" :disabled="busy" @click="loadMoreLogs">{{
                t(logLimit < 5000 ? 'tasks.loadMore' : 'runs.refreshLogs')
              }}</NButton>
            </div>
            <p v-if="logTruncated" class="task-note">
              {{ t('tasks.logLimit', { count: logs.length }) }}
            </p></template
          >
          <p v-else>{{ t('tasks.noLogPermission') }}</p>
        </template>
        <template v-else>
          <div class="task-table-scroll">
            <table class="task-table task-run-table">
              <thead>
                <tr>
                  <th>{{ t('tasks.created') }}</th>
                  <th>{{ t('tasks.status') }}</th>
                  <th>{{ t('tasks.actions') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="run in rows" :key="run.id">
                  <td>{{ time(run.createTime) }}<IdentityText :value="run.id" /></td>
                  <td>{{ t('tasks.status_' + run.status) }}</td>
                  <td>
                    <NButton text type="primary" @click="open(run.id)">{{
                      t('tasks.details')
                    }}</NButton>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <EmptyState v-if="!rows.length && !busy" compact :message="t('tasks.noRuns')" />
          <Pagination
            :page="page"
            :size="size"
            :total="total"
            :busy="busy"
            @page="page = $event"
            @size="setSize"
          />
        </template>
      </div>
      <template #footer
        ><NButton @click="emit('close')">{{ t('common.close') }}</NButton></template
      >
    </NDrawerContent></NDrawer
  >
</template>
