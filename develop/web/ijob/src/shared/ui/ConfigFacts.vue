<script setup lang="ts">
import { computed, inject } from 'vue'
import { referenceNamesKey, referenceKind } from '../runtime/reference-names'
import { isLosslessNumber } from 'lossless-json'
import { useI18n } from 'vue-i18n'
import { detailLabel, literalChildren } from './detail-labels'
defineOptions({ name: 'ConfigFacts' })
const props = withDefaults(
  defineProps<{
    value: unknown
    scope?: string
    path?: string
    grouped?: boolean
    literal?: boolean
  }>(),
  { scope: 'tasks', path: '', grouped: true, literal: false },
)
const { t, te } = useI18n()
const references = inject(referenceNamesKey, undefined)
const reference = computed(() =>
  props.literal
    ? undefined
    : references?.value.get(referenceKind(props.path) + ':' + String(props.value)),
)
const structured = computed(
  () => props.value !== null && typeof props.value === 'object' && !isLosslessNumber(props.value),
)
const entries = computed(() => (structured.value ? Object.entries(props.value as object) : []))
const flatArray = computed(
  () => props.grouped && Array.isArray(props.value) && props.value.every((value) => !nested(value)),
)
function label(key: string) {
  if (Array.isArray(props.value)) return String(Number(key) + 1)
  return props.literal ? key : detailLabel(props.scope, props.path, key, t, te)
}
const nested = (value: unknown) =>
  value !== null && typeof value === 'object' && !isLosslessNumber(value)
const display = computed(() => {
  const value = props.value
  if (value === null || value === undefined || value === '') return '—'
  if (!props.literal && typeof value === 'boolean') {
    if (props.path.endsWith('Configured')) return t(value ? 'config.configured' : 'config.unset')
    return t(value ? 'details.yes' : 'details.no')
  }
  if (props.scope === 'monitor') {
    if (typeof value === 'boolean') return t('monitor.' + (value ? 'yes' : 'no'))
    if (
      (/^collect\.items\.\d+$/.test(props.path) || props.path.endsWith('.matchType')) &&
      te('monitor.' + String(value))
    )
      return t('monitor.' + String(value))
  }
  return String(value)
})
</script>
<template>
  <div v-if="flatArray" class="fact-values">
    <ConfigFacts
      v-for="(item, index) in value as unknown[]"
      :key="index"
      :value="item"
      :scope="scope"
      :path="path + '.' + index"
      :literal="literal"
    />
    <span v-if="!(value as unknown[]).length">—</span>
  </div>
  <dl v-else-if="structured" class="config-facts">
    <template v-for="[key, item] in entries" :key="key"
      ><dt :class="{ 'fact-group': grouped && nested(item) }">{{ label(key) }}</dt>
      <dd :class="{ 'fact-group-value': grouped && nested(item) }">
        <ConfigFacts
          :value="item"
          :scope="scope"
          :path="path ? path + '.' + key : key"
          :grouped="grouped"
          :literal="literal || literalChildren(scope, path, key)"
        /></dd
    ></template>
    <dd v-if="!entries.length">—</dd>
  </dl>
  <div v-else-if="reference" class="fact-value">
    <strong>{{ reference.name }}</strong
    ><br />{{ display }}
  </div>
  <pre v-else class="fact-value">{{ display }}</pre>
</template>
<style scoped>
.config-facts {
  display: grid;
  grid-template-columns: minmax(90px, 30%) minmax(0, 1fr);
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}
dt {
  color: var(--muted);
  overflow-wrap: anywhere;
}
dd {
  margin: 0;
  min-width: 0;
}
.fact-value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  margin: 0;
}
.fact-values {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.fact-group,
.fact-group-value {
  grid-column: 1 / -1;
}
.fact-group {
  font-weight: 600;
  color: var(--text);
}
.fact-group-value {
  padding-left: 12px;
  border-left: 1px solid var(--border);
}
@media (max-width: 480px) {
  .config-facts {
    grid-template-columns: minmax(0, 1fr);
  }
  dd {
    padding-left: 8px;
  }
}
</style>
