<!-- Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NInput, NSelect } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRuntime } from '../../app/runtime'
import { record, exactId } from '../../shared/api/values'
import { parseExact } from '../../shared/ui/json-form'
import RequestError from '../../shared/ui/RequestError.vue'
import JsonFormEditor from '../../shared/ui/JsonFormEditor.vue'
import { clone, json, pointerLabel, type Entry, type Step, type Choice, type Action } from './model'
const props = defineProps<{ entry: Entry; seed?: Step; modelValue?: Choice; disabled: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [Choice | undefined]; invalid: [boolean] }>()
const { t, te } = useI18n(),
  runtime = useRuntime(),
  page = ref(1),
  advanced = ref(false),
  invalid = ref(false)
const draft = ref(clone(props.modelValue?.body ?? props.entry.body)),
  text = ref(json(draft.value))
const action = computed(() => props.modelValue?.action ?? props.seed?.action ?? 'CREATE')
const target = computed(() =>
  props.modelValue ? (props.modelValue.targetId ?? null) : (props.seed?.targetId ?? null),
)
const canUpdate = computed(() => ['TASK', 'JOB'].includes(props.entry.kind))
const choices = computed(() => [
  { value: 'AUTO', label: t('packages.auto') },
  ...(!props.entry.referenceOnly ? [{ value: 'CREATE', label: t('packages.create') }] : []),
  { value: 'REUSE', label: t('packages.reuse') },
  ...(canUpdate.value ? [{ value: 'UPDATE', label: t('packages.update') }] : []),
])
const candidates = ref<{ value: string; label: string }[]>([])
const query = useQuery(
  computed(() => ({
    queryKey: ['package-candidates', runtime.session.revision, props.entry.kind, page.value],
    enabled: action.value !== 'CREATE',
    staleTime: 0,
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const value = await runtime.request(
        '/api/job-packages/candidates?kind=' + props.entry.kind + '&page=' + page.value,
        { signal, lossless: true },
      )
      if (!Array.isArray(value)) throw Error()
      return value.map((v) => {
        const r = record(v)
        return { value: exactId(r.id), label: String(r.name) + ' · #' + exactId(r.id) }
      })
    },
  })),
)
watch(
  () => query.data.value,
  (rows) => {
    if (rows)
      candidates.value = [
        ...new Map([...candidates.value, ...rows].map((r) => [r.value, r])).values(),
      ]
  },
  { immediate: true },
)
const options = computed(() =>
  target.value && !candidates.value.some((r) => r.value === target.value)
    ? [{ value: target.value, label: '#' + target.value }, ...candidates.value]
    : candidates.value,
)
function value(actionValue: Action = action.value): Choice {
  return {
    action: actionValue,
    ...(target.value ? { targetId: target.value } : {}),
    body: clone(draft.value),
    secrets: { ...props.modelValue?.secrets },
  }
}
function choose(next: string) {
  if (next === 'AUTO') {
    draft.value = clone(props.entry.body)
    text.value = json(draft.value)
    invalid.value = false
    emit('invalid', false)
    emit('update:modelValue', undefined)
    return
  }
  const choice = value(next as Action)
  if (next === 'CREATE') delete choice.targetId
  emit('update:modelValue', choice)
}
function setTarget(id: string | null) {
  const next = value()
  if (id) next.targetId = id
  else delete next.targetId
  emit('update:modelValue', next)
}
function credential(path: string, input: string) {
  const next = value()
  next.secrets = { ...next.secrets, [path]: input }
  emit('update:modelValue', next)
}
const fields = computed(() => {
  const result: { parent: string | null; key: string; value: unknown }[] = []
  for (const key of ['name', 'code', 'description', 'maxConcurrency'])
    if (Object.hasOwn(draft.value, key)) result.push({ parent: null, key, value: draft.value[key] })
  if (draft.value.concurrencyPolicy && typeof draft.value.concurrencyPolicy === 'object')
    result.push({
      parent: 'concurrencyPolicy',
      key: 'taskMaxConcurrency',
      value: record(draft.value.concurrencyPolicy).taskMaxConcurrency,
    })
  for (const [parent, config] of Object.entries(draft.value)) {
    if (
      !parent.endsWith('Config') ||
      !config ||
      typeof config !== 'object' ||
      Array.isArray(config)
    )
      continue
    for (const [key, v] of Object.entries(config)) {
      if (
        [
          'host',
          'port',
          'username',
          'jdbcUrl',
          'endpoint',
          'basePath',
          'schema',
          'bucket',
          'region',
          'bootstrapServers',
          'virtualHost',
          'exchange',
          'routingKey',
        ].includes(key)
      )
        result.push({ parent, key, value: v })
    }
  }
  return result
})
function fieldLabel(key: string) {
  return te('packages.' + key) ? t('packages.' + key) : key
}
function updateField(parent: string | null, key: string, input: string) {
  const body = clone(draft.value),
    container = parent ? record(body[parent]) : body
  container[key] = ['port', 'maxConcurrency', 'taskMaxConcurrency'].includes(key)
    ? input.trim()
      ? Number(input)
      : null
    : input
  draft.value = body
  text.value = json(body)
  invalid.value = false
  emit('invalid', false)
  emit('update:modelValue', value())
}
function updateJson(source: string) {
  text.value = source
  try {
    draft.value = record(parseExact(source))
    invalid.value = false
    emit('update:modelValue', value())
  } catch {
    invalid.value = true
  }
  emit('invalid', invalid.value)
}
</script>
<template>
  <div class="package-object-editor">
    <div class="package-grid">
      <label class="package-field"
        ><span>{{ t('packages.action') }}</span
        ><NSelect
          :value="modelValue?.action ?? 'AUTO'"
          :disabled="disabled"
          :options="choices"
          :aria-label="t('packages.action')"
          @update:value="choose"
      /></label>
      <label v-if="action !== 'CREATE'" class="package-field"
        ><span>{{ t('packages.target') }}</span
        ><NSelect
          :value="target"
          filterable
          clearable
          :disabled="disabled"
          :loading="query.isFetching.value"
          :options="options"
          :placeholder="t('packages.chooseTarget')"
          :aria-label="t('packages.target')"
          @update:value="setTarget"
        /><NButton
          v-if="query.data.value?.length === 100"
          text
          size="tiny"
          :disabled="query.isFetching.value"
          @click="page++"
          >{{ t('packages.more') }}</NButton
        ></label
      >
    </div>
    <RequestError :error="query.error.value" />
    <template v-if="action !== 'REUSE'">
      <div class="package-grid">
        <label v-for="field in fields" :key="(field.parent ?? '') + field.key" class="package-field"
          ><span>{{ fieldLabel(field.key) }}</span
          ><NInput
            :value="String(field.value ?? '')"
            :disabled="disabled"
            :input-props="{ 'aria-label': fieldLabel(field.key) }"
            @update:value="updateField(field.parent, field.key, $event)"
        /></label>
      </div>
      <fieldset v-if="entry.requiredSecrets.length">
        <legend>{{ t('packages.credentials') }}</legend>
        <p class="package-note">{{ t('packages.credentialNote') }}</p>
        <div class="package-grid">
          <label v-for="path in entry.requiredSecrets" :key="path" class="package-field"
            ><span
              >{{ fieldLabel(path.split('/').at(-1) ?? '') }}
              <small>{{ pointerLabel(path) }}</small></span
            ><NInput
              :value="modelValue?.secrets?.[path] ?? ''"
              type="password"
              show-password-on="click"
              autocomplete="new-password"
              :disabled="disabled"
              :input-props="{ 'aria-label': pointerLabel(path) }"
              @update:value="credential(path, $event)"
          /></label>
        </div>
      </fieldset>
      <NButton text size="small" :disabled="disabled" @click="advanced = !advanced">{{
        t('packages.advanced')
      }}</NButton>
      <JsonFormEditor
        v-if="advanced"
        :model-value="text"
        :disabled="disabled"
        shape="object"
        :title="t('packages.configuration')"
        @update:model-value="updateJson"
      />
      <p v-if="invalid" class="package-issue" role="alert">{{ t('packages.invalidJson') }}</p>
    </template>
  </div>
</template>
