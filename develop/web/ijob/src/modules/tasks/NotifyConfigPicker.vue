<script setup lang="ts">
import { computed, h, nextTick, onBeforeUnmount, ref, shallowRef } from 'vue'
import { NButton, NSelect } from 'naive-ui'
import type { SelectInst, SelectRenderLabel, SelectRenderTag } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import RequestError from '../../shared/ui/RequestError.vue'
import { pageData } from './model'
import { notifyCandidate, type NotifyCandidate } from './notify-binding-model'
const props = defineProps<{
  inputId?: string
  enabledOnly?: boolean
  value?: NotifyCandidate
  bound: string[]
  disabled: boolean
  invalid: boolean
}>()
const emit = defineEmits<{ select: [NotifyCandidate]; show: [boolean] }>()
const runtime = useRuntime(),
  { t } = useI18n()
const picker = ref<SelectInst | null>(null)
const rows = ref<NotifyCandidate[]>([]),
  page = ref(1),
  total = ref(0),
  search = ref(''),
  loading = ref(false),
  show = ref(false),
  error = shallowRef<unknown>(null)
let controller: AbortController | undefined,
  timer: ReturnType<typeof setTimeout> | undefined,
  generation = 0
function stop() {
  generation++
  controller?.abort()
  clearTimeout(timer)
}
onBeforeUnmount(() => {
  stop()
  emit('show', false)
})
async function load() {
  stop()
  const current = generation,
    request = new AbortController()
  controller = request
  loading.value = true
  error.value = null
  rows.value = []
  try {
    const params = new URLSearchParams({
      name: search.value,
      pageNum: String(page.value),
      pageSize: '20',
    })
    const result = pageData(
      await runtime.request('/api/notify-configs?' + params, { signal: request.signal }),
      notifyCandidate,
    )
    if (current !== generation || request.signal.aborted) return
    rows.value = result.list
    total.value = result.total
  } catch (e) {
    if (current === generation && !request.signal.aborted) error.value = e
  } finally {
    if (current === generation) loading.value = false
  }
}
function query(value: string) {
  stop()
  search.value = value
  page.value = 1
  rows.value = []
  total.value = 0
  loading.value = true
  timer = setTimeout(() => void load(), 250)
}
function turn(value: number) {
  page.value = value
  void load()
}
function visibility(value: boolean) {
  show.value = value
  emit('show', value)
}
async function retry() {
  await load()
  if (!picker.value || controller?.signal.aborted) return
  visibility(true)
  await nextTick()
  picker.value?.focus()
}
const options = computed(() =>
  rows.value.map((row) => ({
    value: row.id,
    label: row.name,
    disabled: props.bound.includes(row.id) || (props.enabledOnly && !row.enabled),
    metadata: `${row.type} · ${t(row.enabled ? 'bindings.enabled' : 'bindings.disabled')}${props.bound.includes(row.id) ? ' · ' + t('bindings.bound') : ''}`,
  })),
)
function select(id: string) {
  const row = rows.value.find((r) => r.id === id)
  if (row && !props.bound.includes(id) && (!props.enabledOnly || row.enabled)) emit('select', row)
}
const renderLabel: SelectRenderLabel = (option) =>
  h('div', { class: 'binding-option' }, [
    h('span', String(option.label)),
    h('small', String(option.metadata ?? '')),
  ])
const renderTag: SelectRenderTag = ({ option }) => String(option.label)
const fallback = () => ({ value: props.value!.id, label: props.value!.name })
void load()
</script>
<template>
  <NSelect
    ref="picker"
    :value="value?.id ?? null"
    :options="options"
    :fallback-option="value ? fallback : false"
    :consistent-menu-width="false"
    :menu-props="{ style: { width: 'min(320px, calc(100vw - 32px))' } }"
    :disabled="disabled"
    :loading="loading"
    :show="show"
    filterable
    remote
    size="small"
    :placeholder="t('bindings.choose')"
    :input-props="{
      id: inputId ?? 'binding-config',
      'aria-label': t('bindings.config'),
      'aria-required': true,
      'aria-invalid': invalid || undefined,
      'aria-describedby': invalid ? 'binding-config-error' : undefined,
      role: 'combobox',
      'aria-expanded': show,
    }"
    :render-label="renderLabel"
    :render-tag="renderTag"
    @update:show="visibility"
    @search="query"
    @update:value="select"
  >
    <template #empty>{{ loading ? t('common.loading') : t('bindings.emptyCandidates') }}</template>
    <template #action>
      <div class="binding-candidate-footer" @mousedown.prevent>
        <RequestError :error="error" />
        <NButton v-if="error" size="tiny" @click="retry">{{ t('common.retry') }}</NButton>
        <span>{{ t('common.pagination', { page, total }) }}</span>
        <div>
          <NButton size="tiny" :disabled="loading || page <= 1" @click="turn(page - 1)">{{
            t('common.previous')
          }}</NButton>
          <NButton
            size="tiny"
            :disabled="loading || !!error || page * 20 >= total"
            @click="turn(page + 1)"
            >{{ t('common.next') }}</NButton
          >
        </div>
      </div>
    </template>
  </NSelect>
</template>
