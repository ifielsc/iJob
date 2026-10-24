<!-- Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT -->
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { NButton, NCheckbox, NInput, NModal, NRadio, NRadioGroup } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { record } from '../../shared/api/values'
import RequestError from '../../shared/ui/RequestError.vue'
import { bundle, download, filename, totals, zip, type Bundle, type Selection } from './model'
const props = defineProps<{ kind: 'TASK' | 'JOB'; selected: Selection[] }>(),
  emit = defineEmits<{ close: [] }>()
const runtime = useRuntime(),
  { t } = useI18n()
const selected = ref([...props.selected]),
  activeList = ref<'selection' | 'dependencies'>(),
  search = ref(''),
  name = ref('selected-' + props.kind.toLowerCase() + 's.ijob.zip')
const includeResources = ref(true),
  includeSchedules = ref(true),
  includeNotifications = ref(true),
  busy = ref(false),
  error = shallowRef<unknown>(),
  data = shallowRef<Bundle>(),
  archive = ref('')
let controller: AbortController | undefined
const visible = computed(() =>
  selected.value.filter((r) => r.name.toLowerCase().includes(search.value.toLowerCase())),
)
const counts = computed(() => totals(data.value?.objects ?? []))
const expanded = computed(() => activeList.value === 'selection')
const dependencies = computed(() => activeList.value === 'dependencies')
function toggleList(value: 'selection' | 'dependencies') {
  activeList.value = activeList.value === value ? undefined : value
}
async function load() {
  controller?.abort()
  const current = new AbortController()
  controller = current
  busy.value = true
  error.value = undefined
  data.value = undefined
  archive.value = ''
  try {
    if (!selected.value.length || selected.value.length > 100) return
    const result = record(
      await runtime.request('/api/job-packages/export', {
        method: 'POST',
        body: {
          roots: selected.value.map((r) => ({ kind: props.kind, id: r.id })),
          includeResources: includeResources.value,
          includeSchedules: includeSchedules.value,
          includeNotifications: includeNotifications.value,
        },
        signal: current.signal,
        timeoutMs: 120000,
        lossless: true,
      }),
    )
    if (current.signal.aborted) return
    const inspected = bundle(
      await runtime.request('/api/job-packages/inspect', {
        method: 'POST',
        body: { content: result.content },
        signal: current.signal,
        lossless: true,
        timeoutMs: 120000,
      }),
    )
    if (!current.signal.aborted) {
      data.value = inspected
      archive.value = String(result.content)
    }
  } catch (e) {
    if (!current.signal.aborted) error.value = e
  } finally {
    if (!current.signal.aborted) busy.value = false
  }
}
watch([selected, includeResources, includeSchedules, includeNotifications], () => void load(), {
  immediate: true,
})
watch(
  () => runtime.accessIssue.value,
  (issue) => {
    if (issue) emit('close')
  },
)
onBeforeUnmount(() => controller?.abort())
function save() {
  if (!archive.value || busy.value) return
  download(filename(name.value), zip(archive.value))
  emit('close')
}
</script>
<template>
  <NModal
    :show="true"
    preset="card"
    class="package-modal package-export"
    :class="{ 'package-export-expanded': !!activeList }"
    :title="t(kind === 'TASK' ? 'packages.exportTask' : 'packages.export')"
    :mask-closable="false"
    @close="emit('close')"
    @esc="emit('close')"
  >
    <div class="package-export-layout">
      <div class="package-summary">
        <strong>{{
          t('packages.selected', { count: selected.length, kind: t('packages.kinds.' + kind) })
        }}</strong
        ><NButton text type="primary" size="small" @click="toggleList('selection')">{{
          t(expanded ? 'packages.hideList' : 'packages.viewList')
        }}</NButton>
      </div>
      <p class="package-note">{{ t('packages.deduplicate') }}</p>
      <div v-if="expanded || dependencies" class="package-list-panel">
        <NInput
          v-if="expanded"
          v-model:value="search"
          size="small"
          :placeholder="t('packages.search')"
          :aria-label="t('packages.search')"
        />
        <strong v-else>{{ t('packages.dependencies') }}</strong>
        <div
          class="package-selection-table"
          tabindex="0"
          :aria-label="t(expanded ? 'packages.viewList' : 'packages.viewDependencies')"
        >
          <table v-if="expanded">
            <thead>
              <tr>
                <th>{{ t('packages.object') }}</th>
                <th>{{ t('packages.action') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in visible" :key="row.id">
                <td>
                  {{ row.name }} <small>#{{ row.id }}</small>
                </td>
                <td>
                  <NButton
                    text
                    type="error"
                    size="tiny"
                    @click="selected = selected.filter((r) => r.id !== row.id)"
                    >{{ t('packages.remove') }}</NButton
                  >
                </td>
              </tr>
            </tbody>
          </table>
          <table v-else>
            <thead>
              <tr>
                <th>{{ t('packages.type') }}</th>
                <th>{{ t('packages.object') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="e in data?.objects ?? []" :key="e.key">
                <td>{{ t('packages.kinds.' + e.kind) }}</td>
                <td>{{ e.name === e.kind ? t('packages.kinds.' + e.kind) : e.name }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <label class="package-field package-filename"
        ><span>{{ t('packages.filename') }}</span
        ><NInput v-model:value="name" :maxlength="110" :aria-label="t('packages.filename')"
      /></label>
      <fieldset>
        <legend>{{ t('packages.resources') }}</legend>
        <NRadioGroup v-model:value="includeResources" class="package-radio"
          ><NRadio :value="false">{{ t('packages.references') }}</NRadio
          ><NRadio :value="true">{{ t('packages.includeResources') }}</NRadio></NRadioGroup
        >
        <p class="package-note">
          {{ t('packages.secretsNote') }} {{ t('packages.concurrencyNote') }}
        </p>
      </fieldset>
      <fieldset>
        <legend>{{ t('packages.contents') }}</legend>
        <div class="package-options">
          <NCheckbox v-model:checked="includeSchedules">{{
            t('packages.includeSchedules')
          }}</NCheckbox
          ><NCheckbox v-model:checked="includeNotifications">{{
            t('packages.includeNotifications')
          }}</NCheckbox>
        </div>
        <p class="package-note">{{ t('packages.notificationNote') }}</p>
      </fieldset>
      <RequestError :error="error" />
      <p v-if="busy" role="status">{{ t('packages.loading') }}</p>
      <p v-if="selected.length > 100 || !selected.length" role="alert">
        {{ t(selected.length ? 'packages.limit' : 'packages.empty') }}
      </p>
      <div v-if="data" class="package-counts">
        <span v-for="c in counts" :key="c.kind"
          >{{ t('packages.kinds.' + c.kind) }} <strong>{{ c.count }}</strong></span
        ><NButton text type="primary" size="small" @click="toggleList('dependencies')">{{
          t(dependencies ? 'packages.hideDependencies' : 'packages.viewDependencies')
        }}</NButton>
      </div>
    </div>
    <template #footer
      ><div class="package-footer">
        <NButton @click="emit('close')">{{ t('packages.cancel') }}</NButton
        ><NButton
          type="primary"
          :loading="busy"
          :disabled="!archive || !name.trim()"
          @click="save"
          >{{ t('packages.exportCount', { count: selected.length }) }}</NButton
        >
      </div></template
    >
  </NModal>
</template>
