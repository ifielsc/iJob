<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { NCollapse, NCollapseItem, NInput, NSelect, NSwitch, useDialog } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FormItem from '../../shared/ui/CompactFormItem.vue'
import IntegerInput from '../../shared/ui/IntegerInput.vue'
import FileResourcePicker from './FileResourcePicker.vue'
import { archives, bundled, limits, type TransferDraft, type LimitKey } from './file-transfer-model'
const props = defineProps<{
  errors: Record<string, string>
  submitted: boolean
  disabled: boolean
}>()
const draft = defineModel<TransferDraft>('draft', { required: true })
const emit = defineEmits<{ select: [boolean] }>()
const { t } = useI18n(),
  dialog = useDialog(),
  root = ref<HTMLElement>(),
  expanded = ref<string[]>([])
const keys = Object.keys(limits) as LimitKey[]
const error = (key: string) =>
  props.submitted && props.errors['transfer.' + key]
    ? t('files.' + props.errors['transfer.' + key])
    : undefined
const selectedKeys = computed(() =>
  draft.value.mode === 'TRANSFER'
    ? (['sourceResourceId', 'targetResourceId'] as const)
    : (['sourceResourceId'] as const),
)
async function change(key: 'mode' | 'compressType', value: string) {
  if (props.disabled) return
  if (
    (key === 'mode' &&
      value === 'READ_ONLY' &&
      (draft.value.targetResourceId || draft.value.targetPath || draft.value.deleteSource)) ||
    (key === 'compressType' &&
      (draft.value.archiveName || draft.value.compressionLevel !== 'DEFAULT'))
  ) {
    const accepted = await new Promise<boolean>((resolve) =>
      dialog.warning({
        title: t('common.confirm'),
        content: t('files.changeHint'),
        positiveText: t('common.confirm'),
        negativeText: t('common.cancel'),
        onPositiveClick: () => resolve(true),
        onNegativeClick: () => resolve(false),
        onClose: () => resolve(false),
        onMaskClick: () => resolve(false),
        onEsc: () => resolve(false),
      }),
    )
    if (!accepted || props.disabled) return
  }
  draft.value[key] = value
}
async function focusError(key: string) {
  expanded.value = ['limits']
  await nextTick()
  root.value?.querySelector<HTMLElement>('[id="task-' + key + '"]')?.focus()
}
defineExpose({ focusError })
</script>
<template>
  <section ref="root" class="file-task-fields">
    <p v-if="draft.blocked" role="alert">{{ t('files.unsupported') }}</p>
    <pre v-if="draft.blocked" class="event-original">{{ draft.original }}</pre>
    <template v-else>
      <FormItem id="task-transfer.mode" :label="t('files.mode')" required
        ><NSelect
          filterable
          :value="draft.mode"
          :disabled="disabled"
          :options="
            ['TRANSFER', 'READ_ONLY'].map((value) => ({ value, label: t('files.' + value) }))
          "
          :input-props="{ id: 'task-transfer.mode' }"
          @update:value="change('mode', $event)"
          @update:show="emit('select', $event)"
      /></FormItem>
      <p v-if="draft.mode === 'READ_ONLY'" class="task-note">{{ t('files.readHint') }}</p>
      <section
        v-for="key in selectedKeys"
        :key="key"
        class="transfer-group"
        :aria-labelledby="'transfer-' + key + '-heading'"
      >
        <h3 :id="'transfer-' + key + '-heading'">
          {{ t(key === 'sourceResourceId' ? 'files.sourceGroup' : 'files.targetGroup') }}
        </h3>
        <div class="task-advanced-grid">
          <FormItem
            :id="'task-transfer.' + key"
            :label="t('files.' + key)"
            required
            :invalid="!!error(key)"
            :message="error(key)"
            ><FileResourcePicker
              :id="'task-transfer.' + key"
              v-model:value="draft[key]"
              :label="t('files.' + key)"
              :disabled="disabled"
              :invalid="!!error(key)"
              @select="emit('select', $event)"
          /></FormItem>
          <FormItem
            :id="'task-transfer.' + (key === 'sourceResourceId' ? 'sourcePath' : 'targetPath')"
            :label="t('files.' + (key === 'sourceResourceId' ? 'sourcePath' : 'targetPath'))"
            :invalid="!!error(key === 'sourceResourceId' ? 'sourcePath' : 'targetPath')"
            :message="error(key === 'sourceResourceId' ? 'sourcePath' : 'targetPath')"
            ><NInput
              v-model:value="draft[key === 'sourceResourceId' ? 'sourcePath' : 'targetPath']"
              :disabled="disabled"
              :maxlength="2048"
              :input-props="{
                id: 'task-transfer.' + (key === 'sourceResourceId' ? 'sourcePath' : 'targetPath'),
              }"
          /></FormItem>
        </div>
        <template v-if="key === 'sourceResourceId'">
          <div class="transfer-pattern-row">
            <FormItem
              id="task-transfer.filePattern"
              :label="t('files.filePattern')"
              required
              :invalid="!!error('filePattern')"
              :message="error('filePattern')"
              ><NInput
                v-model:value="draft.filePattern"
                :disabled="disabled"
                :maxlength="512"
                :input-props="{ id: 'task-transfer.filePattern' }"
            /></FormItem>
            <div class="task-inline">
              <label for="transfer-recursive">{{ t('files.recursive') }}</label
              ><NSwitch
                id="transfer-recursive"
                v-model:value="draft.recursive"
                :disabled="disabled"
              />
            </div>
          </div>
          <p class="task-note">{{ t('files.pathHint') }}</p>
        </template>
      </section>
      <section class="transfer-group" aria-labelledby="transfer-archive-heading">
        <h3 id="transfer-archive-heading">{{ t('files.archiveGroup') }}</h3>
        <div class="task-advanced-grid">
          <FormItem
            v-for="key in ['decompressType', 'compressType'] as const"
            :id="'task-transfer.' + key"
            :key="key"
            :label="t('files.' + key)"
            ><NSelect
              filterable
              :value="draft[key]"
              :disabled="disabled"
              :options="
                archives.map((value) => ({
                  value,
                  label: value === 'NONE' ? t('files.NONE') : value,
                }))
              "
              :input-props="{ id: 'task-transfer.' + key }"
              @update:value="key === 'compressType' ? change(key, $event) : (draft[key] = $event)"
              @update:show="emit('select', $event)"
          /></FormItem>
        </div>
        <div v-if="bundled(draft.compressType)" class="task-advanced-grid">
          <FormItem
            id="task-transfer.archiveName"
            :label="t('files.archiveName')"
            :invalid="!!error('archiveName')"
            :message="error('archiveName')"
            ><NInput
              v-model:value="draft.archiveName"
              :disabled="disabled"
              :maxlength="255"
              :input-props="{ id: 'task-transfer.archiveName' }"
          /></FormItem>
          <FormItem
            v-if="draft.compressType === 'ZIP'"
            id="task-transfer.compressionLevel"
            :label="t('files.compressionLevel')"
            ><NSelect
              v-model:value="draft.compressionLevel"
              filterable
              :disabled="disabled"
              :options="
                ['DEFAULT', 'FAST', 'BEST'].map((value) => ({ value, label: t('files.' + value) }))
              "
              :input-props="{ id: 'task-transfer.compressionLevel' }"
              @update:show="emit('select', $event)"
          /></FormItem>
        </div>
      </section>
      <section
        v-if="draft.mode === 'TRANSFER'"
        class="transfer-group"
        aria-labelledby="transfer-write-heading"
      >
        <h3 id="transfer-write-heading">{{ t('files.writeGroup') }}</h3>
        <FormItem id="task-transfer.remotePublishMode" :label="t('files.remotePublishMode')">
          <NSelect
            v-model:value="draft.remotePublishMode"
            filterable
            :disabled="disabled"
            :options="
              ['TEMP_RENAME', 'DIRECT'].map((value) => ({ value, label: t('files.' + value) }))
            "
            :input-props="{ id: 'task-transfer.remotePublishMode' }"
            @update:show="emit('select', $event)"
          />
        </FormItem>
        <p class="task-note">{{ t('files.publishHelp') }}</p>
        <div class="task-advanced-grid">
          <div v-for="key in ['overwrite', 'deleteSource'] as const" :key="key" class="task-inline">
            <label :for="'task-transfer.' + key">{{ t('files.' + key) }}</label
            ><NSwitch
              :id="'task-transfer.' + key"
              v-model:value="draft[key]"
              :disabled="disabled"
              :aria-invalid="!!error(key)"
            />
          </div>
        </div>
        <p v-if="error('deleteSource')" role="alert">{{ error('deleteSource') }}</p>
        <p v-if="draft.deleteSource" class="task-note">{{ t('files.deleteHint') }}</p>
      </section>
      <NCollapse v-model:expanded-names="expanded"
        ><NCollapseItem name="limits" :title="t('files.advanced')"
          ><div class="task-advanced-grid">
            <FormItem
              v-for="key in keys"
              :id="'task-transfer.' + key"
              :key="key"
              :label="t('files.' + key)"
              required
              :invalid="!!error(key)"
              :message="error(key)"
              ><IntegerInput
                :id="'task-transfer.' + key"
                v-model:value="draft.limits[key]"
                :disabled="disabled"
                :invalid="!!error(key)"
              /><small>{{ limits[key][1] }}–{{ limits[key][2] }}</small></FormItem
            >
          </div></NCollapseItem
        ></NCollapse
      >
      <p class="task-note">{{ t('files.saveTaskHint') }}</p>
    </template>
  </section>
</template>
<style scoped>
.file-task-fields {
  display: grid;
  gap: 12px;
  min-width: 0;
}
.file-task-fields small {
  color: var(--text-muted);
  font-size: 12px;
}
.file-task-fields pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.transfer-group {
  display: grid;
  gap: 12px;
  min-width: 0;
  border-top: 1px solid var(--border);
  padding-top: 14px;
}
.transfer-group h3 {
  margin: 0;
  padding-left: 9px;
  border-left: 2px solid var(--primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}
.transfer-pattern-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 12px;
}
.transfer-pattern-row .task-inline {
  min-height: 32px;
}
@media (max-width: 600px) {
  .transfer-pattern-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
