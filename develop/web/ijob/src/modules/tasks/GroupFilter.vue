<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { NAutoComplete } from 'naive-ui'
import { ChevronDown } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { ApiError } from '../../shared/api/errors'
import { pageData } from '../../shared/api/values'

const props = withDefaults(defineProps<{ value: string; endpoint?: 'tasks' | 'jobs' }>(), {
  endpoint: 'tasks',
})
const emit = defineEmits<{ 'update:value': [string] }>()
const runtime = useRuntime(),
  { t } = useI18n()
const options = ref<string[]>([]),
  loading = ref(false),
  failed = ref(false),
  total = ref(0)
let controller: AbortController | undefined, timer: ReturnType<typeof setTimeout> | undefined

function cancel() {
  clearTimeout(timer)
  controller?.abort()
}
async function load() {
  cancel()
  options.value = []
  total.value = 0
  failed.value = false
  loading.value = false
  if (runtime.accessIssue.value) return
  const prefix = props.value.trim().toLowerCase()
  if (!/^[a-z0-9._-]{0,64}$/.test(prefix)) return
  controller = new AbortController()
  const { signal } = controller
  loading.value = true
  try {
    const params = new URLSearchParams({ prefix, pageNum: '1', pageSize: '50' })
    const data = pageData(
      await runtime.request('/api/' + props.endpoint + '/groups?' + params, { signal }),
      (v) => {
        if (typeof v !== 'string') throw new ApiError('protocol')
        return v
      },
    )
    if (signal.aborted || runtime.accessIssue.value) return
    options.value = data.list
    total.value = data.total
  } catch {
    if (!signal.aborted && !runtime.accessIssue.value) failed.value = true
  } finally {
    if (!signal.aborted) loading.value = false
  }
}
watch(
  () => props.value,
  () => {
    cancel()
    options.value = []
    total.value = 0
    failed.value = false
    timer = setTimeout(() => void load(), 200)
  },
)
watch(
  () => runtime.accessIssue.value,
  () => {
    cancel()
    options.value = []
    total.value = 0
    loading.value = false
  },
)
onBeforeUnmount(cancel)
</script>

<template>
  <NAutoComplete
    :value="value"
    :options="options"
    :loading="loading"
    :disabled="!!runtime.accessIssue.value"
    :placeholder="t('tasks.groupFilterPlaceholder')"
    :input-props="{
      id: 'tasks-group',
      'aria-label': t('tasks.group'),
      'aria-describedby': 'tasks-group-hint',
    }"
    :menu-props="{ 'aria-label': t('tasks.group') }"
    clearable
    show-empty
    :get-show="() => true"
    @focus="load"
    @update:value="emit('update:value', $event ?? '')"
  >
    <template #suffix><ChevronDown :size="14" aria-hidden="true" /></template>
    <template #empty>{{
      t(
        loading
          ? 'common.loading'
          : failed
            ? 'tasks.groupOptionsFailed'
            : 'tasks.groupOptionsEmpty',
      )
    }}</template>
  </NAutoComplete>
  <p id="tasks-group-hint" class="task-note task-group-hint" :role="failed ? 'status' : undefined">
    {{
      t(
        failed
          ? 'tasks.groupOptionsFailed'
          : total > 50
            ? 'tasks.groupOptionsMore'
            : 'tasks.groupFilterHelp',
      )
    }}
  </p>
</template>
