<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  NButton,
  NCollapse,
  NCollapseItem,
  NInput,
  NInputNumber,
  NSelect,
  NSwitch,
  NTag,
  useDialog,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import EditableTable from '../../shared/ui/EditableTable.vue'
import NotifyConfigPicker from './NotifyConfigPicker.vue'
import { notifyCandidate, type NotifyCandidate } from './notify-binding-model'
import {
  checkTypes,
  comparisons,
  newCheck,
  type CheckType,
  type DataCheckDraft,
  type ValueKind,
} from './data-check-model'
const SqlEditor = defineAsyncComponent(() => import('../../shared/ui/SqlEditor.vue'))
const draft = defineModel<DataCheckDraft>('draft', { required: true })
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
}>()
const emit = defineEmits<{ expanded: [boolean]; select: [boolean] }>()
const { t } = useI18n(),
  runtime = useRuntime(),
  dialog = useDialog()
const selected = ref(0),
  expanded = ref<string[]>([]),
  host = ref<HTMLElement>(),
  sql = ref<{ focus: () => void }>()
const item = computed(() => draft.value.checks[selected.value])
const error = (key: string) =>
  props.submitted && props.errors['check.' + key] ? t('dataCheck.invalid') : undefined
const field = (key: string) => `${selected.value}.${key}`
const id = (key: string) => 'task-check.' + field(key)
const options = (values: readonly string[], prefix: string) =>
  values.map((value) => ({ value, label: t(`dataCheck.${prefix.replace('.', '')}${value}`) }))
const kindOptions = computed(() =>
  options(['text', 'number', 'boolean', 'expression', 'json'], 'kind.'),
)
async function confirm(content: string) {
  return new Promise<boolean>((resolve) =>
    dialog.warning({
      title: t('common.confirm'),
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
function add() {
  if (props.disabled || draft.value.checks.length >= 100) return
  draft.value.checks.push(newCheck())
  selected.value = draft.value.checks.length - 1
  void nextTick(() => host.value?.querySelector<HTMLElement>('[id="' + id('name') + '"]')?.focus())
}
async function remove(index: number) {
  if (props.disabled || !(await confirm(t('dataCheck.removeHint')))) return
  if (props.disabled) return
  draft.value.checks.splice(index, 1)
  selected.value = Math.max(0, Math.min(selected.value, draft.value.checks.length - 1))
}
async function changeType(type: CheckType) {
  const current = item.value
  if (!current || props.disabled || current.type === type) return
  if (!(await confirm(t('dataCheck.typeHint'))) || props.disabled) return
  current.type = type
  current.compareType = 'EQ'
  current.expected =
    type === 'EXISTS'
      ? { kind: 'boolean', text: 'true' }
      : { kind: type === 'COUNT' ? 'number' : 'text', text: '' }
  current.minValue = { kind: type === 'COUNT' ? 'number' : 'text', text: '' }
  current.maxValue = { kind: type === 'COUNT' ? 'number' : 'text', text: '' }
}
async function changeCompare(compare: string) {
  const current = item.value
  if (!current || props.disabled || current.compareType === compare) return
  if (
    (compare === 'BETWEEN' || current.compareType === 'BETWEEN') &&
    !(await confirm(t('dataCheck.conditionHint')))
  )
    return
  if (props.disabled) return
  current.compareType = compare
}
function changeKind(key: 'expected' | 'minValue' | 'maxValue', kind: ValueKind) {
  if (!item.value || props.disabled) return
  item.value[key].kind = kind
  if (kind === 'boolean' && !['true', 'false'].includes(item.value[key].text))
    item.value[key].text = 'true'
}
async function focusError(key: string) {
  const parts = key.split('.')
  if (/^\d+$/.test(parts[1] ?? '')) selected.value = Number(parts[1])
  else expanded.value = ['handlers', 'query']
  await nextTick()
  if (parts[2] === 'sql') sql.value?.focus()
  else host.value?.querySelector<HTMLElement>('[id="task-' + key + '"]')?.focus()
}
const candidate = ref<NotifyCandidate>(),
  notifyLoading = ref(false),
  notifyInvalid = ref(false)
let controller: AbortController | undefined
watch(
  () => draft.value.notifyConfigId,
  async (value) => {
    controller?.abort()
    candidate.value = undefined
    notifyInvalid.value = false
    notifyLoading.value = false
    if (!value) return
    const request = new AbortController()
    controller = request
    notifyLoading.value = true
    try {
      const row = notifyCandidate(
        await runtime.request('/api/notify-configs/' + value, { signal: request.signal }),
      )
      if (request.signal.aborted) return
      candidate.value = row
      notifyInvalid.value = !row.enabled
    } catch {
      if (!request.signal.aborted) notifyInvalid.value = true
    } finally {
      if (!request.signal.aborted) notifyLoading.value = false
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
defineExpose({
  focusError,
  validateNotify: () =>
    !draft.value.notify ||
    (!notifyLoading.value && !notifyInvalid.value && candidate.value?.enabled === true),
})
</script>
<template>
  <div ref="host" class="data-check-fields">
    <p v-if="draft.blocked" role="alert">{{ t('dataCheck.unsupported') }}</p>
    <template v-else>
      <p class="task-note">{{ t('dataCheck.saveHint') }}</p>
      <EditableTable
        :title="t('dataCheck.checks')"
        :count="draft.checks.length"
        :add-label="t('dataCheck.add')"
        :disabled="disabled || draft.checks.length >= 100"
        :columns="
          ['name', 'type', 'level', 'actions'].map((key) => ({
            key,
            label: t('dataCheck.' + (key === 'type' || key === 'level' ? key + 'Label' : key)),
          }))
        "
        @add="add"
      >
        <tr
          v-for="(row, index) in draft.checks"
          :key="row.key"
          :class="{ 'check-selected': selected === index }"
        >
          <td>
            <NButton text type="primary" :disabled="disabled" @click="selected = index">{{
              row.name || t('dataCheck.unnamed', { n: index + 1 })
            }}</NButton>
          </td>
          <td>{{ t('dataCheck.type' + row.type) }}</td>
          <td>
            <NTag size="small" :type="row.level === 'ERROR' ? 'error' : 'warning'">{{
              row.level
            }}</NTag>
          </td>
          <td>
            <NButton
              text
              type="error"
              :disabled="disabled"
              :aria-label="t('dataCheck.remove', { name: row.name || index + 1 })"
              @click="remove(index)"
              >{{ t('dataCheck.delete') }}</NButton
            >
          </td>
        </tr>
      </EditableTable>
      <p v-if="error('checks')" role="alert">{{ error('checks') }}</p>
      <section v-if="item" :key="item.key" class="check-editor">
        <h3>{{ t('dataCheck.item', { n: selected + 1 }) }}</h3>
        <FormItem
          :id="id('name')"
          :label="t('dataCheck.name')"
          required
          :invalid="!!error(field('name'))"
          :message="error(field('name'))"
          ><NInput
            v-model:value="item.name"
            :disabled="disabled"
            :maxlength="128"
            :input-props="{ id: id('name') }"
        /></FormItem>
        <div class="task-advanced-grid">
          <FormItem :id="id('type')" :label="t('dataCheck.typeLabel')" required
            ><NSelect
              filterable
              :value="item.type"
              :disabled="disabled"
              :options="options(checkTypes, 'type.')"
              :input-props="{ id: id('type') }"
              @update:value="changeType"
              @update:show="emit('select', $event)"
          /></FormItem>
          <FormItem :id="id('level')" :label="t('dataCheck.levelLabel')" required
            ><NSelect
              v-model:value="item.level"
              filterable
              :disabled="disabled"
              :options="options(['ERROR', 'WARN'], 'level.')"
              :input-props="{ id: id('level') }"
              @update:show="emit('select', $event)"
          /></FormItem>
        </div>
        <FormItem
          :id="id('sql')"
          :label="t('dataCheck.sql')"
          required
          :invalid="!!error(field('sql'))"
          :message="error(field('sql'))"
          ><SqlEditor
            ref="sql"
            v-model="item.sql"
            :context-key="item.key"
            :disabled="disabled"
            :invalid="!!error(field('sql'))"
            @expanded="emit('expanded', $event)"
        /></FormItem>
        <p class="task-note">{{ t('dataCheck.hint' + item.type) }}</p>
        <template v-if="item.type !== 'EMPTY'">
          <FormItem :id="id('compareType')" :label="t('dataCheck.compareLabel')" required
            ><NSelect
              filterable
              :value="item.compareType"
              :disabled="disabled"
              :options="options(item.type === 'EXISTS' ? ['EQ', 'NE'] : comparisons, 'compare.')"
              :input-props="{ id: id('compareType') }"
              @update:value="changeCompare"
              @update:show="emit('select', $event)"
          /></FormItem>
          <div :class="{ 'task-advanced-grid': item.compareType === 'BETWEEN' }">
            <FormItem
              v-for="key in item.compareType === 'BETWEEN'
                ? (['minValue', 'maxValue'] as const)
                : (['expected'] as const)"
              :id="id(key)"
              :key="key"
              :label="t('dataCheck.' + key)"
              required
              :invalid="!!error(field(key))"
              :message="error(field(key))"
            >
              <div class="check-value">
                <NSelect
                  filterable
                  :value="item[key].kind"
                  :disabled="disabled"
                  :options="kindOptions"
                  :aria-label="t('dataCheck.valueKind')"
                  @update:value="changeKind(key, $event)"
                  @update:show="emit('select', $event)"
                />
                <NSelect
                  v-if="item[key].kind === 'boolean'"
                  v-model:value="item[key].text"
                  filterable
                  :disabled="disabled"
                  :options="[
                    { label: 'true', value: 'true' },
                    { label: 'false', value: 'false' },
                  ]"
                  :input-props="{ id: id(key) }"
                  @update:show="emit('select', $event)"
                />
                <NInput
                  v-else
                  v-model:value="item[key].text"
                  :disabled="disabled"
                  :input-props="{ id: id(key) }"
                />
              </div>
            </FormItem>
          </div>
        </template>
        <FormItem
          :id="id('variable')"
          :label="t('dataCheck.variable')"
          :invalid="!!error(field('variable'))"
          :message="error(field('variable'))"
          ><NInput
            v-model:value="item.variable"
            :disabled="disabled"
            :maxlength="128"
            :input-props="{ id: id('variable') }"
        /></FormItem>
        <p class="task-note">{{ t('dataCheck.variableHint') }}</p>
        <p class="task-note">{{ t('dataCheck.levelHint') }}</p>
      </section>
      <NCollapse v-model:expanded-names="expanded">
        <NCollapseItem name="handlers" :title="t('dataCheck.handlers')">
          <div class="task-inline">
            <label for="check-export">{{ t('dataCheck.export') }}</label
            ><NSwitch
              id="check-export"
              v-model:value="draft.exportVariables"
              :disabled="disabled"
              :aria-label="t('dataCheck.export')"
            />
          </div>
          <p class="task-note">{{ t('dataCheck.exportHint') }}</p>
          <div class="task-inline">
            <label for="check-notify">{{ t('dataCheck.notify') }}</label
            ><NSwitch
              id="check-notify"
              v-model:value="draft.notify"
              :disabled="disabled"
              :aria-label="t('dataCheck.notify')"
            />
          </div>
          <template v-if="draft.notify">
            <FormItem
              id="task-check.notifyConfigId"
              :label="t('bindings.config')"
              required
              :invalid="!!error('notifyConfigId') || notifyInvalid"
              :message="notifyInvalid ? t('dataCheck.notifyInvalid') : error('notifyConfigId')"
            >
              <NotifyConfigPicker
                input-id="task-check.notifyConfigId"
                :value="
                  candidate ??
                  (draft.notifyConfigId
                    ? {
                        id: draft.notifyConfigId,
                        name: t('tasks.missingResource', { id: draft.notifyConfigId }),
                        type: 'EMAIL',
                        enabled: false,
                      }
                    : undefined)
                "
                :bound="[]"
                :disabled="disabled"
                :invalid="!!error('notifyConfigId') || notifyInvalid"
                enabled-only
                @select="draft.notifyConfigId = $event.id"
                @show="emit('select', $event)"
              />
            </FormItem>
            <div class="task-inline">
              <label for="check-success">{{ t('dataCheck.onSuccess') }}</label
              ><NSwitch
                id="check-success"
                v-model:value="draft.notifyOnSuccess"
                :disabled="disabled"
                :aria-label="t('dataCheck.onSuccess')"
              />
            </div>
            <p class="task-note">{{ t('dataCheck.notifyHint') }}</p>
          </template>
        </NCollapseItem>
        <NCollapseItem name="query" :title="t('dataCheck.querySettings')">
          <FormItem
            id="task-check.queryTimeoutSeconds"
            :label="t('dataCheck.timeout')"
            required
            :invalid="!!error('queryTimeoutSeconds')"
            :message="error('queryTimeoutSeconds')"
            ><NInputNumber
              v-model:value="draft.queryTimeoutSeconds"
              :disabled="disabled"
              :min="1"
              :max="3600"
              :precision="0"
              :input-props="{ id: 'task-check.queryTimeoutSeconds' }"
          /></FormItem>
          <p class="task-note">{{ t('dataCheck.timeoutHint') }}</p>
        </NCollapseItem>
      </NCollapse>
    </template>
  </div>
</template>
<style scoped>
.check-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 14px;
  margin: 12px 0;
}
.check-editor h3 {
  margin: 0;
  font-size: 13px;
}
.check-selected :deep(td) {
  background: color-mix(in srgb, #3578ed 9%, transparent);
}
.check-value {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 8px;
  width: 100%;
}
.data-check-fields :deep(.editable-table) {
  min-width: 0;
}
.data-check-fields :deep(td) {
  overflow-wrap: anywhere;
}
.data-check-fields :deep(td .n-button__content) {
  white-space: normal;
  text-align: left;
}
@media (max-width: 480px) {
  .check-value {
    grid-template-columns: 1fr;
  }
  .check-editor {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 10px;
  }
}
</style>
