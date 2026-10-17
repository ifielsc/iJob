<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import { NButton, NSelect, type SelectInst } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import RequestError from '../../shared/ui/RequestError.vue'
import { parsePage } from '../security-contracts'
import { parseMailChannel, type MailChannel } from './mail-channel-model'
const props = defineProps<{ value: string; name?: string; disabled?: boolean; invalid?: boolean }>()
const emit = defineEmits<{ change: [string, string | undefined] }>()
const { t } = useI18n(),
  runtime = useRuntime()
const rows = shallowRef<MailChannel[]>([]),
  selected = shallowRef<MailChannel>(),
  page = ref(1),
  total = ref(0),
  search = ref(''),
  loading = ref(false),
  error = shallowRef<unknown>(null)
const picker = ref<SelectInst | null>(null)
let controller: AbortController | undefined
const options = computed(() => [
  ...(props.value && !rows.value.some((row) => row.id === props.value)
    ? [
        {
          value: props.value,
          label:
            selected.value?.id === props.value ? selected.value.name : (props.name ?? props.value),
        },
      ]
    : []),
  ...rows.value.map((row) => ({
    value: row.id,
    label: row.name + ' · ' + row.from + (!row.enabled ? ' · ' + t('mailChannels.disabled') : ''),
    disabled: !row.enabled,
  })),
])
async function load() {
  if (props.disabled) return
  picker.value?.focus()
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = null
  rows.value = []
  try {
    const params = new URLSearchParams({
      name: search.value,
      pageNum: String(page.value),
      pageSize: '50',
    })
    const result = parsePage(
      await runtime.request('/api/mail-channels?' + params, {
        signal: current.signal,
      }),
      parseMailChannel,
    )
    if (controller === current && !current.signal.aborted) {
      rows.value = result.list
      total.value = result.total
    }
  } catch (e) {
    if (!current.signal.aborted) error.value = e
  } finally {
    if (controller === current) loading.value = false
  }
}
function change(id: string) {
  const row = rows.value.find((row) => row.id === id)
  if (props.disabled || !row?.enabled) return
  selected.value = row
  emit('change', id, row.from)
}
function query(value: string) {
  search.value = value
  page.value = 1
  total.value = 0
  void load()
}
function turn(value: number) {
  if (loading.value || props.disabled) return
  page.value = value
  void load()
}
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <NSelect
    ref="picker"
    :value="value || null"
    :options="options"
    :disabled="disabled"
    :loading="loading"
    filterable
    remote
    :input-props="{
      id: 'config-mailChannel',
      'aria-label': t('mailChannels.channel'),
      'aria-invalid': invalid || undefined,
      role: 'combobox',
    }"
    :placeholder="t('mailChannels.searchHint')"
    @update:show="
      (value) => {
        if (value) load()
      }
    "
    @search="query"
    @update:value="change"
  >
    <template #action>
      <div class="mail-channel-candidate-footer" @mousedown.prevent>
        <RequestError :error="error" />
        <NButton v-if="error" size="tiny" :disabled="loading || disabled" @click="load">{{
          t('common.retry')
        }}</NButton>
        <span>{{ t('common.pagination', { page, total }) }}</span>
        <div>
          <NButton
            size="tiny"
            :disabled="loading || disabled || page <= 1"
            @click="turn(page - 1)"
            >{{ t('common.previous') }}</NButton
          >
          <NButton
            size="tiny"
            :disabled="loading || disabled || !!error || page * 50 >= total"
            @click="turn(page + 1)"
            >{{ t('common.next') }}</NButton
          >
        </div>
      </div>
    </template>
  </NSelect>
  <RequestError :error="error" />
  <p class="configuration-note">
    {{ t(value ? 'mailChannels.selectionHint' : 'mailChannels.requiredHint') }}
  </p>
</template>
<style scoped>
.mail-channel-candidate-footer {
  display: grid;
  gap: 8px;
}
.mail-channel-candidate-footer > div:last-child {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
</style>
