<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NInput, NSelect } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import SqlEditor from '../../shared/ui/SqlEditor.vue'
import { json, jsonValue, operators, type Condition, type Operand } from './model'
const props = withDefaults(
  defineProps<{
    modelValue: string
    disabled?: boolean
    depth?: number
    drafts: Record<string, string>
    path?: string
  }>(),
  { depth: 1, path: '' },
)
const emit = defineEmits<{
  'update:modelValue': [string]
  'update:drafts': [Record<string, string>]
  invalid: [boolean]
}>()
const { t } = useI18n(),
  advanced = ref(false),
  inputErrors = computed(() =>
    Object.fromEntries(
      Object.keys(props.drafts)
        .filter((key) => key.startsWith(props.path))
        .map((key) => [key.slice(props.path.length), true]),
    ),
  )
const current = computed(() => {
  try {
    const c = jsonValue(props.modelValue) as Condition
    return c && operators.includes(c.op) ? c : undefined
  } catch {
    return undefined
  }
})
const unary = computed(() => ['EXISTS', 'IS_NULL', 'IS_NOT_NULL'].includes(current.value?.op ?? ''))
const grouped = computed(() => ['AND', 'OR', 'NOT'].includes(current.value?.op ?? ''))
const options = operators.map((value) => ({ label: value, value }))
const seed = (): Condition => ({ op: 'EQ', left: { var: 'input.value' }, right: { literal: 0 } })
function update(c: Condition) {
  emit('update:modelValue', json(c))
}
function changeOp(op: string) {
  if (op === 'AND' || op === 'OR') update({ op, operands: [current.value ?? seed(), seed()] })
  else if (op === 'NOT') update({ op, operand: current.value ?? seed() })
  else
    update({
      op,
      left: ['EXISTS', 'IS_NULL', 'IS_NOT_NULL'].includes(op)
        ? { var: current.value?.left?.var ?? 'input.value' }
        : (current.value?.left ?? { var: 'input.value' }),
      ...(['EXISTS', 'IS_NULL', 'IS_NOT_NULL'].includes(op)
        ? {}
        : {
            right:
              op === 'IN' || op === 'NOT_IN'
                ? { literal: [0] }
                : (current.value?.right ?? { literal: 0 }),
          }),
    })
  clearDrafts()
}
function clearDrafts() {
  emit(
    'update:drafts',
    Object.fromEntries(Object.entries(props.drafts).filter(([key]) => !key.startsWith(props.path))),
  )
}
function draftField(key: string, text?: string) {
  const next = { ...props.drafts }
  if (text === undefined) delete next[key]
  else next[key] = text
  emit('update:drafts', next)
}
function advancedUpdate(text: string) {
  clearDrafts()
  emit('update:modelValue', text)
}
function setOperand(side: 'left' | 'right', value: Operand) {
  draftField(props.path + side)
  if (current.value) update({ ...current.value, [side]: value })
}
function setLiteral(side: 'left' | 'right', text: string) {
  try {
    const value = jsonValue(text)
    setOperand(side, { literal: value })
    draftField(props.path + side)
  } catch {
    draftField(props.path + side, text)
  }
}
function child(index: number, text: string) {
  try {
    if (!current.value) return
    const c = { ...current.value }
    if (c.op === 'NOT') c.operand = jsonValue(text) as Condition
    else c.operands = c.operands!.map((v, i) => (i === index ? (jsonValue(text) as Condition) : v))
    update(c)
  } catch {
    draftField(props.path + 'child' + index, text)
  }
}
function remove(index: number) {
  clearDrafts()
  if (current.value?.operands)
    update({ ...current.value, operands: current.value.operands.filter((_, i) => i !== index) })
}
watch(inputErrors, () => emit('invalid', Object.values(inputErrors.value).some(Boolean)), {
  deep: true,
  immediate: true,
})
</script>
<template>
  <div class="job-condition">
    <div class="job-inline">
      <label>{{ t('jobs.operator') }}</label
      ><NSelect
        :value="current?.op"
        :options="options"
        :disabled="disabled || !current || depth >= 16"
        :aria-label="t('jobs.operator')"
        @update:value="changeOp"
      /><NButton v-if="depth === 1" size="small" @click="advanced = !advanced">{{
        t(advanced ? 'jobs.structured' : 'jobs.advancedJson')
      }}</NButton>
    </div>
    <template v-if="advanced || !current || (depth >= 16 && grouped)"
      ><SqlEditor
        :model-value="modelValue"
        language="JSON"
        :title="t('jobs.condition')"
        :disabled="disabled"
        :context-key="'condition-' + depth"
        @update:model-value="advancedUpdate"
    /></template>
    <template v-else-if="grouped">
      <div
        v-for="(c, i) in current.op === 'NOT' ? [current.operand!] : current.operands"
        :key="i"
        class="job-condition-child"
      >
        <ConditionEditor
          :model-value="json(c)"
          :depth="depth + 1"
          :drafts="drafts"
          :path="path + i + '.'"
          :disabled="disabled"
          @update:drafts="emit('update:drafts', $event)"
          @update:model-value="child(i, $event)"
        />
        <NButton
          v-if="!disabled && current.op !== 'NOT' && (current.operands?.length ?? 0) > 2"
          text
          type="error"
          @click="remove(i)"
          >{{ t('jobs.removeCondition') }}</NButton
        >
      </div>
      <NButton
        v-if="!disabled && current.op !== 'NOT' && depth < 16"
        size="small"
        @click="update({ ...current, operands: [...current.operands!, seed()] })"
        >{{ t('jobs.addCondition') }}</NButton
      >
    </template>
    <template v-else>
      <div
        v-for="side in (unary ? ['left'] : ['left', 'right']) as ('left' | 'right')[]"
        :key="side"
        class="job-field"
      >
        <label>{{ t(side === 'left' ? 'jobs.lhs' : 'jobs.rhs') }}</label>
        <NSelect
          :value="current[side]?.var !== undefined ? 'variable' : 'literal'"
          :options="[
            { value: 'variable', label: t('jobs.variable') },
            { value: 'literal', label: t('jobs.literal') },
          ]"
          :disabled="disabled || unary"
          @update:value="
            setOperand(side, $event === 'variable' ? { var: 'input.value' } : { literal: 0 })
          "
        />
        <NInput
          v-if="current[side]?.var !== undefined"
          :value="current[side]?.var"
          :disabled="disabled"
          :input-props="{ 'aria-label': t('jobs.variable') + ' ' + side }"
          @update:value="setOperand(side, { var: $event })"
        />
        <NInput
          v-else
          :key="current.op + '-' + side"
          :value="drafts[path + side] ?? json(current[side]?.literal ?? null)"
          :disabled="disabled"
          :status="inputErrors[side] ? 'error' : undefined"
          :input-props="{
            'aria-label': t('jobs.literal') + ' ' + side,
            'aria-invalid': !!inputErrors[side],
          }"
          @update:value="setLiteral(side, $event)"
        />
        <span v-if="inputErrors[side]" class="job-error" role="alert">{{
          t('jobs.conditionInvalid')
        }}</span>
      </div>
    </template>
  </div>
</template>
