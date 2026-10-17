<script setup lang="ts">
import EmptyState from './EmptyState.vue'
import { NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import './editable-table.css'
withDefaults(
  defineProps<{
    title: string
    columns: { key: string; label: string; required?: boolean }[]
    count: number
    addLabel: string
    disabled?: boolean
    hasDraft?: boolean
    bounded?: boolean
  }>(),
  { bounded: true },
)
defineEmits<{ add: [] }>()
const { t } = useI18n()
</script>
<template>
  <section
    class="editable-table-section"
    :class="{ 'editable-table-bounded': bounded !== false && count > 6 }"
    :aria-label="title"
  >
    <div class="editable-table-toolbar">
      <h3>
        {{ title }} <span class="editable-table-count">{{ count }}</span>
      </h3>
      <div class="editable-table-tools">
        <slot name="tools" /><NButton size="small" :disabled="disabled" @click="$emit('add')">{{
          addLabel
        }}</NButton>
      </div>
    </div>
    <div class="editable-table-scroll" tabindex="0" :aria-label="title" role="region">
      <table class="editable-table" :aria-label="title">
        <colgroup>
          <col v-for="column in columns" :key="column.key" :class="'editable-col-' + column.key" />
        </colgroup>
        <thead>
          <tr>
            <th v-for="column in columns" :key="column.key" scope="col">
              <span v-if="column.required" class="editable-required" aria-hidden="true">*</span
              >{{ column.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <slot />
          <tr v-if="!count && !hasDraft">
            <td :colspan="columns.length" class="list-empty-cell">
              <EmptyState compact :message="t('common.empty')" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <slot name="feedback" />
  </section>
</template>
