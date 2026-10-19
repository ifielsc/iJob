<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { NButton, NModal } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { describeError } from '../../shared/api/errors'
import { list, resource, outcomeUnknown, type Resource } from './model'
const props = defineProps<{ rows: Resource[] }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const identity = runtime.session.revision
const allowed = computed(
  () =>
    identity === runtime.session.revision &&
    !runtime.accessIssue.value &&
    can(user.value, 'CONFIGURATION_WRITE') &&
    can(user.value, 'DEFINITION_READ'),
)
const results = ref(props.rows.map((row) => ({ row, state: 'pending', message: '' })))
const busy = ref(false),
  started = ref(false),
  reviewError = ref('')
const uncertain = computed(() => results.value.some((r) => r.state === 'unknown'))
const controller = new AbortController()
async function remove() {
  if (busy.value || started.value || !allowed.value) return
  busy.value = true
  started.value = true
  try {
    for (const item of results.value) {
      if (!allowed.value || controller.signal.aborted) break
      try {
        await runtime.request(
          '/api/concurrency/resources/' + item.row.id + '?expectedVersion=' + item.row.version,
          { method: 'DELETE', signal: controller.signal },
        )
        if (!allowed.value || controller.signal.aborted) return
        item.state = 'deleted'
        item.message = t('concurrency.deleted')
      } catch (e) {
        if (!allowed.value || controller.signal.aborted) return
        item.state = outcomeUnknown(e) ? 'unknown' : 'failed'
        item.message = outcomeUnknown(e) ? t('concurrency.deleteUnknown') : describeError(e, t)
        // Never replay an ambiguous write or continue a batch through a broken connection.
        if (item.state === 'unknown') break
      }
    }
    if (allowed.value && !controller.signal.aborted) {
      emit('changed')
      if (results.value.every((r) => r.state === 'deleted')) emit('close')
    }
  } finally {
    busy.value = false
  }
}
async function review() {
  if (busy.value || !allowed.value) return
  busy.value = true
  reviewError.value = ''
  try {
    const current = list(
      await runtime.request(
        '/api/concurrency/resources/by-ids?' +
          new URLSearchParams({
            ids: results.value
              .filter((r) => r.state === 'unknown')
              .map((r) => r.row.id)
              .join(','),
          }),
        { signal: controller.signal },
      ),
      resource,
    )
    if (!allowed.value || controller.signal.aborted) return
    for (const item of results.value.filter((r) => r.state === 'unknown')) {
      item.message = t(
        current.some((r) => r.id === item.row.id)
          ? 'concurrency.deleteStillPresent'
          : 'concurrency.missing',
      )
      item.state = current.some((r) => r.id === item.row.id) ? 'failed' : 'deleted'
    }
    emit('changed')
  } catch (e) {
    if (!controller.signal.aborted) reviewError.value = describeError(e, t)
  } finally {
    busy.value = false
  }
}
function close() {
  if (!busy.value) emit('close')
}
watch(allowed, (value) => {
  if (!value) {
    controller.abort()
    emit('close')
  }
})
onBeforeRouteLeave(() => !busy.value)
const unload = (event: BeforeUnloadEvent) => {
  if (busy.value) event.preventDefault()
}
window.addEventListener('beforeunload', unload)
onBeforeUnmount(() => {
  controller.abort()
  window.removeEventListener('beforeunload', unload)
})
</script>
<template>
  <NModal
    :show="allowed"
    preset="dialog"
    type="warning"
    :title="t(rows.length > 1 ? 'config.batchDelete' : 'concurrency.remove')"
    style="width: min(446px, calc(100vw - 32px))"
    :mask-closable="!busy"
    :close-on-esc="!busy"
    :closable="!busy"
    @close="close"
    @update:show="close"
  >
    <p>{{ t('concurrency.deleteResourcesHint', { count: rows.length }) }}</p>
    <ul class="concurrency-delete-results" aria-live="polite">
      <li v-for="item in results" :key="item.row.id">
        <strong>{{ item.row.name }}</strong> · {{ item.row.code }}
        <p
          v-if="started"
          :class="{ 'field-error': item.state === 'failed' || item.state === 'unknown' }"
        >
          {{ item.message || t('concurrency.deleteNotAttempted') }}
        </p>
      </li>
    </ul>
    <p v-if="reviewError" role="alert">{{ reviewError }}</p>
    <template #action
      ><div class="concurrency-footer">
        <NButton :disabled="busy" @click="close">{{
          t(started ? 'common.close' : 'common.cancel')
        }}</NButton>
        <NButton v-if="uncertain" :loading="busy" @click="review">{{
          t('concurrency.review')
        }}</NButton>
        <NButton v-if="!started" type="warning" :loading="busy" @click="remove">{{
          t('concurrency.remove')
        }}</NButton>
      </div></template
    >
  </NModal>
</template>
<style scoped>
.concurrency-delete-results {
  padding-left: 20px;
  max-height: 320px;
  overflow: auto;
  overflow-wrap: anywhere;
}
.concurrency-delete-results li {
  margin: 10px 0;
}
.concurrency-delete-results p {
  margin: 4px 0;
}
</style>
