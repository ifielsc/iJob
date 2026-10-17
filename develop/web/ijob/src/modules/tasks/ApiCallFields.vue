<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NInput, NInputNumber, NSelect, NSwitch } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import EditableTable from '../../shared/ui/EditableTable.vue'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import { HeaderEditor } from '../configuration/public/components'
import { apiHeaders, type ApiDraft } from './api-call-model'
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
  advanced?: boolean
  idPrefix?: string
  controlRequest?: boolean
}>()
const draft = defineModel<ApiDraft>('draft', { required: true })
const headers = computed({
  get: () => apiHeaders(draft.value),
  set: (value) => {
    if (draft.value.protocol === 'WEB_SERVICE') draft.value.soap.headers = value
    else draft.value.http.headers = value
  },
})
const emit = defineEmits<{ expanded: [boolean]; select: [boolean] }>(),
  { t } = useI18n()
const error = (k: string) =>
  props.submitted && props.errors[k] ? t('apiCall.' + props.errors[k]) : undefined
const soap = computed(() => draft.value.protocol === 'WEB_SERVICE')
const prefix = computed(() => props.idPrefix ?? 'task')
</script>
<template>
  <div v-if="!advanced" class="api-fields">
    <div v-if="!soap" class="api-field-group">
      <div class="task-advanced-grid">
        <FormItem
          :id="prefix + '-method'"
          :label="t('apiCall.method')"
          required
          :invalid="!!error('method')"
          :message="error('method')"
          ><NSelect
            v-model:value="draft.http.method"
            :disabled="disabled"
            :options="
              ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map((value) => ({
                value,
                label: value,
              }))
            "
            :input-props="{ id: prefix + '-method', 'aria-label': t('apiCall.method') }"
            @update:show="emit('select', $event)"
        /></FormItem>
        <FormItem
          :id="prefix + '-path'"
          :label="t(controlRequest ? 'apiCall.remoteStopPath' : 'apiCall.path')"
          :required="controlRequest"
          :invalid="!!error('path')"
          :message="error('path')"
          ><NInput
            v-model:value="draft.http.path"
            :disabled="disabled"
            :input-props="{ id: prefix + '-path', 'aria-invalid': !!error('path') }"
        /></FormItem>
      </div>
      <p class="task-note">
        {{ t(controlRequest ? 'apiCall.remoteStopPathHelp' : 'apiCall.pathHelp') }}
      </p>
    </div>
    <template v-else>
      <p class="task-note">{{ t('apiCall.soapHelp') }}</p>
      <div class="task-advanced-grid">
        <FormItem
          v-for="key in ['operation', 'action'] as const"
          :id="prefix + '-' + key"
          :key="key"
          :label="t('apiCall.' + key)"
          :required="key === 'operation'"
          :invalid="!!error(key)"
          :message="error(key)"
          ><NInput
            v-model:value="draft.soap[key]"
            :disabled="disabled"
            :input-props="{ id: prefix + '-' + key, 'aria-invalid': !!error(key) }"
        /></FormItem>
      </div>
      <EditableTable
        :title="t('apiCall.parameters')"
        :columns="[
          { key: 'name', label: t('apiCall.headerName'), required: true },
          { key: 'value', label: t('apiCall.headerValue'), required: false },
          { key: 'actions', label: t('common.actions') },
        ]"
        :count="draft.soap.parameters.length"
        :add-label="t('apiCall.addParameter')"
        :disabled="disabled"
        @add="draft.soap.parameters.push({ name: '', value: '', initial: '', original: undefined })"
      >
        <tr v-for="(entry, i) in draft.soap.parameters" :key="i">
          <td>
            <FormItem
              :id="prefix + '-parameter-' + i"
              hide-label
              :label="t('apiCall.headerName')"
              required
              :invalid="!!error('parameter-' + i)"
              :message="error('parameter-' + i)"
            >
              <NInput
                v-model:value="entry.name"
                :disabled="disabled"
                :input-props="{
                  id: prefix + '-parameter-' + i,
                  'aria-label': t('apiCall.headerName') + ' ' + (i + 1),
                  'aria-invalid': !!error('parameter-' + i),
                }"
              />
            </FormItem>
          </td>
          <td>
            <FormItem
              :id="prefix + '-parameter-value-' + i"
              hide-label
              :label="t('apiCall.headerValue')"
            >
              <NInput
                v-model:value="entry.value"
                :disabled="disabled"
                :input-props="{
                  id: prefix + '-parameter-value-' + i,
                  'aria-label': t('apiCall.headerValue') + ' ' + (i + 1),
                  'aria-invalid': !!error('parameter-' + i),
                }"
              />
            </FormItem>
          </td>
          <td>
            <NButton
              text
              type="error"
              :disabled="disabled"
              :aria-label="t('apiCall.remove') + ' ' + (i + 1)"
              @click="draft.soap.parameters.splice(i, 1)"
              >{{ t('apiCall.remove') }}</NButton
            >
          </td>
        </tr>
        <template #feedback
          ><p v-if="error('parameters')" class="field-error" role="alert">
            {{ error('parameters') }}
          </p></template
        >
      </EditableTable>
    </template>
    <HeaderEditor
      v-model:rows="headers"
      :prefix="prefix"
      :errors="errors"
      :submitted="submitted"
      :disabled="disabled"
    />
  </div>
  <div v-else class="api-fields">
    <FormItem
      :id="prefix + '-maxResponseBytes'"
      :label="t('apiCall.maxResponseBytes')"
      :invalid="!!error('maxResponseBytes')"
      :message="error('maxResponseBytes')"
      ><NInputNumber
        v-model:value="draft.maxResponseBytes"
        :disabled="disabled"
        :precision="0"
        :input-props="{
          id: prefix + '-maxResponseBytes',
          'aria-invalid': !!error('maxResponseBytes'),
        }"
    /></FormItem>
    <template v-if="!soap || draft.useCondition || draft.extractors.length">
      <p class="task-note">{{ t(soap ? 'apiCall.legacySoap' : 'apiCall.responseHelp') }}</p>
      <div class="task-inline">
        <label for="api-success">{{ t('apiCall.success') }}</label
        ><NSwitch
          id="api-success"
          v-model:value="draft.useCondition"
          :disabled="disabled"
          :aria-label="t('apiCall.success')"
        />
      </div>
      <template v-if="draft.useCondition"
        ><FormItem
          :id="prefix + '-expression'"
          :label="t('apiCall.expression')"
          required
          :invalid="!!error('expression')"
          :message="error('expression')"
          ><NInput
            v-model:value="draft.expression"
            :disabled="disabled"
            :input-props="{ id: prefix + '-expression', 'aria-invalid': !!error('expression') }"
        /></FormItem>
        <div class="task-advanced-grid">
          <FormItem id="api-operator" :label="t('apiCall.operator')"
            ><NSelect
              v-model:value="draft.operator"
              :disabled="disabled"
              :options="['EQ', 'NE', 'GT', 'LT'].map((value) => ({ value, label: value }))"
              :input-props="{ id: 'api-operator', 'aria-label': t('apiCall.operator') }"
              @update:show="emit('select', $event)" /></FormItem
          ><FormItem id="api-expected" :label="t('apiCall.expected')"
            ><NInput
              v-model:value="draft.expected"
              :disabled="disabled"
              :input-props="{ id: 'api-expected' }"
          /></FormItem></div
      ></template>
      <EditableTable
        :title="t('apiCall.extractors')"
        :columns="[
          { key: 'name', label: t('apiCall.variable'), required: true },
          { key: 'value', label: t('apiCall.expression'), required: true },
          { key: 'actions', label: t('common.actions') },
        ]"
        :count="draft.extractors.length"
        :add-label="t('apiCall.addExtractor')"
        :disabled="disabled"
        @add="draft.extractors.push({ variable: '', expression: '' })"
      >
        <tr v-for="(entry, i) in draft.extractors" :key="i">
          <td>
            <FormItem
              :id="prefix + '-extractor-' + i"
              hide-label
              :label="t('apiCall.variable')"
              required
              :invalid="!!error('extractor-' + i)"
              :message="error('extractor-' + i)"
            >
              <NInput
                v-model:value="entry.variable"
                :disabled="disabled"
                :input-props="{
                  id: prefix + '-extractor-' + i,
                  'aria-label': t('apiCall.variable') + ' ' + (i + 1),
                  'aria-invalid': !!error('extractor-' + i),
                }"
              />
            </FormItem>
          </td>
          <td>
            <FormItem
              :id="prefix + '-extractor-expression-' + i"
              hide-label
              :label="t('apiCall.expression')"
              required
            >
              <NInput
                v-model:value="entry.expression"
                :disabled="disabled"
                :input-props="{
                  id: prefix + '-extractor-expression-' + i,
                  'aria-label': t('apiCall.expression') + ' ' + (i + 1),
                  'aria-invalid': !!error('extractor-' + i),
                }"
              />
            </FormItem>
          </td>
          <td>
            <NButton
              text
              type="error"
              :disabled="disabled"
              :aria-label="t('apiCall.remove') + ' ' + (i + 1)"
              @click="draft.extractors.splice(i, 1)"
              >{{ t('apiCall.remove') }}</NButton
            >
          </td>
        </tr>
        <template #feedback
          ><p v-if="error('extractors')" class="field-error" role="alert">
            {{ error('extractors') }}
          </p></template
        >
      </EditableTable>
    </template>
  </div>
</template>
