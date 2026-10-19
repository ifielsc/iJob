<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PluginFacts from './PluginFacts.vue'
import { detailLabel } from '../../shared/ui/detail-labels'
import { detailPresentation } from './detail-presentation'
const props = defineProps<{ plugin: string; scope: string; value: unknown }>()
const { t, te } = useI18n()
const presentation = computed(() => detailPresentation(props.plugin, props.value))
</script>
<template>
  <div class="plugin-technical">
    <PluginFacts
      v-if="Object.keys(presentation.metadata).length"
      :value="presentation.metadata"
      :scope="scope"
    />
    <details v-if="Object.keys(presentation.extensions).length" class="plugin-extensions">
      <summary>{{ t('details.extensionFields') }}</summary>
      <p>{{ t('details.extensionHint') }}</p>
      <PluginFacts :value="presentation.extensions" :scope="scope" literal />
    </details>
    <details v-if="presentation.missing.length" class="plugin-missing">
      <summary>{{ t('details.unsavedFields', { count: presentation.missing.length }) }}</summary>
      <p>{{ t('details.unsavedHint') }}</p>
      <ul>
        <li v-for="key in presentation.missing" :key="key">
          {{ detailLabel(scope, '', key, t, te) }}
        </li>
      </ul>
    </details>
  </div>
</template>
<style scoped>
.plugin-technical {
  display: grid;
  gap: 14px;
  margin-top: 14px;
  font-size: 12px;
}
summary {
  cursor: pointer;
}
p,
ul {
  color: var(--muted);
}
ul {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 20px;
  padding-left: 18px;
}
</style>
