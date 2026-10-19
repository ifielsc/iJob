<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { terminal } from '../runtime/contracts'
const props = defineProps<{ progress: number | null; targetType: string; status: string }>()
const { t } = useI18n()
const ended = computed(
  () => terminal.has(props.status) && !(props.targetType === 'JOB' && props.status === 'SUCCESS'),
)
const known = computed(
  () =>
    !ended.value && props.progress != null && (props.targetType === 'JOB' || props.progress > 0),
)
</script>
<template>
  <div class="experience-progress">
    <small>{{
      t(
        ended
          ? 'experience.executionEnded'
          : targetType === 'JOB'
            ? 'experience.jobProgress'
            : 'experience.taskProgress',
      )
    }}</small>
    <template v-if="known"
      ><progress
        :value="progress ?? 0"
        max="100"
        :aria-label="t(targetType === 'JOB' ? 'experience.jobProgress' : 'experience.taskProgress')"
      />
      <span>{{ progress }}%</span></template
    >
    <span v-else class="experience-note">
      · {{ t(ended ? 'tasks.status_' + status : 'experience.noProgress') }}</span
    >
  </div>
</template>
