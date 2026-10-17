<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { NButton, NSelect } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import RequestError from '../../shared/ui/RequestError.vue'
import { exactId, pageData, record } from './model'
defineProps<{ id: string; label: string; disabled?: boolean; invalid?: boolean }>()
const value = defineModel<string>('value', { required: true })
const emit = defineEmits<{ select: [boolean] }>()
const runtime = useRuntime(),
  { t } = useI18n()
const rows = ref<{ value: string; label: string; disabled?: boolean }[]>([]),
  selected = ref<{ value: string; label: string; disabled?: boolean }>(),
  busy = ref(false),
  error = shallowRef<unknown>(null)
const search = ref(''),
  page = ref(0),
  total = ref(0)
const controller = new AbortController()
let retryAppend = false
let generation = 0,
  resolution = 0,
  timer: ReturnType<typeof setTimeout> | undefined
async function load(append = false) {
  const version = ++generation
  const requestedPage = append ? page.value + 1 : 1
  retryAppend = append
  busy.value = true
  try {
    const result = pageData(
      await runtime.request(
        '/api/resources?' +
          new URLSearchParams({
            type: 'FILE',
            enabled: 'true',
            name: search.value,
            pageNum: String(requestedPage),
            pageSize: '20',
          }),
        { signal: controller.signal },
      ),
      (v) => {
        const row = record(v)
        if (row.type !== 'FILE' || row.enabled !== true || typeof row.name !== 'string')
          throw Error('Invalid file resource')
        return { value: exactId(row.id), label: row.name + ' · #' + exactId(row.id) }
      },
    )
    if (version !== generation || controller.signal.aborted) return
    rows.value = append
      ? [...new Map([...rows.value, ...result.list].map((v) => [v.value, v])).values()]
      : result.list
    page.value = requestedPage
    total.value = result.total
    error.value = null
  } catch (e) {
    if (version === generation && !controller.signal.aborted) error.value = e
  } finally {
    if (version === generation) busy.value = false
  }
}
async function resolve() {
  const version = ++resolution,
    id = value.value
  selected.value = undefined
  if (!id) return
  const found = rows.value.find((r) => r.value === id)
  if (found) {
    selected.value = found
    return
  }
  try {
    const r = record(
      await runtime.request('/api/resources/' + id, {
        signal: controller.signal,
        localForbidden: true,
      }),
    )
    if (version !== resolution || value.value !== id || controller.signal.aborted) return
    selected.value = {
      value: id,
      label:
        (typeof r.name === 'string' ? r.name : id) +
        ' · #' +
        id +
        (r.type === 'FILE' && r.enabled === true ? '' : ' · ' + t('files.unavailable')),
      disabled: r.type !== 'FILE' || r.enabled !== true,
    }
  } catch {
    if (version === resolution && !controller.signal.aborted)
      selected.value = {
        value: id,
        label: '#' + id + ' · ' + t('files.unavailable'),
        disabled: true,
      }
  }
}
watch(value, () => void resolve(), { immediate: true })
const options = computed(() =>
  value.value && !rows.value.some((r) => r.value === value.value)
    ? [selected.value ?? { value: value.value, label: '#' + value.value }, ...rows.value]
    : rows.value,
)
function query(text: string) {
  search.value = text
  page.value = 0
  generation++
  busy.value = true
  error.value = null
  clearTimeout(timer)
  timer = setTimeout(() => void load(), 200)
}
function more(e: Event) {
  const el = e.target as HTMLElement
  if (
    !busy.value &&
    !error.value &&
    page.value * 20 < total.value &&
    el.scrollTop + el.clientHeight >= el.scrollHeight - 40
  ) {
    void load(true)
  }
}
onBeforeUnmount(() => {
  controller.abort()
  clearTimeout(timer)
})
void load()
</script>
<template>
  <NSelect
    :value="value || null"
    :disabled="disabled"
    filterable
    remote
    :options="options"
    :loading="busy"
    :input-props="{ id, 'aria-label': label, 'aria-invalid': invalid || selected?.disabled }"
    @update:value="value = $event"
    @search="query"
    @scroll="more"
    @update:show="emit('select', $event)"
  /><RequestError :error="error" /><NButton
    v-if="error"
    text
    :disabled="disabled"
    @click="load(retryAppend)"
    >{{ t('common.retry') }}</NButton
  >
  <p v-if="selected?.disabled" class="task-note" role="alert">{{ t('files.unavailable') }}</p>
</template>
