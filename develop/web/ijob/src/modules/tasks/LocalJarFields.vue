<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { NButton, NInput, NSelect, useDialog } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import LineTabs from '../../shared/ui/LineTabs.vue'
import LocalJarTable from './LocalJarTable.vue'
import LocalJarValue from './LocalJarValue.vue'
import {
  container,
  parameterKinds,
  parameterNode,
  parameterPath,
  type LocalJarDraft,
  type ParameterKind,
  type ParameterNode,
} from './local-jar-model'
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
}>()
const draft = defineModel<LocalJarDraft>('draft', { required: true })
const emit = defineEmits<{ select: [boolean] }>()
const { t } = useI18n(),
  dialog = useDialog(),
  tab = ref('artifact'),
  root = ref<HTMLElement>(),
  selected = ref(''),
  confirming = ref(false)
const rootTable = ref<InstanceType<typeof LocalJarTable>>(),
  nestedTable = ref<InstanceType<typeof LocalJarTable>>()
const disabled = computed(() => props.disabled || confirming.value)
const path = computed(() => parameterPath(draft.value.root, selected.value) ?? [draft.value.root])
const nested = computed(() => (path.value.length > 1 ? path.value.at(-1) : undefined))
const tabs = computed(() =>
  ['artifact', 'parameters'].map((value) => ({ value, label: t('localJar.' + value) })),
)
const options = computed(() =>
  parameterKinds.map((value) => ({ value, label: t('localJar.' + value) })),
)
const error = (key: string) =>
  props.submitted && props.errors['localJar.' + key]
    ? t('localJar.' + props.errors['localJar.' + key])
    : undefined
const changed = computed(
  () =>
    draft.value.snapshot &&
    (['jarFile', 'sha256', 'entryClass'] as const).some(
      (key) => draft.value[key] !== draft.value.snapshot![key],
    ),
)
function label(node: ParameterNode, i: number) {
  const parent = path.value[i - 1]
  return i === 0
    ? t('localJar.parameters')
    : parent?.kind === 'array'
      ? '[' + parent.children.indexOf(node) + ']'
      : node.name === ''
        ? '""'
        : node.name
}
async function confirm() {
  confirming.value = true
  try {
    return await new Promise<boolean>((resolve) =>
      dialog.warning({
        title: t('localJar.confirmTitle'),
        content: t('localJar.confirmContent'),
        positiveText: t('localJar.confirm'),
        negativeText: t('localJar.cancel'),
        onPositiveClick: () => resolve(true),
        onNegativeClick: () => resolve(false),
        onClose: () => resolve(false),
        onEsc: () => resolve(false),
        onMaskClick: () => resolve(false),
      }),
    )
  } finally {
    confirming.value = false
  }
}
async function changeKind(node: ParameterNode, kind: ParameterKind) {
  if (disabled.value || node.kind === kind) return
  if (!(await confirm()) || props.disabled) return
  Object.assign(node, parameterNode(kind, node.name), { key: node.key })
  if (!parameterPath(draft.value.root, selected.value) || !container(node)) selected.value = ''
}
async function remove(parent: ParameterNode, node: ParameterNode) {
  if (disabled.value || !(await confirm()) || props.disabled) return
  const index = parent.children.indexOf(node)
  if (index >= 0) parent.children.splice(index, 1)
}
function add(node: ParameterNode) {
  if (!disabled.value) {
    let name = ''
    if (node.kind === 'object') {
      let i = 1
      while (node.children.some((n) => n.name === name)) name = 'parameter' + i++
    }
    node.children.push(parameterNode('string', name))
  }
}
async function focusError(key: string) {
  tab.value =
    key.startsWith('localJar.nodes.') || key === 'localJar.size' ? 'parameters' : 'artifact'
  const match = /^localJar.nodes\.([^.]+)\./.exec(key)
  if (match) {
    const nodes = parameterPath(draft.value.root, match[1]!)
    const parent = nodes?.at(-2)
    selected.value = parent && parent !== draft.value.root ? parent.key : ''
    await nextTick()
    ;(selected.value ? nestedTable.value : rootTable.value)?.show(match[1]!)
  }
  await nextTick()
  root.value?.querySelector<HTMLElement>('[id="task-' + key + '"]')?.focus()
}
defineExpose({ focusError })
</script>
<template>
  <section ref="root" class="local-jar-fields">
    <p v-if="draft.blocked" role="alert">{{ t('localJar.unsupported') }}</p>
    <template v-else>
      <LineTabs
        id="localJar-tabs"
        :label="t('localJar.title')"
        :tabs="tabs"
        :value="tab"
        panel-id="localJar-panel"
        :disabled="disabled"
        @change="tab = $event"
      />
      <div id="localJar-panel" role="tabpanel" :aria-labelledby="'localJar-tabs-' + tab">
        <template v-if="tab === 'artifact'">
          <FormItem
            v-for="key in ['jarFile', 'sha256', 'entryClass'] as const"
            :id="'task-localJar.' + key"
            :key="key"
            :label="t('localJar.' + key)"
            required
            :invalid="!!error(key)"
            :message="error(key)"
          >
            <NInput
              v-model:value="draft[key]"
              :disabled="disabled"
              :input-props="{
                id: 'task-localJar.' + key,
                'aria-describedby': 'localJar-' + key + '-hint',
              }"
              :placeholder="t('localJar.' + key + 'Placeholder')"
            />
            <p :id="'localJar-' + key + '-hint'" class="task-note">
              {{ t('localJar.' + key + 'Hint') }}
            </p>
          </FormItem>
          <section class="local-jar-snapshot" :aria-label="t('localJar.snapshot')">
            <h3>
              {{ t('localJar.snapshot') }}
              <span v-if="changed">· {{ t('localJar.published') }}</span>
            </h3>
            <dl v-if="draft.snapshot">
              <template
                v-for="key in ['jarSize', 'implementationVersion', 'spiVersion'] as const"
                :key="key"
                ><dt>{{ t('localJar.' + key) }}</dt>
                <dd>{{ draft.snapshot[key] }}{{ key === 'jarSize' ? ' bytes' : '' }}</dd></template
              >
            </dl>
            <p v-else class="task-note">{{ t('localJar.afterSave') }}</p>
          </section>
          <p class="task-note">{{ t('localJar.deploymentHint') }}</p>
        </template>
        <template v-else>
          <p class="task-note">{{ t('localJar.parameterHint') }}</p>
          <FormItem id="localJar-root-kind" :label="t('localJar.rootKind')"
            ><NSelect
              id="localJar-root-kind"
              :value="draft.root.kind"
              :options="options"
              :disabled="disabled"
              @update:value="changeKind(draft.root, $event)"
              @update:show="emit('select', $event)"
          /></FormItem>
          <LocalJarTable
            v-if="container(draft.root)"
            ref="rootTable"
            :node="draft.root"
            :title="t('localJar.parameters')"
            :disabled="disabled"
            :errors="errors"
            :submitted="submitted"
            @edit="selected = $event.key"
            @kind="changeKind"
            @remove="remove"
            @add="add"
            @select="emit('select', $event)"
          />
          <LocalJarValue
            v-else
            :node="draft.root"
            :disabled="disabled"
            :label="t('localJar.rootValue')"
            :error="error('nodes.' + draft.root.key + '.value')"
          />
          <section v-if="nested && container(nested)" class="local-jar-nested">
            <nav :aria-label="t('localJar.path')" class="local-jar-breadcrumb">
              <template v-for="(node, i) in path" :key="node.key"
                ><span v-if="i">/</span
                ><NButton text :disabled="disabled" @click="selected = i ? node.key : ''">{{
                  label(node, i)
                }}</NButton></template
              >
            </nav>
            <NButton size="small" :disabled="disabled" @click="selected = path.at(-2)?.key ?? ''">{{
              t('localJar.back')
            }}</NButton>
            <LocalJarTable
              ref="nestedTable"
              :node="nested"
              :title="t('localJar.children')"
              :disabled="disabled"
              :errors="errors"
              :submitted="submitted"
              @edit="selected = $event.key"
              @kind="changeKind"
              @remove="remove"
              @add="add"
              @select="emit('select', $event)"
            />
          </section>
          <p
            v-if="error('size')"
            id="task-localJar.size"
            tabindex="-1"
            role="alert"
            class="local-jar-error"
          >
            {{ error('size') }}
          </p>
        </template>
      </div>
    </template>
  </section>
</template>
<style>
.local-jar-fields {
  min-width: 0;
  width: 100%;
}
.local-jar-fields [role='tabpanel'] {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.local-jar-fields .task-note {
  margin: 0;
}
.local-jar-fields .field-content > .task-note {
  margin-top: 4px;
}
.local-jar-fields h3 {
  font-size: 13px;
  margin: 0 0 12px;
}
.local-jar-snapshot {
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
}
.local-jar-snapshot h3 span {
  font-weight: 400;
  color: var(--muted);
}
.local-jar-snapshot dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 0;
  font-size: 13px;
}
.local-jar-snapshot dt {
  color: var(--muted);
}
.local-jar-snapshot dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.local-jar-nested {
  margin-top: 16px;
  padding: 16px 0 0;
  border-top: 1px solid var(--border);
  display: grid;
  gap: 12px;
}
.local-jar-nested > .n-button {
  justify-self: start;
}
.local-jar-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  overflow-wrap: anywhere;
}
.local-jar-breadcrumb .n-button {
  max-width: 100%;
}
.local-jar-breadcrumb .n-button__content {
  white-space: normal;
  overflow-wrap: anywhere;
}
.local-jar-error {
  color: var(--error);
  font-size: 12px;
  margin-top: 4px;
}
.local-jar-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  min-height: 32px;
}
.local-jar-pages {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 8px;
}
.local-jar-index {
  line-height: 32px;
  color: var(--muted);
}
</style>
