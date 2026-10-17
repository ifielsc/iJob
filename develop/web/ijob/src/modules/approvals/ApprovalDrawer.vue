<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { NButton, NDrawer, NDrawerContent, NInput, useDialog, useMessage } from 'naive-ui'
import { X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { ApiError, describeError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import { approval, outcomeUnknown, time, type Approval, type Decision } from './model'
import { decisionMemory, type PendingDecision } from './decision-memory'
import './approvals.css'
const props = defineProps<{ id: string }>(),
  emit = defineEmits<{ close: []; resolved: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage(),
  router = useRouter(),
  route = useRoute()
const allowed = computed(() => can(user.value, 'APPROVAL_DECIDE') && !runtime.accessIssue.value)
const row = shallowRef<Approval>(),
  error = shallowRef<unknown>(),
  busy = ref(false),
  loading = ref(false),
  comment = ref(''),
  invalid = ref(false),
  closing = ref(false)
const pending = ref<PendingDecision | undefined>(decisionMemory(runtime).get(props.id)),
  checkedWaiting = ref(false)
if (pending.value) comment.value = pending.value.comment
const conflict = computed(() => error.value instanceof ApiError && error.value.kind === 'conflict')
const noAccess = computed(() => error.value instanceof ApiError && error.value.status === 403)
const notFound = computed(() => error.value instanceof ApiError && error.value.status === 404)
const actionable = computed(
  () =>
    allowed.value &&
    row.value?.waitType === 'APPROVAL' &&
    row.value.status === 'WAITING' &&
    !busy.value &&
    !loading.value &&
    !error.value,
)
let readController: AbortController | undefined, writeController: AbortController | undefined
async function load() {
  if (!allowed.value || busy.value) return
  readController?.abort()
  const controller = new AbortController()
  readController = controller
  loading.value = true
  error.value = undefined
  checkedWaiting.value = false
  try {
    const result = approval(
      await runtime.request('/api/event-waits/' + props.id, {
        signal: controller.signal,
        localForbidden: true,
      }),
    )
    if (controller.signal.aborted) return
    if (result.id !== props.id) throw new ApiError('protocol')
    row.value = result
    if (result.status !== 'WAITING') {
      decisionMemory(runtime).delete(props.id)
      pending.value = undefined
      emit('resolved')
    } else if (pending.value) checkedWaiting.value = true
  } catch (e) {
    if (!controller.signal.aborted) {
      error.value = e
      if (e instanceof ApiError && [403, 404].includes(e.status)) row.value = undefined
    }
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}
watch(
  [() => props.id, () => runtime.session.revision, allowed],
  () => {
    readController?.abort()
    writeController?.abort()
    row.value = undefined
    error.value = undefined
    busy.value = false
    comment.value = ''
    pending.value = decisionMemory(runtime).get(props.id)
    if (pending.value) comment.value = pending.value.comment
    dialog.destroyAll()
    if (allowed.value) void load()
  },
  { immediate: true },
)
watch(
  () => runtime.preferences.locale,
  () => {
    if (!busy.value) void load()
  },
)
async function submit(action: Decision, retry = false) {
  if (!actionable.value || (pending.value && !retry)) return
  const input = retry && pending.value ? pending.value.comment : comment.value
  if (input.length > 500) {
    invalid.value = true
    return
  }
  const decision = { action, comment: input },
    revision = runtime.session.revision
  readController?.abort()
  writeController = new AbortController()
  const signal = writeController.signal
  busy.value = true
  error.value = undefined
  try {
    const result = approval(
      await runtime.request('/api/event-waits/' + props.id + '/' + action, {
        method: 'POST',
        body: { comment: input || null },
        signal,
        localForbidden: true,
      }),
    )
    if (signal.aborted || runtime.session.revision !== revision) return
    if (result.id !== props.id || result.waitType !== 'APPROVAL' || result.status === 'WAITING')
      throw new ApiError('protocol')
    row.value = result
    pending.value = undefined
    decisionMemory(runtime).delete(props.id)
    comment.value = ''
    message.success(t('approvals.actualResult'))
    emit('resolved')
  } catch (e) {
    if (signal.aborted || runtime.session.revision !== revision) return
    error.value = e
    if (outcomeUnknown(e)) {
      pending.value = decision
      decisionMemory(runtime).set(props.id, decision)
      checkedWaiting.value = false
    } else message.error(describeError(e, t))
    if (e instanceof ApiError && [403, 404].includes(e.status)) row.value = undefined
  } finally {
    if (!signal.aborted && runtime.session.revision === revision) busy.value = false
  }
}
function confirm(action: Decision, retry = false) {
  if (
    !actionable.value ||
    (pending.value && (!retry || !checkedWaiting.value || pending.value.action !== action))
  )
    return
  if (comment.value.length > 500) {
    invalid.value = true
    return
  }
  dialog.warning({
    title: t('approvals.confirm', { action: t('approvals.' + action) }),
    content: t('approvals.confirmBody', {
      name: row.value?.taskName || t('approvals.missingName'),
      id: props.id,
      hint: t('approvals.' + action + 'Hint') + (retry ? ' ' + t('approvals.retryHint') : ''),
    }),
    positiveText: t('approvals.confirm', { action: t('approvals.' + action) }),
    negativeText: t('common.cancel'),
    onPositiveClick: () => submit(action, retry),
  })
}
async function allowClose() {
  if (!allowed.value) return true
  if (busy.value) return false
  if (!comment.value && !pending.value) return true
  return new Promise<boolean>((resolve) =>
    dialog.warning({
      title: t('common.discard'),
      content: t(pending.value ? 'approvals.leaveUnknown' : 'common.discardHint'),
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
let approvedClose = false
async function close() {
  if (closing.value) return
  closing.value = true
  try {
    if (await allowClose()) {
      approvedClose = true
      emit('close')
    }
  } finally {
    closing.value = false
  }
}
onBeforeRouteLeave(allowClose)
onBeforeRouteUpdate((to, from) => {
  if (to.query.waitId === from.query.waitId && to.params.id === from.params.id) return true
  if (approvedClose) {
    approvedClose = false
    return true
  }
  return allowClose()
})
const unload = (event: BeforeUnloadEvent) => {
  if (allowed.value && (comment.value || busy.value || pending.value)) event.preventDefault()
}
window.addEventListener('beforeunload', unload)
onBeforeUnmount(() => {
  readController?.abort()
  writeController?.abort()
  window.removeEventListener('beforeunload', unload)
})
function viewRun() {
  if (row.value)
    void router.push({
      path: '/runs/instances/' + row.value.instanceId,
      query: { back: route.fullPath },
    })
}
</script>
<template>
  <NDrawer
    :show="true"
    class="approval-drawer"
    width="min(520px, 100vw)"
    :mask-closable="!comment && !pending && !busy"
    :close-on-esc="!busy"
    :auto-focus="true"
    :trap-focus="true"
    @update:show="close"
  >
    <NDrawerContent :title="t('approvals.detail')"
      ><template #header
        ><div class="approval-drawer-title">
          <h2>{{ t('approvals.detail') }}</h2>
          <NButton quaternary circle :disabled="busy" :aria-label="t('common.close')" @click="close"
            ><template #icon><X :size="18" /></template
          ></NButton></div
      ></template>
      <div class="approval-detail">
        <p v-if="noAccess" role="alert">{{ t('approvals.noAccess') }}</p>
        <p v-else-if="notFound" role="alert">{{ t('approvals.notFound') }}</p>
        <RequestError v-else-if="!pending" :error="error" />
        <p v-if="conflict" role="alert">{{ t('approvals.conflict') }}</p>
        <p v-if="loading && !row" role="status">{{ t('common.loading') }}</p>
        <NButton v-if="!row && !busy" :loading="loading" @click="load">{{
          t('approvals.refresh')
        }}</NButton>
        <template v-if="row && allowed">
          <div class="approval-identity">
            <strong>{{ row.taskName || t('approvals.missingName') }}</strong
            ><span class="approval-status" :data-status="row.status">{{
              t('approvals.state_' + row.status)
            }}</span
            ><NButton text type="primary" :loading="loading" :disabled="busy" @click="load">{{
              t('approvals.refresh')
            }}</NButton>
          </div>
          <p class="approval-note">{{ t('approvals.id') }} {{ row.id }}</p>
          <dl class="approval-meta">
            <dt>{{ t('approvals.run') }}</dt>
            <dd>{{ row.rootTargetName || t('approvals.missingName') }}</dd>
            <dt>{{ t('approvals.instanceId') }}</dt>
            <dd>
              {{ row.instanceId }}
              <NButton text type="primary" :disabled="busy" @click="viewRun">{{
                t('approvals.viewRun')
              }}</NButton>
            </dd>
            <dt>{{ t('approvals.rootInstanceId') }}</dt>
            <dd>{{ row.rootInstanceId }}</dd>
            <dt>{{ t('approvals.started') }}</dt>
            <dd>
              {{
                time(
                  row.createTime,
                  runtime.preferences.locale,
                  runtime.preferences.displayTimezone,
                )
              }}
            </dd>
            <dt>{{ t('approvals.deadline') }}</dt>
            <dd>
              {{
                row.timeoutAt
                  ? time(
                      row.timeoutAt,
                      runtime.preferences.locale,
                      runtime.preferences.displayTimezone,
                    )
                  : t('approvals.unlimited')
              }}
            </dd>
            <dt>{{ t('approvals.definition') }}</dt>
            <dd>{{ row.taskDefinitionId || '—' }}</dd>
            <dt>{{ t('approvals.attempt') }}</dt>
            <dd>{{ row.attemptNo }}</dd>
          </dl>
          <p class="approval-note">{{ t('approvals.clockHint') }}</p>
          <template v-if="row.status !== 'WAITING'"
            ><dl class="approval-meta approval-decision">
              <dt>{{ t('approvals.decisionBy') }}</dt>
              <dd>
                {{
                  row.decisionBy ||
                  t(
                    ['TIMED_OUT', 'STOPPED'].includes(row.status)
                      ? 'approvals.system'
                      : 'approvals.unknownActor',
                  )
                }}
              </dd>
              <dt>{{ t('approvals.resolvedAt') }}</dt>
              <dd>
                {{
                  time(
                    row.resolvedAt,
                    runtime.preferences.locale,
                    runtime.preferences.displayTimezone,
                  )
                }}
              </dd>
              <dt>{{ t('approvals.decisionComment') }}</dt>
              <dd class="approval-comment">{{ row.decisionComment || '—' }}</dd>
            </dl></template
          >
          <p v-else-if="row.waitType !== 'APPROVAL'">{{ t('approvals.notApproval') }}</p>
          <section v-else class="approval-decision">
            <h3>{{ t('approvals.process') }}</h3>
            <div v-if="pending" class="approval-unknown" role="alert">
              <strong>{{ t('approvals.unknown') }}</strong>
              <p>{{ t('approvals.unknownHint') }}</p>
              <NButton :loading="loading" :disabled="busy" @click="load">{{
                t('approvals.check')
              }}</NButton>
              <p v-if="checkedWaiting">{{ t('approvals.stillWaiting') }}</p>
              <NButton
                v-if="checkedWaiting"
                text
                :disabled="!actionable"
                @click="confirm(pending.action, true)"
                >{{ t('approvals.retryDecision') }}</NButton
              ><RequestError :error="error" />
            </div>
            <label for="approval-comment">{{ t('approvals.comment') }}</label
            ><NInput
              v-model:value="comment"
              type="textarea"
              :disabled="busy || !!pending"
              :rows="4"
              :input-props="{
                id: 'approval-comment',
                'aria-invalid': invalid && comment.length > 500,
              }"
              :placeholder="t('approvals.commentPlaceholder')"
            />
            <p class="approval-counter">{{ comment.length }} / 500</p>
            <p v-if="invalid && comment.length > 500" class="field-error" role="alert">
              {{ t('approvals.commentLength') }}
            </p>
            <p
              v-for="action in ['approve', 'reject', 'withdraw']"
              :key="action"
              class="approval-note"
            >
              {{ t('approvals.' + action) }}：{{ t('approvals.' + action + 'Hint') }}
            </p>
          </section>
        </template>
      </div>
      <template #footer
        ><div class="approval-footer">
          <NButton
            v-if="row?.waitType === 'APPROVAL' && row.status === 'WAITING'"
            text
            type="error"
            :disabled="!actionable || !!pending"
            @click="confirm('withdraw')"
            >{{ t('approvals.withdraw') }}</NButton
          ><span class="approval-spacer" /><NButton :disabled="busy" @click="close">{{
            t('common.close')
          }}</NButton
          ><template v-if="row?.waitType === 'APPROVAL' && row.status === 'WAITING'"
            ><NButton
              type="error"
              ghost
              :disabled="!actionable || !!pending"
              @click="confirm('reject')"
              >{{ t('approvals.reject') }}</NButton
            ><NButton
              type="primary"
              :loading="busy"
              :disabled="!actionable || !!pending"
              @click="confirm('approve')"
              >{{ t('approvals.approve') }}</NButton
            ></template
          >
        </div></template
      >
    </NDrawerContent>
  </NDrawer>
</template>
