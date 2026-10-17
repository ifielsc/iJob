<script setup lang="ts">
import { NButton, useMessage } from 'naive-ui'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError, describeError } from '../api/errors'
const props = defineProps<{ error: unknown }>()
const { t } = useI18n()
const trace = computed(() =>
  props.error instanceof ApiError ? props.error.envelope?.traceId : undefined,
)
const message = useMessage()
async function copy() {
  try {
    // Copy only the stable error category and trace, never request bodies or server payloads.
    const error = props.error
    await navigator.clipboard.writeText(
      error instanceof ApiError
        ? [error.kind, 'HTTP ' + error.status, trace.value ? 'Trace: ' + trace.value : '']
            .filter(Boolean)
            .join('\n')
        : t('error.generic'),
    )
    message.success(t('unified.copied'))
  } catch {
    message.error(t('unified.copyFailed'))
  }
}
</script>
<template>
  <div v-if="error" class="request-error" role="alert">
    <p>{{ describeError(error, t) }}</p>
    <NButton text size="tiny" @click="copy">{{ t('experience.copyError') }}</NButton>
    <small v-if="trace">{{ t('error.trace', { id: trace }) }}</small>
  </div>
</template>
