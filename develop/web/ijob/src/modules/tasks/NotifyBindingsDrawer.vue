<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, h, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { NButton, NDrawer, NDrawerContent, useMessage } from 'naive-ui'
import { X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { ApiError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import EditableTable from '../../shared/ui/EditableTable.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import { useEditor } from '../../shared/forms/use-editor'
import NotifyConfigPicker from './NotifyConfigPicker.vue'
import {
  bindingBody,
  bindingId,
  notifyBindings,
  notifyPolicies,
  type NotifyBinding,
  type NotifyCandidate,
  type NotifyPolicy,
} from './notify-binding-model'
import type { TaskRow } from './model'
import './notify-bindings.css'
const props = withDefaults(
    defineProps<{
      row: Pick<TaskRow, 'id' | 'name'> & { pluginType?: string }
      targetType?: 'TASK' | 'JOB'
    }>(),
    { targetType: 'TASK' },
  ),
  emit = defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  message = useMessage()
const allowed = computed(
  () =>
    can(user.value, 'SENSITIVE_READ') &&
    can(user.value, 'CONFIGURATION_WRITE') &&
    !runtime.accessIssue.value,
)
const rows = ref<NotifyBinding[]>([]),
  page = ref(1),
  loaded = ref(false),
  loading = ref(false),
  listError = shallowRef<unknown>(null),
  root = ref<HTMLElement | null>(null),
  configOpen = ref(false),
  policyOpen = ref(false),
  uncertain = ref(false)
const draft = ref<{
  id?: string
  config?: NotifyCandidate
  policy: NotifyPolicy
  original?: NotifyPolicy
}>()
const editor = useEditor(
  () =>
    !draft.value || (draft.value.id && draft.value.policy === draft.value.original)
      ? null
      : draft.value,
  () => emit('close'),
)
const { busy, error, submitted, close, confirm, focusError } = editor
const controller = new AbortController()
const selectOpen = computed(() => configOpen.value || policyOpen.value)
let listController: AbortController | undefined
onBeforeUnmount(() => {
  controller.abort()
  listController?.abort()
})
watch(allowed, (value) => {
  if (!value) {
    controller.abort()
    listController?.abort()
    rows.value = []
    draft.value = undefined
    emit('close')
  }
})
const bound = computed(() => rows.value.map((r) => r.config.id))
const missing = computed(
  () => !!draft.value?.id && !rows.value.some((r) => r.id === draft.value?.id),
)
const visible = computed(() => {
  const result = rows.value.slice((page.value - 1) * 20, page.value * 20)
  const current = draft.value
  // A refresh may remove or move the row. Keep its draft visible until explicitly cancelled.
  if (current?.id && current.config && !result.some((r) => r.id === current.id))
    result.push({
      id: current.id,
      targetId: props.row.id,
      config: current.config,
      policy: current.original!,
    })
  return result
})
const policies = computed(() =>
  notifyPolicies.map((value) => ({ value, label: t('bindings.' + value) })),
)
const columns = computed(() => [
  { key: 'config', label: t('bindings.config'), required: true },
  { key: 'policy', label: t('bindings.policy'), required: true },
  { key: 'actions', label: t('bindings.actions') },
])
const invalid = computed(
  () =>
    !!draft.value &&
    !draft.value.id &&
    (!draft.value.config || bound.value.includes(draft.value.config.id)),
)
const disabledConfig = computed(() => draft.value?.config && !draft.value.config.enabled)
async function refresh() {
  if (loading.value || !allowed.value) return
  const request = new AbortController()
  listController = request
  loading.value = true
  listError.value = null
  try {
    const params = new URLSearchParams({ targetType: props.targetType, targetId: props.row.id })
    const result = notifyBindings(
      await runtime.request('/api/notify-bindings?' + params, { signal: request.signal }),
      props.row.id,
      props.targetType,
    )
    if (request.signal.aborted) return
    rows.value = result
    loaded.value = true
    uncertain.value = false
    page.value = Math.min(page.value, Math.max(1, Math.ceil(result.length / 20)))
  } catch (e) {
    if (!request.signal.aborted) listError.value = e
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}
async function begin(row?: NotifyBinding) {
  if (
    busy.value ||
    loading.value ||
    draft.value ||
    !allowed.value ||
    !loaded.value ||
    listError.value
  )
    return
  submitted.value = false
  error.value = null
  draft.value = row
    ? { id: row.id, config: row.config, policy: row.policy, original: row.policy }
    : { policy: 'ON_FAILURE' }
  await nextTick()
  root.value
    ?.querySelector<HTMLInputElement>(row ? '#binding-policy-' + row.id : '#binding-config')
    ?.focus()
}
function cancel() {
  if (!busy.value) {
    draft.value = undefined
    submitted.value = false
    error.value = null
    configOpen.value = false
    policyOpen.value = false
  }
}
async function save() {
  if (draft.value?.id && draft.value.policy === draft.value.original) return
  if (busy.value || loading.value || !draft.value || !allowed.value || uncertain.value) return
  submitted.value = true
  error.value = null
  if (missing.value) return
  if (invalid.value) {
    await nextTick()
    focusError(root.value)
    return
  }
  const current = draft.value
  busy.value = true
  try {
    if (current.id)
      await runtime.request('/api/notify-bindings/' + current.id, {
        method: 'PUT',
        body: { notifyPolicy: current.policy },
        signal: controller.signal,
      })
    else
      bindingId(
        await runtime.request('/api/notify-bindings', {
          method: 'POST',
          body: bindingBody(props.row.id, current.config!.id, current.policy, props.targetType),
          signal: controller.signal,
        }),
      )
    if (controller.signal.aborted) return
    draft.value = undefined
    submitted.value = false
    configOpen.value = false
    policyOpen.value = false
    message.success(() => h('span', { role: 'status' }, t('bindings.saved')))
    await refresh()
  } catch (e) {
    if (!controller.signal.aborted) {
      error.value = e
      uncertain.value = e instanceof ApiError && ['network', 'timeout', 'protocol'].includes(e.kind)
    }
  } finally {
    busy.value = false
  }
}
async function remove(row: NotifyBinding) {
  if (busy.value || draft.value || loading.value || !allowed.value || uncertain.value) return
  busy.value = true
  try {
    if (
      !(await confirm(
        t('bindings.remove'),
        t('bindings.removeHint', { task: props.row.name, config: row.config.name }),
      )) ||
      controller.signal.aborted ||
      !allowed.value
    )
      return
    error.value = null
    await runtime.request('/api/notify-bindings/' + row.id, {
      method: 'DELETE',
      signal: controller.signal,
    })
    if (controller.signal.aborted) return
    message.success(() => h('span', { role: 'status' }, t('bindings.removed')))
    await refresh()
  } catch (e) {
    if (!controller.signal.aborted) {
      error.value = e
      uncertain.value = e instanceof ApiError && ['network', 'timeout', 'protocol'].includes(e.kind)
    }
  } finally {
    busy.value = false
  }
}
void refresh()
</script>
<template>
  <NDrawer
    show
    :width="520"
    class="task-drawer binding-drawer"
    :mask-closable="false"
    :close-on-esc="!busy && !selectOpen"
    @update:show="close"
  >
    <NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="task-drawer-heading">
          <span>{{ t('bindings.title') }}</span
          ><NButton
            quaternary
            circle
            :disabled="busy"
            :aria-label="t('common.close')"
            @click="close"
            ><X :size="18"
          /></NButton></div
      ></template>
      <div ref="root" class="binding-content">
        <div class="binding-context">
          <strong>{{ row.name }}</strong
          ><span
            >{{ targetType === 'JOB' ? t('jobs.job') : row.pluginType }} · ID {{ row.id }}</span
          >
        </div>
        <p class="task-note">{{ t('bindings.hint') }}</p>
        <div v-if="!loaded" class="binding-refresh">
          <span v-if="loading" role="status">{{ t('common.loading') }}</span
          ><NButton size="small" :disabled="busy || loading" @click="refresh">{{
            t('tasks.refresh')
          }}</NButton>
        </div>
        <RequestError :error="listError" />
        <EditableTable
          v-if="loaded"
          :title="t('bindings.title')"
          :columns="columns"
          :count="rows.length"
          :has-draft="!!draft"
          :add-label="t('bindings.add')"
          :disabled="busy || loading || !!draft || !!listError || uncertain"
          @add="begin()"
        >
          <template #tools
            ><NButton
              size="small"
              :disabled="busy || loading"
              :loading="loading"
              :aria-label="t('tasks.refresh')"
              @click="refresh"
              >{{ t('tasks.refresh') }}</NButton
            ></template
          >
          <tr v-if="draft && !draft.id">
            <td>
              <NotifyConfigPicker
                :value="draft.config"
                :bound="bound"
                :disabled="busy"
                :invalid="submitted && invalid"
                @select="draft.config = $event"
                @show="configOpen = $event"
              />
              <p
                v-if="submitted && invalid"
                id="binding-config-error"
                class="binding-field-error"
                role="alert"
              >
                {{ t(draft.config ? 'bindings.duplicate' : 'bindings.required') }}
              </p>
            </td>
            <td>
              <CompactSelect
                id="binding-policy-new"
                :value="draft.policy"
                :label="t('bindings.policy') + ' ' + t('bindings.new')"
                :options="policies"
                :disabled="busy"
                size="small"
                @update:value="draft.policy = $event as NotifyPolicy"
                @show="policyOpen = $event"
              />
            </td>
            <td>
              <span class="task-muted">{{ t('bindings.new') }}</span>
            </td>
          </tr>
          <tr v-for="item in visible" :key="item.id">
            <td>
              <div class="binding-config-name">{{ item.config.name }}</div>
              <small class="task-muted"
                >{{ item.config.type }} ·
                {{ t(item.config.enabled ? 'bindings.enabled' : 'bindings.disabled') }}</small
              >
            </td>
            <td>
              <CompactSelect
                v-if="draft?.id === item.id"
                :id="'binding-policy-' + item.id"
                :value="draft.policy"
                :label="t('bindings.policy') + ' ' + item.config.name"
                :options="policies"
                :disabled="busy"
                size="small"
                @update:value="draft.policy = $event as NotifyPolicy"
                @show="policyOpen = $event"
              /><span v-else>{{ t('bindings.' + item.policy) }}</span>
            </td>
            <td>
              <div class="binding-row-actions">
                <NButton
                  size="tiny"
                  text
                  :disabled="busy || loading || !!draft || !!listError || uncertain"
                  :aria-label="t('bindings.edit') + ' ' + item.config.name"
                  @click="begin(item)"
                  >{{ t('bindings.edit') }}</NButton
                ><NButton
                  size="tiny"
                  text
                  type="error"
                  :disabled="busy || loading || !!draft || !!listError || uncertain"
                  :aria-label="t('bindings.remove') + ' ' + item.config.name"
                  @click="remove(item)"
                  >{{ t('bindings.remove') }}</NButton
                >
              </div>
            </td>
          </tr>
        </EditableTable>
        <div v-if="loaded && rows.length > 20" class="binding-pagination">
          <span>{{ t('common.pagination', { page, total: rows.length }) }}</span
          ><NButton
            size="small"
            :disabled="!!draft || busy || loading || page <= 1"
            @click="page--"
            >{{ t('common.previous') }}</NButton
          ><NButton
            size="small"
            :disabled="!!draft || busy || loading || page * 20 >= rows.length"
            @click="page++"
            >{{ t('common.next') }}</NButton
          >
        </div>
        <p v-if="disabledConfig" class="task-note" role="status">
          {{ t('bindings.disabledHint') }}
        </p>
        <RequestError :error="error" />
        <p v-if="missing" class="binding-field-error" role="alert">{{ t('bindings.missing') }}</p>
        <p v-if="error" class="task-note">
          {{ t(uncertain ? 'bindings.uncertain' : 'bindings.recover') }}
        </p>
        <div class="binding-notes">
          <p class="task-note">{{ t('bindings.policyHint') }}</p>
          <p class="task-note">{{ t('bindings.scope') }}</p>
          <p v-if="draft?.id" class="task-note">{{ t('bindings.concurrent') }}</p>
        </div>
      </div>
      <template #footer
        ><div class="binding-footer">
          <span v-if="draft" class="task-note">{{
            t(draft.id ? 'bindings.editing' : 'bindings.adding', {
              name: draft.config?.name ?? t('bindings.new'),
            })
          }}</span>
          <div class="task-footer">
            <template v-if="draft"
              ><NButton :disabled="busy" @click="cancel">{{ t('common.cancel') }}</NButton
              ><NButton
                type="primary"
                :aria-label="t('bindings.save')"
                :loading="busy"
                :disabled="
                  busy ||
                  loading ||
                  uncertain ||
                  !!listError ||
                  missing ||
                  (!!draft.id && draft.policy === draft.original)
                "
                @click="save"
                >{{ t('bindings.save') }}</NButton
              ></template
            ><NButton v-else :disabled="busy" @click="close">{{ t('common.close') }}</NButton>
          </div>
        </div></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
