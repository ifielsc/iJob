<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NInput, NSelect } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import EtlTable from './EtlTable.vue'
import LocalJarValue from './LocalJarValue.vue'
import { parameterKinds, type ParameterKind, type ParameterNode } from './local-jar-model'
const props = defineProps<{
  title: string
  disabled: boolean
  errors: Record<string, string>
  submitted: boolean
}>()
const emit = defineEmits<{
  edit: [ParameterNode]
  kind: [ParameterNode, ParameterKind]
  remove: [ParameterNode, ParameterNode]
  add: [ParameterNode]
  select: [boolean]
}>()
const node = defineModel<ParameterNode>('node', { required: true })
const { t } = useI18n(),
  page = ref(1)
const pages = computed(() => Math.max(1, Math.ceil(node.value.children.length / 20)))
watch(
  () => node.value.key,
  () => {
    page.value = 1
  },
)
watch(pages, (value) => {
  page.value = Math.min(page.value, value)
})
const rows = computed(() => node.value.children.slice((page.value - 1) * 20, page.value * 20))
const columns = computed(() => [
  {
    key: 'name',
    label: t(node.value.kind === 'array' ? 'localJar.index' : 'localJar.name'),
    width: '27%',
  },
  { key: 'type', label: t('localJar.type'), width: '23%' },
  { key: 'value', label: t('localJar.value') },
  {
    key: 'actions',
    label: t('localJar.actions'),
    width: node.value.kind === 'array' ? '112px' : '62px',
  },
])
const options = computed(() =>
  parameterKinds.map((value) => ({ value, label: t('localJar.' + value) })),
)
const index = (row: ParameterNode) => node.value.children.indexOf(row)
const label = (row: ParameterNode, field: string) =>
  t('localJar.rowLabel', { row: index(row) + 1, field: t('localJar.' + field) })
const error = (row: ParameterNode, field: string) =>
  props.submitted && props.errors['localJar.nodes.' + row.key + '.' + field]
    ? t('localJar.' + props.errors['localJar.nodes.' + row.key + '.' + field])
    : undefined
function move(row: ParameterNode, delta: number) {
  if (props.disabled) return
  const i = index(row),
    j = i + delta
  if (j < 0 || j >= node.value.children.length) return
  node.value.children.splice(i, 1)
  node.value.children.splice(j, 0, row)
  page.value = Math.floor(j / 20) + 1
}
function show(key: string) {
  const i = node.value.children.findIndex((n) => n.key === key)
  if (i >= 0) page.value = Math.floor(i / 20) + 1
}
function add() {
  emit('add', node.value)
  page.value = Math.ceil((node.value.children.length || 1) / 20)
}
defineExpose({ show })
</script>
<template>
  <EtlTable
    :title="title"
    :count="node.children.length"
    :columns="columns"
    :disabled="disabled"
    :add-label="t('localJar.add')"
    @add="add"
  >
    <tr v-for="row in rows" :key="row.key">
      <td>
        <span v-if="node.kind === 'array'" class="local-jar-index">[{{ index(row) }}]</span
        ><NInput
          v-else
          :value="row.name"
          :disabled="disabled"
          :status="error(row, 'name') ? 'error' : undefined"
          :input-props="{
            id: 'task-localJar.nodes.' + row.key + '.name',
            'aria-label': label(row, 'name'),
          }"
          @update:value="row.name = $event"
        />
        <div v-if="error(row, 'name')" role="alert" class="local-jar-error">
          {{ error(row, 'name') }}
        </div>
      </td>
      <td>
        <NSelect
          :value="row.kind"
          :options="options"
          :disabled="disabled"
          :aria-label="label(row, 'type')"
          @update:value="emit('kind', row, $event)"
          @update:show="emit('select', $event)"
        />
      </td>
      <td>
        <LocalJarValue
          :node="row"
          :disabled="disabled"
          :error="error(row, 'value')"
          :label="label(row, 'value')"
          @edit="emit('edit', row)"
        />
      </td>
      <td class="etl-table-actions">
        <div class="local-jar-actions">
          <template v-if="node.kind === 'array'"
            ><NButton
              text
              :disabled="disabled || index(row) === 0"
              :aria-label="label(row, 'up')"
              @click="move(row, -1)"
              >↑</NButton
            ><NButton
              text
              :disabled="disabled || index(row) === node.children.length - 1"
              :aria-label="label(row, 'down')"
              @click="move(row, 1)"
              >↓</NButton
            ></template
          ><NButton
            text
            :disabled="disabled"
            :aria-label="label(row, 'remove')"
            @click="emit('remove', node, row)"
            >{{ t('localJar.remove') }}</NButton
          >
        </div>
      </td>
    </tr>
    <template #feedback
      ><div v-if="pages > 1" class="local-jar-pages">
        <NButton size="small" :disabled="disabled || page === 1" @click="page--">{{
          t('localJar.previous')
        }}</NButton
        ><span>{{ page }} / {{ pages }}</span
        ><NButton size="small" :disabled="disabled || page === pages" @click="page++">{{
          t('localJar.next')
        }}</NButton>
      </div></template
    >
  </EtlTable>
</template>
