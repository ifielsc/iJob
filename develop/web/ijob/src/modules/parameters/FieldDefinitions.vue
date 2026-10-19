<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  NButton,
  NCheckbox,
  NCollapse,
  NCollapseItem,
  NInput,
  NInputNumber,
  NSelect,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { copy, emptyField, fieldTypes, textOf, valueOf, type Field } from './model'
import FieldValue from './FieldValue.vue'
import ParameterTable from './ParameterTable.vue'
const props = defineProps<{ modelValue: Field[]; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: Field[]] }>()
const { t } = useI18n()
// Keep partial numbers and JSON exactly as typed; parsing must not rewrite the input.
const defaults = ref<(string | undefined)[]>([])
const i = ref(0)
const f = computed(() => props.modelValue[i.value])
watch(
  () => props.modelValue.length,
  (length) => {
    i.value = Math.max(0, Math.min(i.value, length - 1))
  },
)
function add() {
  if (props.disabled || props.modelValue.length >= 100) return
  const next = [...props.modelValue, emptyField()]
  emit('update:modelValue', next)
  i.value = next.length - 1
}
function duplicate(index: number) {
  if (props.disabled || props.modelValue.length >= 100) return
  defaults.value[props.modelValue.length] = defaults.value[index]
  const field = copy(props.modelValue[index]!)
  const base = field.name || 'parameter'
  let suffix = 1
  while (props.modelValue.some((f) => f.name === `${base}_${suffix}`)) suffix++
  field.name = `${base}_${suffix}`
  emit('update:modelValue', [...props.modelValue, field])
  i.value = props.modelValue.length
}
function remove(index: number) {
  defaults.value.splice(index, 1)
  emit(
    'update:modelValue',
    props.modelValue.filter((_, n) => n !== index),
  )
  if (index < i.value) i.value--
}
function set(index: number, key: keyof Field, value: unknown) {
  const rows = copy(props.modelValue)
  if (key === 'type') {
    const field = rows[index]!
    const text = defaults.value[index] ?? textOf(field.defaultValue, field.type)
    defaults.value[index] = undefined
    if (field.hasDefault) {
      try {
        field.defaultValue = valueOf(text, value as Field['type'])
      } catch {
        field.defaultValue = value === 'BOOLEAN' ? false : undefined
      }
    }
  }
  Object.assign(rows[index]!, { [key]: value })
  if (key === 'sensitive' && value) {
    defaults.value[index] = undefined
    rows[index]!.hasDefault = false
    delete rows[index]!.defaultValue
  }
  emit('update:modelValue', rows)
}
function defaultValue(index: number, text: string) {
  defaults.value[index] = text
  try {
    set(index, 'defaultValue', valueOf(text, props.modelValue[index]!.type))
  } catch {
    set(index, 'defaultValue', text)
  }
}
</script>
<template>
  <div class="parameter-definition">
    <div class="parameter-section-heading">
      <h3>
        {{ t('parameters.list') }} <small>{{ modelValue.length }} / 100</small>
      </h3>
      <NButton size="small" :disabled="disabled || modelValue.length >= 100" @click="add"
        >＋ {{ t('parameters.add') }}</NButton
      >
    </div>
    <p v-if="modelValue.length >= 100" class="parameter-note" role="status">
      {{ t('parameters.fieldLimit') }}
    </p>
    <ParameterTable
      :fields="modelValue"
      :active="i"
      :values="
        modelValue.map((f, index) =>
          f.hasDefault ? (defaults[index] ?? textOf(f.defaultValue, f.type)) : '',
        )
      "
      @select="i = $event"
    >
      <template #action="{ index }"
        ><div class="parameter-actions">
          <NButton
            text
            size="small"
            :disabled="disabled || modelValue.length >= 100"
            :aria-label="t('parameters.duplicate') + ' ' + modelValue[index]?.name"
            @click="duplicate(index)"
            >{{ t('parameters.duplicate') }}</NButton
          ><NButton
            text
            size="small"
            type="error"
            :disabled="disabled"
            :aria-label="t('parameters.remove') + ' ' + modelValue[index]?.name"
            @click="remove(index)"
            >{{ t('parameters.remove') }}</NButton
          >
        </div></template
      >
    </ParameterTable>
    <section v-if="f" class="parameter-detail">
      <div class="parameter-section-heading">
        <h3>{{ t('parameters.selectedField') }} · {{ f.name || t('parameters.unnamed') }}</h3>
        <small>{{ t('parameters.selectHint') }}</small>
      </div>
      <div class="parameter-grid">
        <label
          >{{ t('parameters.fieldName')
          }}<NInput
            :value="f.name"
            :disabled="disabled"
            :input-props="{ 'aria-label': t('parameters.fieldName') }"
            @update:value="set(i, 'name', $event)"
        /></label>
        <label
          >{{ t('parameters.type')
          }}<NSelect
            :value="f.type"
            :disabled="disabled"
            :options="fieldTypes.map((value) => ({ value, label: t('parameters.' + value) }))"
            :aria-label="t('parameters.type')"
            @update:value="set(i, 'type', $event)"
        /></label>
      </div>
      <NInput
        :value="f.description"
        :disabled="disabled"
        :placeholder="t('parameters.description')"
        :input-props="{ 'aria-label': t('parameters.description') }"
        @update:value="set(i, 'description', $event)"
      />
      <div class="parameter-actions">
        <NCheckbox
          :checked="!!f.required"
          :disabled="disabled"
          @update:checked="set(i, 'required', $event)"
          >{{ t('parameters.required') }}</NCheckbox
        >
        <NCheckbox
          :checked="!!f.sensitive"
          :disabled="disabled"
          @update:checked="set(i, 'sensitive', $event)"
          >{{ t('parameters.sensitive') }}</NCheckbox
        >
        <NCheckbox
          :checked="!!f.hasDefault"
          :disabled="disabled || f.sensitive"
          @update:checked="set(i, 'hasDefault', $event)"
          >{{ t('parameters.hasDefault') }}</NCheckbox
        >
      </div>
      <label v-if="f.hasDefault"
        >{{ t('parameters.defaultValue')
        }}<FieldValue
          :model-value="defaults[i] ?? textOf(f.defaultValue, f.type)"
          :field="f"
          :disabled="disabled"
          :label="t('parameters.defaultValue')"
          @update:model-value="defaultValue(i, $event)"
      /></label>
      <NInput
        v-if="f.type === 'ENUM'"
        :value="(f.options ?? []).join('\n')"
        type="textarea"
        :disabled="disabled"
        :placeholder="t('parameters.options')"
        :input-props="{ 'aria-label': t('parameters.options') }"
        @update:value="set(i, 'options', $event.split('\n'))"
      />
      <NCollapse
        ><NCollapseItem :title="t('parameters.validation')"
          ><div class="parameter-grid">
            <template v-if="['INTEGER', 'DECIMAL'].includes(f.type)">
              <label
                >{{ t('parameters.min')
                }}<NInput
                  :value="f.minimum == null ? '' : String(f.minimum)"
                  :disabled="disabled"
                  @update:value="set(i, 'minimum', $event || null)"
              /></label>
              <label
                >{{ t('parameters.max')
                }}<NInput
                  :value="f.maximum == null ? '' : String(f.maximum)"
                  :disabled="disabled"
                  @update:value="set(i, 'maximum', $event || null)"
              /></label>
            </template>
            <label v-else-if="['STRING', 'ENUM'].includes(f.type)"
              >{{ t('parameters.maxLength')
              }}<NInputNumber
                :value="f.maxLength"
                :min="0"
                :max="65536"
                :disabled="disabled"
                @update:value="set(i, 'maxLength', $event)"
            /></label></div></NCollapseItem
      ></NCollapse>
    </section>
  </div>
</template>
