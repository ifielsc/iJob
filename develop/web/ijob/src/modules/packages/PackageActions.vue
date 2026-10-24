<!-- Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT -->
<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'
import { NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { Selection } from './model'
import './packages.css'
const props = defineProps<{ kind: 'TASK' | 'JOB'; selected: Selection[] }>()
const emit = defineEmits<{ imported: [] }>()
const { t } = useI18n(),
  mode = ref<'export' | 'import' | 'history'>()
const ExportDialog = defineAsyncComponent(() => import('./PackageExport.vue'))
const ImportDialog = defineAsyncComponent(() => import('./PackageImport.vue'))
</script>
<template>
  <div class="package-actions">
    <NButton size="small" :disabled="!props.selected.length" @click="mode = 'export'">{{
      t('packages.exportSelected')
    }}</NButton>
    <NButton size="small" @click="mode = 'import'">{{
      t(kind === 'TASK' ? 'packages.importTask' : 'packages.import')
    }}</NButton>
    <NButton size="small" @click="mode = 'history'">{{ t('packages.history') }}</NButton>
  </div>
  <ExportDialog
    v-if="mode === 'export'"
    :kind="kind"
    :selected="selected"
    @close="mode = undefined"
  />
  <ImportDialog
    v-if="mode === 'import' || mode === 'history'"
    :history="mode === 'history'"
    :kind="kind"
    @close="mode = undefined"
    @imported="emit('imported')"
  />
</template>
