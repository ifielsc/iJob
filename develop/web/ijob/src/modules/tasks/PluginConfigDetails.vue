<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { isLosslessNumber } from 'lossless-json'
import PluginFacts from './PluginFacts.vue'
import { literalChildren } from '../../shared/ui/detail-labels'
import { detailPresentation } from './detail-presentation'
const props = defineProps<{ plugin: string; scope: string; value: unknown }>()
const { t } = useI18n()
const presentation = computed(() => detailPresentation(props.plugin, props.value))
const sections = computed(() =>
  presentation.value.sections.map((section) => {
    const entries = Object.entries(section.value)
    const entry = entries.length === 1 ? entries[0] : undefined
    if (
      section.title !== 'details.otherConfiguration' &&
      entry &&
      ['source', 'target', 'rules', 'notify', 'userConfig', 'checks', 'handlers'].includes(
        entry[0],
      ) &&
      entry[1] !== null &&
      typeof entry[1] === 'object' &&
      !isLosslessNumber(entry[1])
    ) {
      return {
        title: section.title,
        value: entry[1],
        path: entry[0],
        literal: literalChildren(props.scope, '', entry[0]),
      }
    }
    return { ...section, path: '', literal: false }
  }),
)
</script>
<template>
  <div v-if="sections.length" class="plugin-detail-sections">
    <section v-for="section in sections" :key="section.title" :aria-label="t(section.title)">
      <h4>{{ t(section.title) }}</h4>
      <PluginFacts
        :value="section.value"
        :scope="scope"
        :path="section.path"
        :literal="section.literal"
      />
    </section>
  </div>
  <PluginFacts v-else-if="!presentation.object" :value="value" :scope="scope" />
  <EmptyState v-else compact :message="t('common.empty')" />
</template>
<style scoped>
.plugin-detail-sections {
  display: grid;
  gap: 20px;
}
h4 {
  margin: 0 0 12px;
  padding-left: 10px;
  border-left: 2px solid var(--primary);
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}
</style>
