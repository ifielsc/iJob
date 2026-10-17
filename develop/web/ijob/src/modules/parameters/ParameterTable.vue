<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Field } from './model'
import './parameters.css'
const props = defineProps<{
  fields: Field[]
  active: number
  values: string[]
  compact?: boolean
  execution?: boolean
  unbounded?: boolean
}>()
const emit = defineEmits<{ select: [index: number] }>()
const { t } = useI18n()
const scroll = ref<HTMLElement>()
const query = ref('')
const pinned = ref<number>()
const visible = computed(() =>
  props.fields
    .map((field, i) => ({ field, i }))
    .filter(
      ({ field, i }) =>
        i === pinned.value ||
        (field.name + ' ' + (field.description ?? ''))
          .toLocaleLowerCase()
          .includes(query.value.trim().toLocaleLowerCase()),
    ),
)
watch(query, () => {
  pinned.value = undefined
})
function select(i: number) {
  pinned.value = i
  emit('select', i)
}
async function reveal(index: number) {
  if (!visible.value.some((row) => row.i === index)) query.value = ''
  await nextTick()
  const row = scroll.value?.querySelector<HTMLElement>(`tr[data-index="${index}"]`)
  const container = scroll.value
  if (!container || !row) return
  const bounds = container.getBoundingClientRect(),
    target = row.getBoundingClientRect()
  const header = container.querySelector('thead')?.getBoundingClientRect().height ?? 0
  if (target.top < bounds.top + header) container.scrollTop += target.top - bounds.top - header
  else if (target.bottom > bounds.bottom) container.scrollTop += target.bottom - bounds.bottom
}
defineExpose({ reveal })
watch(
  () => [props.active, props.fields.length],
  async () => {
    await nextTick()
    const container = scroll.value
    if (!visible.value.some((row) => row.i === props.active)) query.value = ''
    await nextTick()
    const row = container?.querySelector<HTMLElement>(`tr[data-index="${props.active}"]`)
    if (!container || !row || props.unbounded) return
    const bounds = container.getBoundingClientRect(),
      target = row.getBoundingClientRect()
    const header = container.querySelector('thead')?.getBoundingClientRect().height ?? 0
    if (target.top < bounds.top + header) container.scrollTop += target.top - bounds.top - header
    else if (target.bottom > bounds.bottom) container.scrollTop += target.bottom - bounds.bottom
  },
)
function navigate(event: KeyboardEvent, index: number) {
  const position = visible.value.findIndex((row) => row.i === index)
  const count = visible.value.length
  const next =
    event.key === 'ArrowDown'
      ? Math.min(count - 1, position + 1)
      : event.key === 'ArrowUp'
        ? Math.max(0, position - 1)
        : null
  if (next == null) return
  event.preventDefault()
  select(visible.value[next]!.i)
  const table = (event.currentTarget as HTMLElement).closest('table')!
  const button = table.querySelectorAll<HTMLButtonElement>('.parameter-select-row')[next]
  button?.focus()
}
</script>
<template>
  <div v-if="fields.length > 12 || query" class="parameter-table-tools">
    <input
      v-model="query"
      type="search"
      :placeholder="t('details.searchRows')"
      :aria-label="t('details.searchRows')"
    />
    <span>{{ t('details.rowCounts', { total: fields.length, count: visible.length }) }}</span>
  </div>
  <div
    ref="scroll"
    class="parameter-table-scroll"
    :class="{ 'parameter-table-unbounded': unbounded || fields.length <= 6 }"
    :aria-label="t('parameters.list')"
    tabindex="0"
  >
    <table
      class="parameter-table"
      :class="{ 'parameter-value-table': execution, 'parameter-table-compact': compact }"
    >
      <thead>
        <tr>
          <th
            :title="compact ? t('parameters.fieldName') : undefined"
            :aria-label="compact ? t('parameters.fieldName') : undefined"
          >
            {{ t(compact ? 'parameters.fieldNameCompact' : 'parameters.fieldName') }}
          </th>
          <th>{{ t('parameters.type') }}</th>
          <th
            :title="compact ? t('parameters.required') : undefined"
            :aria-label="compact ? t('parameters.required') : undefined"
          >
            {{ t(compact ? 'parameters.requiredCompact' : 'parameters.required') }}
          </th>
          <th
            :title="compact && execution ? t('parameters.currentValue') : undefined"
            :aria-label="compact && execution ? t('parameters.currentValue') : undefined"
          >
            {{
              t(
                execution
                  ? compact
                    ? 'parameters.currentValueCompact'
                    : 'parameters.currentValue'
                  : 'parameters.defaultValue',
              )
            }}
          </th>
          <th
            :title="compact && execution ? t('parameters.include') : undefined"
            :aria-label="compact && execution ? t('parameters.include') : undefined"
          >
            {{
              t(
                execution
                  ? compact
                    ? 'parameters.includeCompact'
                    : 'parameters.includeShort'
                  : 'tasks.actions',
              )
            }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="{ field, i } in visible"
          :key="i"
          :data-index="i"
          :class="{ 'is-selected': active === i }"
          @click="select(i)"
        >
          <td>
            <button
              type="button"
              class="parameter-select-row"
              :aria-pressed="active === i"
              @click.stop="select(i)"
              @keydown="navigate($event, i)"
            >
              {{ field.name || t('parameters.unnamed') }}
            </button>
          </td>
          <td>
            <span class="parameter-type" :title="t('parameters.' + field.type)">{{
              t('parameters.' + field.type)
            }}</span>
          </td>
          <td>
            <span :class="{ 'parameter-required': field.required }">{{
              field.required ? '●' : '—'
            }}</span>
          </td>
          <td>
            <span class="parameter-preview">{{
              field.sensitive ? '••••••' : values[i] || '—'
            }}</span
            ><slot name="badge" :index="i" />
          </td>
          <td @click.stop><slot name="action" :index="i" /></td>
        </tr>
        <tr v-if="!visible.length">
          <td colspan="5" class="list-empty-cell">
            <EmptyState compact :message="t('parameters.noFields')" />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
