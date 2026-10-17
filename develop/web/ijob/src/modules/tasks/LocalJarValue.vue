<script setup lang="ts">
import { NButton, NInput, NSwitch } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { container, type ParameterNode } from './local-jar-model'
defineProps<{ disabled: boolean; error?: string; label: string }>()
defineEmits<{ edit: [] }>()
const node = defineModel<ParameterNode>('node', { required: true })
const { t } = useI18n()
</script>
<template>
  <NButton
    v-if="container(node)"
    :id="'task-localJar.nodes.' + node.key + '.value'"
    size="small"
    :disabled="disabled"
    :aria-label="label"
    @click="$emit('edit')"
    >{{ t('localJar.items', { count: node.children.length }) }}</NButton
  >
  <NSwitch
    v-else-if="node.kind === 'boolean'"
    :value="node.flag"
    :disabled="disabled"
    :aria-label="label"
    @update:value="node.flag = $event"
    ><template #checked>true</template><template #unchecked>false</template></NSwitch
  >
  <span v-else-if="node.kind === 'null'" class="task-note">null</span>
  <NInput
    v-else
    :type="node.kind === 'string' ? 'textarea' : 'text'"
    :autosize="node.kind === 'string' ? { minRows: 1, maxRows: 4 } : undefined"
    :value="node.text"
    :disabled="disabled"
    :status="error ? 'error' : undefined"
    :input-props="{
      id: 'task-localJar.nodes.' + node.key + '.value',
      'aria-label': label,
      'aria-describedby': error ? 'localJar-error-' + node.key + '-value' : undefined,
    }"
    @update:value="node.text = $event"
  />
  <div
    v-if="error"
    :id="'localJar-error-' + node.key + '-value'"
    class="local-jar-error"
    role="alert"
  >
    {{ error }}
  </div>
</template>
