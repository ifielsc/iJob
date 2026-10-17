<script setup lang="ts">
import { NButton, NInput } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import EditableTable from '../../shared/ui/EditableTable.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import ConfiguredField from './ConfiguredField.vue'
import './configuration.css'
import { hidden } from './contracts'
import type { HeaderDraft } from './header-model'
const props = defineProps<{
  title?: string
  errors: Record<string, string>
  submitted?: boolean
  disabled?: boolean
  prefix?: string
}>()
const rows = defineModel<HeaderDraft[]>('rows', { required: true })
const { t } = useI18n()
const error = (key: string) =>
  props.submitted && props.errors[key] ? t('apiCall.' + props.errors[key]) : undefined
</script>
<template>
  <EditableTable
    :title="title ?? t('apiCall.headers')"
    :count="rows.length"
    :add-label="t('apiCall.addHeader')"
    :disabled="disabled"
    :columns="[
      { key: 'name', label: t('apiCall.headerName'), required: true },
      { key: 'value', label: t('apiCall.headerValue'), required: true },
      { key: 'actions', label: t('common.actions') },
    ]"
    @add="rows.push({ name: '', field: hidden(false, false) })"
  >
    <tr v-for="(row, i) in rows" :key="i">
      <td>
        <FormItem
          :id="(prefix ?? 'task') + '-header-name-' + i"
          :label="t('apiCall.headerName')"
          hide-label
          required
          :invalid="!!error('header-name-' + i)"
          :message="error('header-name-' + i)"
        >
          <NInput
            v-model:value="row.name"
            :disabled="disabled || row.field.configured"
            :input-props="{
              id: (prefix ?? 'task') + '-header-name-' + i,
              'aria-label': t('apiCall.headerName') + ' ' + (i + 1),
              'aria-required': true,
              'aria-invalid': !!error('header-name-' + i),
            }"
          />
        </FormItem>
      </td>
      <td>
        <ConfiguredField
          :id="(prefix ?? 'task') + '-header-value-' + i"
          :label="t('apiCall.headerValue') + ' ' + (i + 1)"
          :field="row.field"
          table-cell
          required
          secret
          password-toggle
          :disabled="disabled"
          :message="error('header-value-' + i)"
          @change="row.field = $event"
        />
      </td>
      <td>
        <NButton
          text
          type="error"
          :disabled="disabled"
          :aria-label="t('apiCall.remove') + ' ' + (i + 1)"
          @click="rows.splice(i, 1)"
          >{{ t('apiCall.remove') }}</NButton
        >
      </td>
    </tr>
    <template #feedback
      ><p v-if="error('headers')" class="configuration-field-error" role="alert">
        {{ error('headers') }}
      </p></template
    >
  </EditableTable>
</template>
