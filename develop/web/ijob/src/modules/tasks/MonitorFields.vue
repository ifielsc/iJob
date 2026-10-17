<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { NButton, NCheckbox, NCheckboxGroup, NInput, NSelect, NSwitch } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import LineTabs from '../../shared/ui/LineTabs.vue'
import EtlTable from './EtlTable.vue'
import PushResourcePicker from './PushResourcePicker.vue'
import NotifyConfigPicker from './NotifyConfigPicker.vue'
import MonitorNumber from './MonitorNumber.vue'
import { notifyCandidate, type NotifyCandidate } from './notify-binding-model'
import {
  collectTypes,
  matchTypes,
  monitorApplication,
  monitorThreshold,
  type MonitorDraft,
} from './monitor-model'
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
}>()
const draft = defineModel<MonitorDraft>('draft', { required: true })
const emit = defineEmits<{ select: [boolean] }>()
const { t } = useI18n(),
  runtime = useRuntime(),
  tab = ref('collect'),
  root = ref<HTMLElement>(),
  selected = ref(0)
const tabs = computed(() =>
  ['collect', 'rules', 'notify'].map((value) => ({ value, label: t('monitor.tab_' + value) })),
)
const app = computed(() => draft.value.applications[selected.value])
const metrics = ['cpu', 'memory', 'disk', 'receiveRate', 'sendRate'] as const
const levels = ['warning', 'critical'] as const
const options = (values: readonly string[]) =>
  values.map((value) => ({ value, label: t('monitor.' + value) }))
const error = (key: string) =>
  props.submitted && props.errors['monitor.' + key]
    ? t('monitor.' + props.errors['monitor.' + key])
    : undefined
const label = (i: number, key: string) =>
  t('monitor.rowLabel', { row: i + 1, field: t('monitor.' + key) })
const collected = (key: string) =>
  draft.value.items.includes(key.endsWith('Rate') ? 'NETWORK' : key.toUpperCase())
const keys = new WeakMap<object, number>()
let sequence = 0
function rowKey(row: object) {
  if (!keys.has(row)) keys.set(row, ++sequence)
  return keys.get(row)!
}
function addApplication() {
  draft.value.applications.push(monitorApplication())
  selected.value = draft.value.applications.length - 1
}
function removeApplication(i: number) {
  draft.value.applications.splice(i, 1)
  selected.value = Math.max(0, Math.min(selected.value, draft.value.applications.length - 1))
}
async function focusError(key: string) {
  const path = key.replace(/^monitor\./, '')
  tab.value = path.startsWith('notify')
    ? 'notify'
    : path.startsWith('metrics.') ||
        path.startsWith('paths') ||
        /^applications\.\d+\.(cpu|memory)/.test(path)
      ? 'rules'
      : 'collect'
  if (path.startsWith('applications.')) selected.value = Number(path.split('.')[1]) || 0
  await nextTick()
  root.value?.querySelector<HTMLElement>('[id="task-monitor.' + path + '"]')?.focus()
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
  <section ref="root" class="monitor-fields">
    <p v-if="draft.blocked" role="alert">{{ t('monitor.unsupported') }}</p>
    <template v-else>
      <LineTabs
        id="monitor-tabs"
        :label="t('monitor.title')"
        :value="tab"
        :tabs="tabs"
        panel-id="monitor-panel"
        :disabled="disabled"
        @change="tab = $event"
      />
      <section
        id="monitor-panel"
        role="tabpanel"
        :aria-labelledby="'monitor-tabs-' + tab"
        class="monitor-panel"
      >
        <template v-if="tab === 'collect'">
          <p class="task-note">{{ t('monitor.intro') }}</p>
          <FormItem
            id="task-monitor.resourceId"
            :label="t('monitor.resourceId')"
            required
            :invalid="!!error('resourceId')"
            :message="error('resourceId')"
            ><PushResourcePicker
              id="task-monitor.resourceId"
              v-model:value="draft.resourceId"
              type="MONITOR_SERVER"
              :label="t('monitor.resourceId')"
              :disabled="disabled"
              :invalid="!!error('resourceId')"
              @select="emit('select', $event)"
            />
            <p class="task-note">{{ t('monitor.resourceHelp') }}</p></FormItem
          >
          <FormItem
            id="task-monitor.items"
            :label="t('monitor.items')"
            required
            :invalid="!!error('items')"
            :message="error('items')"
            ><div id="task-monitor.items" tabindex="-1">
              <NCheckboxGroup v-model:value="draft.items" :disabled="disabled"
                ><div class="monitor-checks">
                  <NCheckbox v-for="key in collectTypes" :key="key" :value="key">{{
                    t('monitor.' + key)
                  }}</NCheckbox>
                </div></NCheckboxGroup
              >
            </div></FormItem
          >
          <template v-if="draft.items.includes('APPLICATION')">
            <EtlTable
              :title="t('monitor.applications')"
              :count="draft.applications.length"
              :add-label="t('monitor.addApplication')"
              :disabled="disabled || draft.applications.length >= 64"
              :columns="[
                { key: 'name', label: t('monitor.name'), required: true },
                { key: 'matchType', label: t('monitor.matchType') },
                { key: 'matchValue', label: t('monitor.matchValue'), required: true },
                { key: 'actions', label: t('monitor.actions'), width: '70px' },
              ]"
              @add="addApplication"
            >
              <tr
                v-for="(row, i) in draft.applications"
                :key="rowKey(row)"
                :class="{ 'monitor-selected': selected === i }"
                @focusin="selected = i"
                @click="selected = i"
              >
                <td>
                  <NInput
                    v-model:value="row.name"
                    :disabled="disabled"
                    :input-props="{
                      id: 'task-monitor.applications.' + i + '.name',
                      'aria-label': label(i, 'name'),
                      'aria-invalid': !!error('applications.' + i + '.name'),
                    }"
                  />
                  <p v-if="error('applications.' + i + '.name')" class="monitor-error">
                    {{ error('applications.' + i + '.name') }}
                  </p>
                </td>
                <td>
                  <NSelect
                    v-model:value="row.matchType"
                    :disabled="disabled"
                    :options="options(matchTypes)"
                    :input-props="{ 'aria-label': label(i, 'matchType') }"
                    @update:show="emit('select', $event)"
                  />
                </td>
                <td>
                  <NInput
                    v-model:value="row.matchValue"
                    :disabled="disabled"
                    :input-props="{
                      id: 'task-monitor.applications.' + i + '.matchValue',
                      'aria-label': label(i, 'matchValue'),
                      'aria-invalid': !!error('applications.' + i + '.matchValue'),
                    }"
                  />
                  <p v-if="error('applications.' + i + '.matchValue')" class="monitor-error">
                    {{ error('applications.' + i + '.matchValue') }}
                  </p>
                </td>
                <td class="etl-table-actions">
                  <NButton
                    text
                    type="error"
                    :disabled="disabled"
                    :aria-label="label(i, 'remove')"
                    @click.stop="removeApplication(i)"
                    >{{ t('monitor.remove') }}</NButton
                  >
                </td>
              </tr></EtlTable
            >
            <p class="task-note">{{ t('monitor.applicationHelp') }}</p>
            <div v-if="app" class="monitor-app-options">
              <h3>{{ app.name || t('monitor.name') }} · {{ t('monitor.selectApplication') }}</h3>
              <div class="monitor-checks">
                <NCheckbox
                  v-for="key in ['required', 'allowMultiple', 'enabled'] as const"
                  :id="'task-monitor.applications.' + selected + '.' + key"
                  :key="key"
                  v-model:checked="app[key]"
                  :disabled="disabled"
                  >{{ t('monitor.' + key) }}</NCheckbox
                >
              </div>
              <p v-if="error('applications.' + selected + '.enabled')" class="monitor-error">
                {{ error('applications.' + selected + '.enabled') }}
              </p>
              <p class="task-note">{{ t('monitor.requiredHelp') }}</p>
            </div>
          </template>
          <p class="task-note">{{ t('monitor.sampleHelp') }}</p>
        </template>
        <template v-if="tab === 'rules'">
          <p class="task-note">{{ t('monitor.alarmHelp') }}</p>
          <EtlTable
            :title="t('monitor.baseRules')"
            :count="5"
            :disabled="disabled"
            :columns="[
              { key: 'metric', label: t('monitor.metric'), width: '34%' },
              { key: 'enable', label: t('monitor.enable'), width: '70px' },
              ...levels.map((key) => ({ key, label: t('monitor.' + key) })),
            ]"
          >
            <tr v-for="key in metrics" :key="key">
              <td>
                {{ t('monitor.' + key) }}
                <p v-if="!collected(key)" class="task-note">{{ t('monitor.uncollected') }}</p>
              </td>
              <td>
                <NSwitch
                  v-model:value="draft.metrics[key].enabled"
                  :disabled="disabled || !collected(key)"
                  :aria-label="t('monitor.' + key) + ' ' + t('monitor.enable')"
                />
              </td>
              <td v-for="level in levels" :key="level">
                <MonitorNumber
                  :id="'task-monitor.metrics.' + key + '.' + level"
                  v-model:value="draft.metrics[key][level]"
                  :label="t('monitor.' + key) + ' ' + t('monitor.' + level)"
                  :disabled="disabled || !collected(key) || !draft.metrics[key].enabled"
                  :error="error('metrics.' + key + '.' + level)"
                />
              </td>
            </tr>
          </EtlTable>
          <p class="task-note">{{ t('monitor.thresholdHelp') }}</p>
          <template v-if="draft.items.includes('DISK') && draft.metrics.disk.enabled">
            <EtlTable
              :title="t('monitor.paths')"
              :count="draft.paths.length"
              :add-label="t('monitor.addPath')"
              :disabled="disabled || draft.paths.length >= 128"
              :columns="[
                { key: 'path', label: t('monitor.path'), width: '38%' },
                ...levels.map((key) => ({ key, label: t('monitor.' + key) + ' (%)' })),
                { key: 'actions', label: t('monitor.actions'), width: '70px' },
              ]"
              @add="draft.paths.push({ path: '', threshold: monitorThreshold() })"
            >
              <tr v-for="(row, i) in draft.paths" :key="rowKey(row)">
                <td>
                  <NInput
                    v-model:value="row.path"
                    :disabled="disabled"
                    :input-props="{
                      id: 'task-monitor.paths.' + i + '.path',
                      'aria-label': label(i, 'path'),
                      'aria-invalid': !!error('paths.' + i + '.path'),
                    }"
                  />
                  <p v-if="error('paths.' + i + '.path')" class="monitor-error">
                    {{ error('paths.' + i + '.path') }}
                  </p>
                </td>
                <td v-for="level in levels" :key="level">
                  <MonitorNumber
                    :id="'task-monitor.paths.' + i + '.' + level"
                    v-model:value="row.threshold[level]"
                    :label="label(i, level)"
                    :disabled="disabled"
                    :error="error('paths.' + i + '.' + level)"
                  />
                </td>
                <td class="etl-table-actions">
                  <NButton
                    text
                    type="error"
                    :disabled="disabled"
                    @click="draft.paths.splice(i, 1)"
                    >{{ t('monitor.remove') }}</NButton
                  >
                </td>
              </tr>
            </EtlTable>
            <p class="task-note">{{ t('monitor.pathHelp') }}</p></template
          >
          <template v-if="draft.items.includes('APPLICATION')">
            <EtlTable
              :title="t('monitor.appRules')"
              :count="draft.applications.length * 2"
              :disabled="disabled"
              :columns="[
                { key: 'name', label: t('monitor.name') },
                { key: 'metric', label: t('monitor.metric'), width: '130px' },
                ...levels.map((key) => ({ key, label: t('monitor.' + key) })),
              ]"
            >
              <template v-for="(row, i) in draft.applications" :key="rowKey(row)"
                ><tr v-for="key in ['cpu', 'memory'] as const" :key="key">
                  <td>{{ row.name || t('monitor.name') }}</td>
                  <td>
                    <NCheckbox
                      v-model:checked="row[key === 'cpu' ? 'cpuEnabled' : 'memoryEnabled']"
                      :disabled="disabled || !row.enabled"
                      >{{ t(key === 'cpu' ? 'monitor.appCpu' : 'monitor.appMemory') }}</NCheckbox
                    >
                  </td>
                  <td v-for="level in levels" :key="level">
                    <MonitorNumber
                      :id="'task-monitor.applications.' + i + '.' + key + '.' + level"
                      v-model:value="row[key][level]"
                      :label="row.name + ' ' + t('monitor.' + key) + ' ' + t('monitor.' + level)"
                      :disabled="
                        disabled ||
                        !row.enabled ||
                        !row[key === 'cpu' ? 'cpuEnabled' : 'memoryEnabled']
                      "
                      :error="error('applications.' + i + '.' + key + '.' + level)"
                    />
                  </td></tr
              ></template>
            </EtlTable>
            <p class="task-note">{{ t('monitor.appRuleHelp') }}</p></template
          >
        </template>
        <template v-if="tab === 'notify'">
          <div class="monitor-checks">
            <label for="task-monitor.notify">{{ t('monitor.notify') }}</label
            ><NSwitch
              id="task-monitor.notify"
              v-model:value="draft.notify"
              :disabled="disabled"
              :aria-label="t('monitor.notify')"
            />
          </div>
          <FormItem
            v-if="draft.notify"
            id="task-monitor.notifyConfigId"
            :label="t('monitor.notifyConfigId')"
            required
            :invalid="!!error('notifyConfigId') || notifyInvalid"
            :message="
              notifyInvalid || notifyLoading ? t('monitor.notifyInvalid') : error('notifyConfigId')
            "
            ><NotifyConfigPicker
              input-id="task-monitor.notifyConfigId"
              enabled-only
              :value="candidate"
              :bound="[]"
              :disabled="disabled"
              :invalid="!!error('notifyConfigId') || notifyInvalid"
              @select="draft.notifyConfigId = $event.id"
              @show="emit('select', $event)"
          /></FormItem>
          <p class="task-note">{{ t('monitor.notifyHelp') }}</p>
          <h3>{{ t('monitor.results') }}</h3>
          <dl class="monitor-results">
            <template
              v-for="key in ['Healthy', 'AlarmCount', 'WarningCount', 'CriticalCount', 'MaxLevel']"
              :key="key"
              ><dt>
                <code>monitor.{{ key[0]!.toLowerCase() + key.slice(1) }}</code>
              </dt>
              <dd>{{ t('monitor.result' + key) }}</dd></template
            >
          </dl>
          <p class="task-note">{{ t('monitor.resultHelp') }}</p>
        </template>
      </section></template
    >
  </section>
</template>
<style scoped>
.monitor-fields {
  width: 100%;
  min-width: 0;
}
.monitor-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 16px;
  min-width: 0;
}
.monitor-panel .task-note {
  margin: 0;
}
.monitor-checks {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.monitor-app-options {
  display: grid;
  gap: 16px;
  border-top: 1px solid var(--border);
  padding-top: 16px;
}
.monitor-error {
  color: var(--error);
  font-size: 12px;
  margin: 6px 0 0;
}
h3 {
  font-size: 13px;
  margin: 0;
}
.monitor-selected td {
  background: var(--selected) !important;
}
.monitor-results {
  display: grid;
  grid-template-columns: minmax(170px, 1fr) 1fr;
  gap: 10px;
  margin: 0;
  font-size: 12px;
}
.monitor-results dd {
  margin: 0;
  overflow-wrap: anywhere;
}
</style>
