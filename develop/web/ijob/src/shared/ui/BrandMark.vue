<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import icon from '../../assets/brand/ijob-helix.png'

defineProps<{ compact?: boolean; subtitle?: string }>()
</script>

<template>
  <span class="brand-mark" :class="{ 'brand-mark--compact': compact }">
    <img class="brand-icon" :src="icon" width="40" height="44" alt="" aria-hidden="true" />
    <span v-if="!compact" class="brand-copy">
      <span class="brand-wordmark">iJob</span>
      <span v-if="subtitle" class="brand-caption">{{ subtitle }}</span>
    </span>
  </span>
</template>

<style scoped>
.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--text);
}
.brand-icon {
  display: block;
  width: 40px;
  height: 44px;
  object-fit: contain;
  flex-shrink: 0;
}
.brand-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.brand-wordmark {
  color: var(--primary);
  font-size: 30px;
  font-weight: 750;
  line-height: 1.1;
  letter-spacing: -1.3px;
}
[data-theme='dark'] .brand-wordmark {
  color: var(--text);
}
.brand-caption {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
  margin-top: 4px;
  white-space: nowrap;
}
.brand-mark--compact {
  justify-content: center;
}
</style>
