<script setup lang="ts">
import EmptyState from './EmptyState.vue'
import { computed, defineAsyncComponent, nextTick, ref, watch } from 'vue'
import { NButton, NCheckbox, NInput, NSelect } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import {
  newRow,
  newValue,
  readDocument,
  writeDocument,
  valueKinds,
  type ValueKind,
} from './json-form'
const SqlEditor = defineAsyncComponent(() => import('./SqlEditor.vue'))
const props = defineProps<{
  modelValue: string
  disabled?: boolean
  invalid?: boolean
  title?: string
  contextKey?: string
  shape?: 'object' | 'sqlArray'
  nested?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [string]
  expanded: [boolean]
  select: [boolean]
}>()
const { t } = useI18n()
const mode = ref('form'),
  draft = ref(readDocument('')),
  selected = ref(0),
  problem = ref(''),
  root = ref<HTMLElement>()
let emitted: string | undefined
let formText: string | undefined
function hydrate(text: string) {
  try {
    const next = readDocument(text)
    if (props.shape === 'object' && (next.absent || next.kind !== 'object')) throw Error()
    if (
      props.shape === 'sqlArray' &&
      (next.kind !== 'array' || next.rows.some((r) => r.kind !== 'string'))
    )
      throw Error()
    draft.value = next
    selected.value = next.rows[0]?.key ?? 0
    problem.value = ''
    return true
  } catch {
    mode.value = 'json'
    problem.value = t('jsonForm.advancedOnly')
    return false
  }
}
watch(
  () => props.modelValue,
  (text) => {
    if (text !== emitted) hydrate(text)
  },
  { immediate: true },
)
const row = computed(() => draft.value.rows.find((r) => r.key === selected.value))
const index = computed(() => draft.value.rows.findIndex((r) => r.key === selected.value))
watch([selected, index], async () => {
  await nextTick()
  const table = root.value?.querySelector<HTMLElement>('.json-table-wrap')
  const current = table?.querySelector<HTMLElement>('tr.selected')
  if (!table || !current || table.scrollHeight <= table.clientHeight) return
  const viewport = table.getBoundingClientRect()
  const rect = current.getBoundingClientRect()
  const header = table.querySelector('thead')?.getBoundingClientRect().height ?? 0
  if (rect.bottom > viewport.bottom) table.scrollTop += rect.bottom - viewport.bottom
  else if (rect.top < viewport.top + header) table.scrollTop -= viewport.top + header - rect.top
})
const value = computed(() => row.value ?? draft.value)
const collection = computed(
  () => !draft.value.absent && ['object', 'array'].includes(draft.value.kind),
)
const options = computed(() =>
  valueKinds.map((kind) => ({ value: kind, label: t('jsonForm.' + kind) })),
)
function publish() {
  if (props.disabled) return
  try {
    emitted = writeDocument(draft.value)
    problem.value = ''
  } catch (error) {
    emitted = '{'
    problem.value = t(
      error instanceof Error && error.message === 'duplicate'
        ? 'jsonForm.duplicate'
        : 'jsonForm.invalid',
    )
  }
  emit('update:modelValue', emitted)
}
function advanced(text: string) {
  emitted = text
  emit('update:modelValue', text)
}
function changeMode(next: string) {
  if (next === mode.value) return
  if (next === 'form') {
    if (props.modelValue === formText || hydrate(props.modelValue)) mode.value = 'form'
  } else if (!problem.value) {
    formText = props.modelValue
    mode.value = 'json'
  }
}
function changeRoot(kind: string) {
  draft.value = {
    ...newValue(kind === 'unset' ? 'object' : (kind as ValueKind)),
    absent: kind === 'unset',
    rows: [],
  }
  selected.value = 0
  publish()
}
function changeKind(kind: ValueKind) {
  Object.assign(value.value, newValue(kind))
  publish()
}
function add() {
  let name = 'field' + (draft.value.rows.length + 1)
  while (draft.value.rows.some((r) => r.name === name)) name += '_'
  const entry = newRow(name)
  draft.value.rows.push(entry)
  selected.value = entry.key
  publish()
}
function copy() {
  if (!row.value) return
  const entry = { ...row.value, key: newRow().key, name: row.value.name + '_copy' }
  while (draft.value.rows.some((r) => r.name === entry.name)) entry.name += '_'
  draft.value.rows.splice(index.value + 1, 0, entry)
  selected.value = entry.key
  publish()
}
function remove() {
  const position = index.value
  if (position < 0) return
  draft.value.rows.splice(position, 1)
  selected.value = draft.value.rows[Math.min(position, draft.value.rows.length - 1)]?.key ?? 0
  publish()
}
function move(offset: number) {
  const position = index.value,
    rows = draft.value.rows
  if (position < 0 || !rows[position + offset]) return
  const [entry] = rows.splice(position, 1)
  rows.splice(position + offset, 0, entry!)
  publish()
}
function summary(kind: ValueKind, text: string) {
  if (kind === 'string' && !text) return t('jsonForm.emptyString')
  const preview = props.shape === 'sqlArray' ? text.replace(/\s+/g, ' ').trim() : text
  const limit = props.shape === 'sqlArray' ? 48 : 80
  return preview.length > limit ? preview.slice(0, limit) + '…' : preview
}
defineExpose({
  focus: () => root.value?.querySelector<HTMLElement>('input,textarea,.cm-content,button')?.focus(),
})
</script>
<template>
  <section
    ref="root"
    class="json-form"
    :aria-label="title || t('jsonForm.title')"
    :data-json-context="contextKey"
  >
    <div class="json-toolbar">
      <div class="json-modes">
        <NButton
          size="small"
          :type="mode === 'form' ? 'primary' : 'default'"
          @click="changeMode('form')"
          >{{ t('jsonForm.form') }}</NButton
        ><NButton
          size="small"
          :type="mode === 'json' ? 'primary' : 'default'"
          :disabled="mode === 'form' && !!problem"
          @click="changeMode('json')"
          >{{ t('jsonForm.advanced') }}</NButton
        >
      </div>
      <template v-if="mode === 'form' && !shape && !nested"
        ><label>{{ t('jsonForm.rootType') }}</label
        ><NSelect
          class="json-root-type"
          :value="draft.absent ? 'unset' : draft.kind"
          :options="[
            ...(nested ? [] : [{ value: 'unset', label: t('jsonForm.unset') }]),
            ...options,
          ]"
          :disabled="disabled"
          :aria-label="t('jsonForm.rootType')"
          @update:value="changeRoot"
          @update:show="emit('select', $event)"
      /></template>
    </div>
    <p v-if="problem" class="json-error" role="alert">{{ problem }}</p>
    <SqlEditor
      v-if="mode === 'json'"
      :model-value="modelValue"
      language="JSON"
      :context-key="contextKey + '-advanced'"
      :title="title"
      :disabled="disabled"
      :invalid="invalid"
      @update:model-value="advanced"
      @expanded="emit('expanded', $event)"
    />
    <template v-else>
      <template v-if="collection">
        <div class="json-table-wrap" :class="{ 'json-table-bounded': draft.rows.length > 6 }">
          <table class="json-table" :class="{ 'json-sql-table': shape === 'sqlArray' }">
            <thead>
              <tr>
                <th>{{ t(draft.kind === 'object' ? 'jsonForm.name' : 'jsonForm.index') }}</th>
                <th v-if="shape !== 'sqlArray'">{{ t('jsonForm.type') }}</th>
                <th>{{ t(shape === 'sqlArray' ? 'jsonForm.sqlSummary' : 'jsonForm.value') }}</th>
                <th v-if="draft.kind === 'object'">{{ t('jsonForm.include') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(entry, position) in draft.rows"
                :key="entry.key"
                :class="{ selected: selected === entry.key }"
              >
                <td>
                  <button
                    type="button"
                    :aria-pressed="selected === entry.key"
                    @click="selected = entry.key"
                  >
                    {{
                      draft.kind === 'object' ? entry.name || t('jsonForm.emptyName') : position + 1
                    }}
                  </button>
                </td>
                <td v-if="shape !== 'sqlArray'">{{ t('jsonForm.' + entry.kind) }}</td>
                <td>
                  <button type="button" class="json-summary" @click="selected = entry.key">
                    {{ summary(entry.kind, entry.text) }}
                  </button>
                </td>
                <td v-if="draft.kind === 'object'">
                  <NCheckbox
                    v-model:checked="entry.included"
                    :aria-label="t('jsonForm.include') + ' ' + entry.name"
                    :disabled="disabled"
                    @update:checked="publish"
                  />
                </td>
              </tr>
              <tr v-if="!draft.rows.length">
                <td :colspan="4" class="list-empty-cell">
                  <EmptyState compact :message="t('jsonForm.empty')" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="json-actions">
          <NButton size="small" text type="primary" :disabled="disabled" @click="add"
            >+
            {{
              t(
                shape === 'sqlArray'
                  ? 'jsonForm.addSql'
                  : draft.kind === 'array'
                    ? 'jsonForm.addElement'
                    : 'jsonForm.addField',
              )
            }}</NButton
          ><NButton size="small" text :disabled="disabled || !row" @click="copy">{{
            t('jsonForm.copy')
          }}</NButton
          ><NButton size="small" text :disabled="disabled || index < 1" @click="move(-1)"
            >↑ {{ t('jsonForm.up') }}</NButton
          ><NButton
            size="small"
            text
            :disabled="disabled || index < 0 || index >= draft.rows.length - 1"
            @click="move(1)"
            >↓ {{ t('jsonForm.down') }}</NButton
          ><NButton size="small" text type="error" :disabled="disabled || !row" @click="remove">{{
            t('jsonForm.remove')
          }}</NButton>
        </div>
      </template>
      <div v-if="!draft.absent && (!collection || row)" class="json-current">
        <strong v-if="collection"
          >{{ t(shape === 'sqlArray' ? 'jsonForm.currentSql' : 'jsonForm.current') }} ·
          {{ draft.kind === 'object' ? row?.name : index + 1 }}</strong
        >
        <div v-if="row && draft.kind === 'object'" class="json-inline">
          <label>{{ t('jsonForm.name') }}</label
          ><NInput
            v-model:value="row.name"
            :disabled="disabled"
            :input-props="{ 'aria-label': t('jsonForm.name') }"
            @update:value="publish"
          />
        </div>
        <div v-if="shape !== 'sqlArray' && collection" class="json-inline">
          <label>{{ t('jsonForm.type') }}</label
          ><NSelect
            :value="value.kind"
            :options="options"
            :disabled="disabled"
            :aria-label="t('jsonForm.type')"
            @update:value="changeKind"
            @update:show="emit('select', $event)"
          />
        </div>
        <SqlEditor
          v-if="
            shape === 'sqlArray' || value.kind === 'object' || (value.kind === 'array' && nested)
          "
          v-model="value.text"
          :language="shape === 'sqlArray' ? 'SQL' : 'JSON'"
          :context-key="contextKey + '-' + selected"
          :disabled="disabled"
          @update:model-value="publish"
          @expanded="emit('expanded', $event)"
        />
        <JsonFormEditor
          v-else-if="value.kind === 'array'"
          v-model="value.text"
          nested
          :context-key="contextKey + '-array-' + selected"
          :disabled="disabled"
          @update:model-value="publish"
          @expanded="emit('expanded', $event)"
          @select="emit('select', $event)"
        />
        <div v-else class="json-inline">
          <label>{{ t('jsonForm.value') }}</label
          ><NSelect
            v-if="value.kind === 'boolean'"
            v-model:value="value.text"
            :options="[
              { value: 'true', label: 'true' },
              { value: 'false', label: 'false' },
            ]"
            :disabled="disabled"
            :aria-label="t('jsonForm.value')"
            @update:value="publish"
            @update:show="emit('select', $event)"
          /><NInput
            v-else
            v-model:value="value.text"
            :disabled="disabled || value.kind === 'null'"
            :input-props="{ 'aria-label': t('jsonForm.value') }"
            @update:value="publish"
          />
        </div>
      </div>
      <p v-if="shape === 'sqlArray'" class="json-hint">{{ t('jsonForm.sqlHint') }}</p>
    </template>
  </section>
</template>
<style scoped src="./json-form.css"></style>
