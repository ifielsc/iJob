<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { ref } from 'vue'
const props = defineProps<{
  id: string
  label: string
  value: string
  tabs: { value: string; label: string }[]
  panelId: string
  disabled?: boolean
}>()
const emit = defineEmits<{ change: [value: string] }>()
const bar = ref<HTMLElement>()
function navigate(event: KeyboardEvent, index: number) {
  const last = props.tabs.length - 1
  const next =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? last
        : event.key === 'ArrowRight'
          ? (index + 1) % (last + 1)
          : event.key === 'ArrowLeft'
            ? (index + last) % (last + 1)
            : -1
  if (next < 0 || props.disabled) return
  event.preventDefault()
  // Manual activation: moving focus never discards an editor's draft.
  bar.value?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus()
}
</script>
<template>
  <div ref="bar" class="line-tabs" role="tablist" :aria-label="label">
    <button
      v-for="(item, index) in tabs"
      :id="`${id}-${item.value}`"
      :key="item.value"
      type="button"
      role="tab"
      :aria-selected="value === item.value"
      :aria-controls="panelId"
      :tabindex="value === item.value ? 0 : -1"
      :disabled="disabled"
      @keydown="navigate($event, index)"
      @click="emit('change', item.value)"
    >
      {{ item.label }}
    </button>
  </div>
</template>
<style scoped>
.line-tabs {
  display: flex;
  gap: 24px;
  overflow-x: auto;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
  min-width: 0;
}
.line-tabs button {
  flex: none;
  padding: 10px 2px;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--muted);
  font: inherit;
  cursor: pointer;
  white-space: nowrap;
}
.line-tabs button[aria-selected='true'] {
  color: var(--primary);
  border-bottom-color: var(--primary);
}
.line-tabs button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -3px;
}
.line-tabs button:disabled {
  cursor: default;
  opacity: 0.6;
}
</style>
