<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import {
  NButton,
  NCollapse,
  NCollapseItem,
  NInput,
  NInputNumber,
  NSelect,
  NSwitch,
  useDialog,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { ApiError } from '../../shared/api/errors'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import RequestError from '../../shared/ui/RequestError.vue'
import ProbeFileFields from './ProbeFileFields.vue'
import { eventActions, removedEventFields, type EventAction, type EventDraft } from './event-model'
import { record } from './model'
import JsonFormEditor from '../../shared/ui/JsonFormEditor.vue'
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
}>()
const draft = defineModel<EventDraft>('draft', { required: true })
const timeout = defineModel<number | null>('timeout', { required: true })
const emit = defineEmits<{ expanded: [boolean]; select: [boolean] }>()
const runtime = useRuntime(),
  { t } = useI18n(),
  dialog = useDialog()
const values = computed(() => draft.value.drafts[draft.value.action])
const waiting = computed(() => ['WAIT_EVENT', 'WAIT_APPROVAL'].includes(draft.value.action))
const error = (key: string) =>
  props.submitted && props.errors['event.' + key]
    ? t('eventTask.' + props.errors['event.' + key])
    : undefined
const eventAdvanced = ref<string[]>([])
watch(
  () => [props.submitted, props.errors],
  () => {
    if (
      props.submitted &&
      (props.errors['event.idempotencyKey'] || props.errors['event.occurredAt'])
    )
      eventAdvanced.value = ['eventAdvanced']
  },
)
function removePerson(id: string) {
  if (!props.disabled)
    values.value.allowedOperatorIds = values.value.allowedOperatorIds.filter(
      (value) => value !== id,
    )
}
const unavailablePeople = computed(() =>
  selected.value.filter(
    (person) =>
      person.status !== 'AVAILABLE' && values.value.allowedOperatorIds.includes(person.loginId),
  ),
)
const controller = new AbortController()
const peopleError = shallowRef<unknown>(null),
  loading = ref(false),
  resolving = ref(false),
  more = ref(false),
  search = ref(''),
  page = ref(0)
const failedPage = ref(false)
let retryPeopleAppend = false
type Person = {
  loginId: string
  username: string | null
  displayName: string | null
  status: string
}
const candidates = ref<Person[]>([]),
  selected = ref<Person[]>([])
let generation = 0,
  resolution = 0,
  timer: ReturnType<typeof setTimeout> | undefined,
  savedTimeout: number | null = null
function parsePerson(value: unknown, resolved = false): Person {
  const r = record(value)
  if (
    typeof r.loginId !== 'string' ||
    (r.username != null && typeof r.username !== 'string') ||
    (r.displayName != null && typeof r.displayName !== 'string') ||
    (resolved && !['AVAILABLE', 'MISSING', 'DISABLED', 'FORBIDDEN'].includes(String(r.status)))
  )
    throw new ApiError('protocol')
  return {
    loginId: r.loginId,
    username: r.username as string | null,
    displayName: r.displayName as string | null,
    status: resolved ? (r.status as string) : 'AVAILABLE',
  }
}
const options = computed(() => {
  const all = new Map(candidates.value.map((p) => [p.loginId, p]))
  for (const p of selected.value) all.set(p.loginId, p)
  for (const id of values.value.allowedOperatorIds)
    if (!all.has(id))
      all.set(id, { loginId: id, username: null, displayName: null, status: 'UNRESOLVED' })
  return [...all.values()].map((p) => ({
    value: p.loginId,
    label:
      (p.displayName ? p.displayName + ' · ' : '') +
      (p.username ?? p.loginId) +
      (p.status === 'AVAILABLE' ? '' : ' · ' + t('eventTask.' + p.status)),
    disabled: p.status !== 'AVAILABLE',
  }))
})
const invalidPeople = computed(() =>
  values.value.allowedOperatorIds.some(
    (id) => !selected.value.some((p) => p.loginId === id && p.status === 'AVAILABLE'),
  ),
)
async function loadPeople(append = false) {
  const version = ++generation
  const requestedPage = append ? page.value + 1 : 1
  retryPeopleAppend = append
  loading.value = true
  try {
    const result = await runtime.request(
      '/api/users/approvers?' +
        new URLSearchParams({ pageNum: String(requestedPage), pageSize: '20', name: search.value }),
      { signal: controller.signal, localForbidden: true },
    )
    if (version !== generation || controller.signal.aborted) return
    if (!Array.isArray(result)) throw new ApiError('protocol')
    const rows = result.map((v) => parsePerson(v))
    candidates.value = append ? [...candidates.value, ...rows] : rows
    page.value = requestedPage
    failedPage.value = false
    more.value = rows.length === 20
    peopleError.value = null
  } catch (e) {
    if (!controller.signal.aborted && version === generation) {
      peopleError.value = e
      failedPage.value = true
    }
  } finally {
    if (version === generation) loading.value = false
  }
}
function refreshPeople() {
  void validatePeople()
  void loadPeople(failedPage.value && retryPeopleAppend)
}
function morePeople() {
  if (loading.value || failedPage.value) return
  void loadPeople(true)
}
function searchPeople(value: string) {
  search.value = value
  page.value = 0
  loading.value = true
  failedPage.value = false
  clearTimeout(timer)
  generation++
  timer = setTimeout(() => void loadPeople(), 250)
}
async function validatePeople() {
  if (draft.value.action !== 'WAIT_APPROVAL' || draft.value.blocked) return !draft.value.blocked
  const version = ++resolution,
    ids = [...values.value.allowedOperatorIds]
  resolving.value = true
  try {
    const rows: Person[] = []
    for (let i = 0; i < ids.length; i += 100) {
      const result = await runtime.request('/api/users/approvers/resolve', {
        method: 'POST',
        body: ids.slice(i, i + 100),
        signal: controller.signal,
        localForbidden: true,
      })
      if (!Array.isArray(result)) throw new ApiError('protocol')
      rows.push(...result.map((v) => parsePerson(v, true)))
    }
    if (version !== resolution || controller.signal.aborted) return false
    selected.value = rows
    peopleError.value = null
    return (
      ids.length > 0 &&
      ids.every((id) => rows.some((p) => p.loginId === id && p.status === 'AVAILABLE'))
    )
  } catch (e) {
    if (!controller.signal.aborted && version === resolution) peopleError.value = e
    return false
  } finally {
    if (version === resolution) resolving.value = false
  }
}
watch(
  () => [draft.value.action, [...values.value.allowedOperatorIds]],
  () => {
    if (draft.value.action === 'WAIT_APPROVAL' && !draft.value.blocked) {
      void validatePeople()
      void loadPeople()
    }
  },
  { immediate: true },
)
function confirm(content: string) {
  return new Promise<boolean>((resolve) =>
    dialog.warning({
      title: t('eventTask.switchTitle'),
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
async function changeAction(action: EventAction) {
  if (props.disabled || draft.value.blocked || action === draft.value.action) return
  const removed = removedEventFields(draft.value, action)
  const clearTimeoutValue =
    ['WAIT_EVENT', 'WAIT_APPROVAL'].includes(action) && timeout.value !== null
  const content = [
    removed.length ? t('eventTask.switchHint', { fields: removed.join(', ') }) : '',
    clearTimeoutValue ? t('eventTask.timeoutSwitch') : '',
  ]
    .filter(Boolean)
    .join('\n')
  if (content && !(await confirm(content))) return
  if (controller.signal.aborted || props.disabled) return
  if (clearTimeoutValue) {
    savedTimeout = timeout.value
    timeout.value = null
  }
  if (!['WAIT_EVENT', 'WAIT_APPROVAL'].includes(action) && savedTimeout !== null) {
    timeout.value = savedTimeout
    savedTimeout = null
  }
  draft.value.action = action
}
async function clearExecutionTimeout() {
  if (await confirm(t('eventTask.timeoutSwitch'))) {
    savedTimeout = timeout.value
    timeout.value = null
  }
}
onBeforeUnmount(() => {
  controller.abort()
  clearTimeout(timer)
})
defineExpose({ validatePeople })
</script>
<template>
  <section class="event-fields">
    <h3>{{ t('eventTask.title') }}</h3>
    <div v-if="draft.blocked" role="alert">
      <p>{{ t('eventTask.unsupported') }}</p>
      <pre class="event-original">{{ draft.original }}</pre>
    </div>
    <template v-else>
      <FormItem id="task-event.action" :label="t('eventTask.action')" required
        ><NSelect
          filterable
          :value="draft.action"
          :disabled="disabled"
          :options="eventActions.map((value) => ({ value, label: t('eventTask.' + value) }))"
          :input-props="{ id: 'task-event.action', 'aria-label': t('eventTask.action') }"
          @update:value="changeAction"
          @update:show="emit('select', $event)"
      /></FormItem>
      <p v-if="draft.action !== 'PROBE_FILE'" class="task-note event-action-hint">
        {{ t('eventTask.actionHint') }}
      </p>
      <ProbeFileFields
        v-if="draft.action === 'PROBE_FILE'"
        v-model:value="values"
        :errors="errors"
        :submitted="submitted"
        :disabled="disabled"
        @select="emit('select', $event)"
      />
      <template v-if="draft.action === 'WAIT_APPROVAL'">
        <p class="task-note">{{ t('eventTask.approvalHint') }}</p>
        <FormItem
          id="task-event.allowedOperatorIds"
          :label="t('eventTask.allowedOperatorIds')"
          required
          :invalid="!!error('allowedOperatorIds') || (submitted && invalidPeople)"
          :message="
            error('allowedOperatorIds') ||
            (submitted && invalidPeople ? t('eventTask.peopleInvalid') : undefined)
          "
        >
          <NSelect
            v-model:value="values.allowedOperatorIds"
            multiple
            filterable
            remote
            :disabled="disabled"
            :options="options"
            :loading="loading || resolving"
            :placeholder="t('eventTask.search')"
            :input-props="{
              id: 'task-event.allowedOperatorIds',
              'aria-label': t('eventTask.allowedOperatorIds'),
            }"
            @search="searchPeople"
            @update:show="emit('select', $event)"
          />
        </FormItem>
        <div v-for="person in unavailablePeople" :key="person.loginId" class="task-inline">
          <span
            >{{ person.displayName || person.username || person.loginId }} ·
            {{ t('eventTask.' + person.status) }}</span
          ><NButton text type="error" :disabled="disabled" @click="removePerson(person.loginId)">{{
            t('eventTask.removePerson')
          }}</NButton>
        </div>
        <RequestError :error="peopleError" />
        <div class="task-inline">
          <NButton text :disabled="disabled || loading || resolving" @click="refreshPeople">{{
            t('eventTask.refreshPeople')
          }}</NButton
          ><NButton v-if="more" text :disabled="disabled || loading" @click="morePeople">{{
            t('eventTask.more')
          }}</NButton>
        </div>
      </template>
      <template v-else>
        <FormItem
          id="task-event.eventCode"
          :label="t('eventTask.eventCode')"
          required
          :invalid="!!error('eventCode')"
          :message="error('eventCode')"
          ><NInput
            v-model:value="values.eventCode"
            :disabled="disabled"
            :maxlength="128"
            :input-props="{ id: 'task-event.eventCode', 'aria-invalid': !!error('eventCode') }"
        /></FormItem>
        <p class="task-note">{{ t('eventTask.codeHint') }}</p>
        <div class="task-advanced-grid">
          <FormItem
            v-for="key in ['businessKey', 'correlationKey'] as const"
            :id="'task-event.' + key"
            :key="key"
            :label="t('eventTask.' + key)"
            :invalid="!!error(key)"
            :message="error(key)"
            ><NInput
              v-model:value="values[key]"
              :disabled="disabled"
              :maxlength="256"
              :input-props="{
                id: 'task-event.' + key,
                'aria-describedby': 'task-event.' + key + '-hint',
              }"
            />
            <p :id="'task-event.' + key + '-hint'" class="task-note event-key-hint">
              {{ t('eventTask.' + key + 'Hint') }}
            </p>
          </FormItem>
        </div>
        <p class="task-note">
          {{ t('eventTask.' + (waiting ? 'keysWaitHint' : 'keysPublishHint')) }}
        </p>
      </template>
      <template v-if="waiting">
        <div class="task-advanced-grid">
          <FormItem
            id="task-event.timeoutSeconds"
            :label="t('eventTask.timeoutSeconds')"
            :invalid="!!error('timeoutSeconds')"
            :message="error('timeoutSeconds')"
            ><NInputNumber
              v-model:value="values.timeoutSeconds"
              :disabled="disabled"
              :precision="0"
              :placeholder="t('eventTask.unlimited')"
              :input-props="{ id: 'task-event.timeoutSeconds' }" /></FormItem
          ><FormItem
            v-if="draft.action === 'WAIT_EVENT'"
            id="task-event.lookbackSeconds"
            :label="t('eventTask.lookbackSeconds')"
            :invalid="!!error('lookbackSeconds')"
            :message="error('lookbackSeconds')"
            ><NInputNumber
              v-model:value="values.lookbackSeconds"
              :disabled="disabled"
              :precision="0"
              :input-props="{ id: 'task-event.lookbackSeconds' }"
          /></FormItem>
        </div>
        <p v-if="draft.action === 'WAIT_EVENT'" class="task-note">
          {{ t('eventTask.lookbackHint') }}
        </p>
        <p class="task-note">{{ t('eventTask.waitingHint') }}</p>
        <NButton v-if="timeout !== null" :disabled="disabled" @click="clearExecutionTimeout">{{
          t('eventTask.timeoutSwitch')
        }}</NButton>
      </template>
      <template v-else-if="draft.action === 'PUBLISH'">
        <FormItem
          id="task-event.summary"
          :label="t('eventTask.summary')"
          :invalid="!!error('summary')"
          :message="error('summary')"
          ><NInput
            v-model:value="values.summary"
            :disabled="disabled"
            :maxlength="500"
            :input-props="{ id: 'task-event.summary' }"
        /></FormItem>
        <FormItem
          id="task-event.payload"
          :label="t('eventTask.payload')"
          :invalid="!!error('payload')"
          :message="error('payload')"
          ><JsonFormEditor
            v-model="values.payload"
            :title="t('eventTask.payload')"
            context-key="EVENT_PAYLOAD"
            :disabled="disabled"
            :invalid="!!error('payload')"
            @expanded="emit('expanded', $event)"
            @select="emit('select', $event)"
        /></FormItem>
        <p class="task-note">{{ t('eventTask.payloadHint') }}</p>
        <div class="task-inline">
          <label for="event-suppress">{{ t('eventTask.suppressSameInbound') }}</label
          ><NSwitch
            id="event-suppress"
            v-model:value="values.suppressSameInbound"
            :disabled="disabled"
            :aria-label="t('eventTask.suppressSameInbound')"
          />
        </div>
        <p class="task-note">{{ t('eventTask.suppressHint') }}</p>
        <NCollapse
          ><NCollapseItem name="eventAdvanced" :title="t('tasks.advanced')"
            ><FormItem
              v-for="key in ['idempotencyKey', 'occurredAt'] as const"
              :id="'task-event.' + key"
              :key="key"
              :label="t('eventTask.' + key)"
              :invalid="!!error(key)"
              :message="error(key)"
              ><NInput
                v-model:value="values[key]"
                :disabled="disabled"
                :input-props="{ id: 'task-event.' + key }"
              />
              <p class="task-note">
                {{
                  t(key === 'idempotencyKey' ? 'eventTask.idempotencyHint' : 'eventTask.timeHint')
                }}
              </p></FormItem
            ></NCollapseItem
          ></NCollapse
        >
        <p class="task-note">{{ t('eventTask.publishHint') }}</p>
      </template>
    </template>
  </section>
</template>
<style scoped>
.event-fields {
  display: grid;
  gap: 12px;
  min-width: 0;
}
.event-fields h3 {
  font-size: 14px;
  margin: 0;
}
.event-fields .task-note {
  margin: 0;
}
.event-fields .event-key-hint {
  margin-top: 5px;
}
.event-fields .task-advanced-grid {
  align-items: start;
}
.event-original {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 260px;
  overflow: auto;
}
</style>
