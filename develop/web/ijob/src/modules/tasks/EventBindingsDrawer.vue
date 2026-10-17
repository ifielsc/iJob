<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { NButton, NDrawer, NDrawerContent, NInput, NSwitch, useMessage } from 'naive-ui'
import { X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { ApiError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import EditableTable from '../../shared/ui/EditableTable.vue'
import { useEditor } from '../../shared/forms/use-editor'
import { exactId, record } from './model'
import { validEventCode } from './event-model'
import './notify-bindings.css'
const props = defineProps<{ row: { id: string; name: string }; targetType: 'TASK' | 'JOB' }>()
const emit = defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  message = useMessage()
const code = ref(''),
  adding = ref(false),
  form = ref<HTMLElement>(),
  enabled = ref(true),
  uncertain = ref(false),
  observed = ref(false),
  duplicate = ref('')
const { busy, error, submitted, close, confirm } = useEditor(
  () => ({
    code: code.value,
    enabled: enabled.value,
    uncertain: uncertain.value,
    adding: adding.value,
  }),
  () => emit('close'),
)
const allowed = computed(
  () =>
    can(user.value, 'SENSITIVE_READ') &&
    can(user.value, 'CONFIGURATION_WRITE') &&
    !runtime.accessIssue.value,
)
type Binding = { id: string; eventCode: string; enabled: boolean }
const rows = ref<Binding[]>([]),
  loading = ref(false),
  loaded = ref(false),
  listError = shallowRef<unknown>(null),
  page = ref(1)
const visible = computed(() => rows.value.slice((page.value - 1) * 20, page.value * 20))
const controller = new AbortController()
const invalid = computed(() => !validEventCode(code.value))
const columns = computed(() => [
  { key: 'code', label: t('eventBindings.code'), required: true },
  { key: 'enabled', label: t('eventBindings.enabled') },
  { key: 'actions', label: t('common.actions') },
])
async function begin() {
  if (
    adding.value ||
    busy.value ||
    loading.value ||
    uncertain.value ||
    listError.value ||
    !loaded.value ||
    !allowed.value
  )
    return
  adding.value = true
  submitted.value = false
  duplicate.value = ''
  await nextTick()
  form.value?.querySelector<HTMLInputElement>('#event-binding-code')?.focus()
}
async function cancel() {
  if (busy.value || uncertain.value) return
  if (
    (code.value || !enabled.value) &&
    !(await confirm(t('common.discard'), t('common.discardHint')))
  )
    return
  adding.value = false
  code.value = ''
  enabled.value = true
  submitted.value = false
  duplicate.value = ''
  error.value = null
}
watch([() => runtime.session.revision, () => props.row.id, () => props.targetType, allowed], () => {
  controller.abort()
  rows.value = []
  code.value = ''
  emit('close')
})
onBeforeUnmount(() => controller.abort())
async function refresh(verify = false) {
  if (loading.value || !allowed.value || controller.signal.aborted) return false
  loading.value = true
  listError.value = null
  try {
    const data = await runtime.request(
      '/api/event-bindings?' +
        new URLSearchParams({ targetType: props.targetType, targetId: props.row.id }),
      { signal: controller.signal, localForbidden: true },
    )
    if (controller.signal.aborted) return false
    if (!Array.isArray(data)) throw new ApiError('protocol')
    rows.value = data.map((value) => {
      const r = record(value)
      if (
        exactId(r.targetId) !== props.row.id ||
        r.targetType !== props.targetType ||
        typeof r.eventCode !== 'string' ||
        typeof r.enabled !== 'boolean'
      )
        throw new ApiError('protocol')
      return { id: exactId(r.id), eventCode: r.eventCode, enabled: r.enabled }
    })
    loaded.value = true
    page.value = Math.min(page.value, Math.max(1, Math.ceil(rows.value.length / 20)))
    if (verify) {
      uncertain.value = false
      observed.value = true
      error.value = null
    }
    return true
  } catch (e) {
    if (!controller.signal.aborted) {
      listError.value = e
      if (e instanceof ApiError && e.status === 403) {
        rows.value = []
        code.value = ''
      }
    }
    return false
  } finally {
    loading.value = false
  }
}
async function mutate(action: 'add' | 'toggle' | 'delete', row?: Binding) {
  if (
    busy.value ||
    loading.value ||
    !allowed.value ||
    !loaded.value ||
    listError.value ||
    uncertain.value
  )
    return
  submitted.value = true
  if (action === 'add' && !adding.value) return
  if (action !== 'add' && adding.value) return
  if (action === 'add' && invalid.value) {
    await nextTick()
    form.value?.querySelector<HTMLInputElement>('#event-binding-code')?.focus()
    return
  }
  const eventCode = row?.eventCode ?? code.value
  if (action === 'add' && rows.value.some((r) => r.eventCode === eventCode)) {
    duplicate.value = eventCode
    page.value = Math.floor(rows.value.findIndex((r) => r.eventCode === eventCode) / 20) + 1
    return
  }
  busy.value = true
  try {
    const label =
      action === 'add'
        ? t('eventBindings.add')
        : action === 'delete'
          ? t('eventBindings.remove')
          : t(row!.enabled ? 'eventBindings.disable' : 'eventBindings.enable')
    if (
      !(await confirm(
        t('eventBindings.confirm'),
        t('eventBindings.confirmHint', { name: props.row.name, code: eventCode, action: label }),
      )) ||
      controller.signal.aborted ||
      !allowed.value
    )
      return
    error.value = null
    observed.value = false
    duplicate.value = ''
    const result = await runtime.request('/api/event-bindings' + (row ? '/' + row.id : ''), {
      method: action === 'add' ? 'POST' : action === 'toggle' ? 'PUT' : 'DELETE',
      body:
        action === 'add'
          ? {
              targetType: props.targetType,
              targetId: props.row.id,
              eventCode,
              enabled: enabled.value,
            }
          : action === 'toggle'
            ? { enabled: !row!.enabled }
            : undefined,
      signal: controller.signal,
      localForbidden: true,
    })
    if (controller.signal.aborted) return
    if (action === 'add') {
      exactId(result)
      code.value = ''
      enabled.value = true
      adding.value = false
      submitted.value = false
    }
    message.success(t('eventBindings.saved'))
    await refresh()
  } catch (e) {
    if (controller.signal.aborted) return
    error.value = e
    uncertain.value =
      !(e instanceof ApiError) ||
      ['network', 'timeout', 'protocol'].includes(e.kind) ||
      (e.status ?? 0) >= 500
    if (e instanceof ApiError && e.status === 403) {
      rows.value = []
      code.value = ''
      loaded.value = false
    }
    if (e instanceof ApiError && (e.kind === 'conflict' || e.status === 404)) {
      await refresh()
      if (action === 'add') {
        duplicate.value = eventCode
        const index = rows.value.findIndex((r) => r.eventCode === eventCode)
        if (index >= 0) page.value = Math.floor(index / 20) + 1
      }
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
    class="binding-drawer event-binding-drawer"
    :mask-closable="false"
    :close-on-esc="!busy"
    @update:show="close"
  >
    <NDrawerContent :native-scrollbar="true">
      <template #header
        ><div class="task-drawer-heading">
          <span>{{ t('eventBindings.title') }}</span
          ><NButton
            quaternary
            circle
            :disabled="busy"
            :aria-label="t('common.close')"
            @click="close"
            ><X :size="18"
          /></NButton></div
      ></template>
      <div ref="form" class="binding-content event-binding-body">
        <div>
          <strong>{{ row.name }}</strong
          ><small>{{ t('runEnums.' + targetType) }} · #{{ row.id }}</small>
        </div>
        <p class="task-note">{{ t('eventBindings.hint') }}</p>
        <RequestError :error="listError" /><RequestError :error="error" />
        <div v-if="uncertain" role="alert">
          <p>{{ t('eventBindings.unknown') }}</p>
          <NButton :disabled="busy || loading" @click="refresh(true)">{{
            t('eventBindings.verify')
          }}</NButton>
        </div>
        <p v-if="observed" role="status">{{ t('eventBindings.observed') }}</p>
        <p v-if="duplicate" role="alert">{{ t('eventBindings.duplicate') }} {{ duplicate }}</p>
        <EditableTable
          class="event-binding-table"
          :title="t('eventBindings.title')"
          :columns="columns"
          :count="rows.length"
          :has-draft="adding"
          :add-label="t('eventBindings.add')"
          :disabled="busy || loading || adding || uncertain || !loaded || !!listError"
          @add="begin"
        >
          <template #tools>
            <NButton
              size="small"
              :disabled="busy || uncertain"
              :loading="loading"
              @click="refresh()"
              >{{ t('eventBindings.refresh') }}</NButton
            >
          </template>
          <tr v-if="adding" class="event-binding-draft">
            <td>
              <NInput
                v-model:value="code"
                :maxlength="128"
                :disabled="busy || uncertain"
                :input-props="{
                  id: 'event-binding-code',
                  'aria-label': t('eventBindings.code'),
                  'aria-invalid': submitted && invalid,
                  'aria-describedby': submitted && invalid ? 'event-binding-code-error' : undefined,
                }"
              />
              <p
                v-if="submitted && invalid"
                id="event-binding-code-error"
                class="binding-field-error"
                role="alert"
              >
                {{ t('eventTask.codeInvalid') }}
              </p>
            </td>
            <td>
              <NSwitch
                id="event-binding-enabled"
                v-model:value="enabled"
                :disabled="busy || uncertain"
                :aria-label="t('eventBindings.enabled')"
              />
            </td>
            <td>
              <span class="task-muted">{{ t('bindings.new') }}</span>
            </td>
          </tr>
          <tr
            v-for="item in visible"
            :key="item.id"
            :class="{ 'event-duplicate': item.eventCode === duplicate }"
          >
            <td>{{ item.eventCode }}</td>
            <td>
              <NSwitch
                :value="item.enabled"
                :disabled="busy || loading || adding || uncertain || !!listError"
                :aria-label="t('eventBindings.enabled') + ' ' + item.eventCode"
                @update:value="mutate('toggle', item)"
              />
            </td>
            <td>
              <NButton
                text
                type="error"
                :disabled="busy || loading || adding || uncertain || !!listError"
                :aria-label="t('eventBindings.remove') + ' ' + item.eventCode"
                @click="mutate('delete', item)"
                >{{ t('eventBindings.remove') }}</NButton
              >
            </td>
          </tr>
        </EditableTable>
        <div v-if="rows.length > 20" class="task-inline">
          <NButton :disabled="busy || loading || adding || page === 1" @click="page--">‹</NButton
          ><span>{{ page }} / {{ Math.ceil(rows.length / 20) }}</span
          ><NButton
            :disabled="busy || loading || adding || page * 20 >= rows.length"
            @click="page++"
            >›</NButton
          >
        </div>
        <p class="task-note">{{ t('eventBindings.fixed') }}</p>
        <p v-if="adding" class="task-note">{{ t('eventTask.codeHint') }}</p>
        <p class="task-note">{{ t('eventBindings.effect') }}</p>
      </div>
      <template #footer
        ><div class="binding-footer">
          <span v-if="adding" class="task-note">{{
            t('bindings.adding', { name: code || t('bindings.new') })
          }}</span>
          <div class="task-footer">
            <template v-if="adding">
              <NButton :disabled="busy || uncertain" @click="cancel">{{
                t('common.cancel')
              }}</NButton>
              <NButton
                type="primary"
                :loading="busy"
                :disabled="loading || !loaded || !!listError || uncertain"
                @click="mutate('add')"
                >{{ t('common.save') }}</NButton
              >
            </template>
            <NButton v-else :disabled="busy" @click="close">{{ t('common.close') }}</NButton>
          </div>
        </div></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
<style scoped>
.event-binding-body small {
  display: block;
  font-size: 12px;
  color: var(--muted);
  margin-top: 4px;
}
.event-binding-table :deep(.editable-table) {
  min-width: 320px;
}
.event-binding-table :deep(.editable-col-code) {
  width: 54%;
}
.event-binding-table :deep(.editable-col-enabled) {
  width: 23%;
}
.event-binding-table :deep(.editable-col-actions) {
  width: 23%;
}
.event-binding-table :deep(td) {
  vertical-align: middle;
  overflow-wrap: anywhere;
}
.event-duplicate {
  outline: 1px solid var(--primary);
}
</style>
