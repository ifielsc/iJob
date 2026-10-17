<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import IdentityText from '../../shared/ui/IdentityText.vue'
import { textOf, type Template } from './model'
const props = defineProps<{ template: Template }>()
const query = ref('')
const visible = computed(() =>
  props.template.fields.filter((field) =>
    (field.name + ' ' + (field.description ?? ''))
      .toLocaleLowerCase()
      .includes(query.value.trim().toLocaleLowerCase()),
  ),
)
const { t } = useI18n()
</script>
<template>
  <div class="template-details">
    <dl class="unified-facts">
      <dt>{{ t('parameters.name') }}</dt>
      <dd>{{ template.name }}</dd>
      <dt>ID</dt>
      <dd><IdentityText :value="template.id" /></dd>
      <dt>{{ t('parameters.description') }}</dt>
      <dd>{{ template.description || '—' }}</dd>
    </dl>
    <h3>{{ t('parameters.list') }} · {{ template.fields.length }}</h3>
    <div v-if="template.fields.length > 6" class="parameter-table-tools">
      <input
        v-model="query"
        type="search"
        :placeholder="t('details.searchRows')"
        :aria-label="t('details.searchRows')"
      />
      <span>{{
        t('details.rowCounts', { total: template.fields.length, count: visible.length })
      }}</span>
    </div>
    <div class="table-scroll" :class="{ 'bounded-data-scroll': template.fields.length > 6 }">
      <table class="template-details-table">
        <thead>
          <tr>
            <th>{{ t('parameters.fieldName') }}</th>
            <th>{{ t('parameters.type') }}</th>
            <th>{{ t('parameters.defaultValue') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="field in visible" :key="field.name">
            <td>
              <strong>{{ field.name }}</strong>
              <div class="parameter-note">
                {{ t(field.required ? 'parameters.required' : 'parameters.optional')
                }}<span v-if="field.sensitive"> · {{ t('parameters.sensitive') }}</span>
              </div>
              <div v-if="field.description" class="parameter-note">{{ field.description }}</div>
            </td>
            <td>
              {{ t('parameters.' + field.type) }}
              <div v-if="field.options?.length" class="parameter-note">
                {{ field.options.join(' / ') }}
              </div>
              <div v-if="field.minimum != null" class="parameter-note">
                {{ t('parameters.min') }}: {{ field.minimum }}
              </div>
              <div v-if="field.maximum != null" class="parameter-note">
                {{ t('parameters.max') }}: {{ field.maximum }}
              </div>
              <div v-if="field.maxLength != null" class="parameter-note">
                {{ t('parameters.maxLength') }}: {{ field.maxLength }}
              </div>
            </td>
            <td>
              {{
                field.sensitive
                  ? '—'
                  : field.hasDefault
                    ? textOf(field.defaultValue, field.type)
                    : '—'
              }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <EmptyState v-if="!template.fields.length" compact :message="t('parameters.noFields')" />
  </div>
</template>
<style scoped>
.template-details {
  display: grid;
  gap: 12px;
}
.template-details dl {
  margin: 0;
  grid-template-columns: 80px minmax(0, 1fr);
}
.template-details-table {
  table-layout: fixed;
  font-size: 13px;
}
.template-details-table th,
.template-details-table td {
  padding: 8px 10px;
  min-width: 0;
  overflow-wrap: anywhere;
}
</style>
