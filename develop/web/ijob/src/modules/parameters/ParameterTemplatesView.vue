<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { useLeaveGuard } from '../../shared/forms/use-leave-guard'
import DrawerCloseButton from '../../shared/ui/DrawerCloseButton.vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { stringify } from 'lossless-json'
import { useQuery } from '@tanstack/vue-query'
import {
  NButton,
  NCheckbox,
  NDrawer,
  NDrawerContent,
  NInput,
  useDialog,
  useMessage,
} from 'naive-ui'
import { Plus } from '@lucide/vue'
import Pagination from '../../shared/ui/CompactPagination.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { pageData } from '../../shared/api/values'
import { describeError, isWriteOutcomeUnknown } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import FieldDefinitions from './FieldDefinitions.vue'
import TemplateDetails from './TemplateDetails.vue'
import { copy, template, type Template, type Field } from './model'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage()
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && !runtime.accessIssue.value)
const page = ref(1),
  size = ref(20),
  search = ref(''),
  applied = ref(''),
  editing = ref(false),
  selected = ref<Template>(),
  name = ref(''),
  description = ref(''),
  fields = ref<Field[]>([]),
  busy = ref(false),
  uncertain = ref(false),
  error = ref<unknown>()
const original = ref('')
const details = ref(false)
const checked = ref<string[]>([])
const list = computed(() => query.data.value?.list ?? [])
const allChecked = computed(
  () => !!list.value.length && list.value.every((row) => checked.value.includes(row.id)),
)
const draftKey = computed(() =>
  stringify({ name: name.value, description: description.value, fields: fields.value }),
)
const changed = computed(() => draftKey.value !== original.value)
const controller = new AbortController()
onBeforeUnmount(() => controller.abort())
const query = useQuery(
  computed(() => ({
    queryKey: [
      'parameter-templates',
      runtime.session.revision,
      runtime.preferences.locale,
      page.value,
      size.value,
      applied.value,
    ],
    enabled: can(user.value, 'DEFINITION_READ') && !runtime.accessIssue.value,
    retry: false,
    staleTime: 0,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      pageData(
        await runtime.request(
          `/api/parameter-templates?pageNo=${page.value}&pageSize=${size.value}&name=${encodeURIComponent(applied.value)}`,
          { signal, lossless: true },
        ),
        template,
      ),
  })),
)
watch([page, size, applied, writer, () => query.data.value], () => {
  checked.value = []
})
function check(id: string, value: boolean) {
  checked.value = value ? [...checked.value, id] : checked.value.filter((item) => item !== id)
}
function applySearch(reset = false) {
  if (reset) search.value = ''
  if (applied.value === search.value && page.value === 1) void query.refetch()
  applied.value = search.value
  page.value = 1
}
function setSize(value: number) {
  size.value = value
  page.value = 1
}
async function edit(row?: Template, readonly = false) {
  if (busy.value) return
  if (row) {
    busy.value = true
    try {
      row = template(
        await runtime.request('/api/parameter-templates/' + row.id, {
          signal: controller.signal,
          lossless: true,
        }),
      )
    } catch (e) {
      if (!controller.signal.aborted) message.error(describeError(e, t))
      return
    } finally {
      busy.value = false
    }
  }
  if (controller.signal.aborted) return
  details.value = readonly || !writer.value
  selected.value = row
  name.value = row?.name ?? ''
  description.value = row?.description ?? ''
  fields.value = copy(row?.fields ?? [])
  error.value = undefined
  uncertain.value = false
  original.value = draftKey.value!
  editing.value = true
}
async function allowClose() {
  if (!editing.value || details.value) return true
  if (busy.value) return false
  if (!changed.value) return true
  return new Promise<boolean>((resolve) =>
    dialog.warning({
      title: t('common.discard'),
      content: t('common.discardHint'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
      onMaskClick: () => resolve(false),
      onEsc: () => resolve(false),
    }),
  )
}
useLeaveGuard(() => editing.value && !details.value && changed.value, allowClose)
async function close() {
  if (await allowClose()) editing.value = false
}
async function save() {
  if (!writer.value || busy.value || uncertain.value || !changed.value) return
  busy.value = true
  error.value = undefined
  try {
    await runtime.request(
      '/api/parameter-templates' + (selected.value ? '/' + selected.value.id : ''),
      {
        method: selected.value ? 'PUT' : 'POST',
        body: {
          name: name.value,
          description: description.value,
          fields: fields.value,
          revision: selected.value?.revision,
        },
        signal: controller.signal,
        lossless: true,
      },
    )
    editing.value = false
    await query.refetch()
  } catch (e) {
    error.value = e
    if (isWriteOutcomeUnknown(e)) uncertain.value = true
  } finally {
    busy.value = false
  }
}
function remove(rows: Template[], batch = false) {
  if (!writer.value || busy.value || !rows.length) return
  const ids = rows.map((row) => row.id)
  dialog.warning({
    title: t(batch ? 'tasks.batchDelete' : 'parameters.remove'),
    content: batch
      ? t('parameters.batchDeleteHint', { count: ids.length })
      : t('parameters.deleteHint'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      if (!writer.value || busy.value) return false
      busy.value = true
      try {
        await runtime.request('/api/parameter-templates/' + (batch ? 'batch' : ids[0]), {
          method: 'DELETE',
          body: batch ? { ids } : undefined,
          signal: controller.signal,
          lossless: true,
        })
        checked.value = []
        if (ids.length === list.value.length && page.value > 1) page.value--
        else await query.refetch()
      } catch (e) {
        message.error(describeError(e, t))
        await query.refetch()
      } finally {
        busy.value = false
      }
    },
  })
}
</script>
<template>
  <section class="templates-page">
    <header class="page-heading list-heading">
      <div>
        <h1>{{ t('parameters.title') }}</h1>
        <p class="subtitle">{{ t('parameters.subtitle') }}</p>
      </div>
      <NButton
        v-if="writer"
        class="list-create-action"
        type="primary"
        :disabled="busy"
        @click="edit()"
        ><template #icon><Plus :size="16" /></template>{{ t('parameters.new') }}</NButton
      >
    </header>
    <div class="panel glass list-panel">
      <form class="template-filters list-filters" @submit.prevent="applySearch()">
        <div>
          <label for="template-filter-name">{{ t('parameters.name') }}</label
          ><NInput
            v-model:value="search"
            :maxlength="128"
            :input-props="{ id: 'template-filter-name' }"
          />
        </div>
        <div class="template-filter-actions">
          <NButton
            class="list-query-action"
            attr-type="submit"
            type="primary"
            :loading="query.isFetching.value"
            :disabled="busy"
            >{{ t('tasks.search') }}</NButton
          ><NButton
            class="list-query-action"
            :disabled="busy || query.isFetching.value"
            @click="applySearch(true)"
            >{{ t('tasks.reset') }}</NButton
          >
        </div>
      </form>
      <div v-if="writer" class="template-batch-toolbar list-toolbar">
        <NButton
          size="small"
          class="list-batch-action"
          type="error"
          :disabled="busy || query.isFetching.value || !checked.length"
          @click="
            remove(
              list.filter((row) => checked.includes(row.id)),
              true,
            )
          "
          >{{ t('tasks.batchDelete') }}</NButton
        ><span class="parameter-note">{{ t('tasks.selected', { count: checked.length }) }}</span>
      </div>
      <RequestError :error="query.error.value" />
      <p v-if="query.isPending.value" role="status">{{ t('common.loading') }}</p>
      <template v-else-if="!query.isError.value">
        <div class="table-scroll">
          <table class="template-list-table">
            <thead>
              <tr>
                <th v-if="writer" class="template-list-check">
                  <NCheckbox
                    :checked="allChecked"
                    :indeterminate="checked.length > 0 && !allChecked"
                    :disabled="busy || query.isFetching.value || !list.length"
                    :aria-label="t('config.selectPage')"
                    @update:checked="checked = $event ? list.map((row) => row.id) : []"
                  />
                </th>
                <th>{{ t('parameters.name') }}</th>
                <th>{{ t('parameters.description') }}</th>
                <th>{{ t('parameters.list') }}</th>
                <th class="template-list-actions">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in list"
                :key="row.id"
                :class="{ 'is-selected': checked.includes(row.id) }"
              >
                <td v-if="writer" class="template-list-check">
                  <NCheckbox
                    :checked="checked.includes(row.id)"
                    :disabled="busy || query.isFetching.value"
                    :aria-label="row.name"
                    @update:checked="check(row.id, $event)"
                  />
                </td>
                <td>
                  <strong>{{ row.name }}</strong
                  ><IdentityText :value="row.id" />
                </td>
                <td>{{ row.description || '—' }}</td>
                <td>
                  <span class="template-field-names">{{
                    row.fields.map((f) => f.name).join(', ') || '—'
                  }}</span
                  ><small>{{ t('parameters.fieldCount', { count: row.fields.length }) }}</small>
                </td>
                <td class="template-list-actions">
                  <div class="parameter-actions">
                    <NButton
                      text
                      type="primary"
                      :disabled="busy"
                      :aria-label="t('common.details') + ' ' + row.name"
                      @click="edit(row, true)"
                      >{{ t('common.details') }}</NButton
                    >
                    <NButton
                      v-if="writer"
                      text
                      type="primary"
                      :disabled="busy"
                      :aria-label="t(writer ? 'common.edit' : 'common.details') + ' ' + row.name"
                      @click="edit(row)"
                      >{{ t(writer ? 'common.edit' : 'common.details') }}</NButton
                    ><NButton
                      v-if="writer"
                      text
                      type="error"
                      :disabled="busy"
                      :aria-label="t('parameters.remove') + ' ' + row.name"
                      @click="remove([row])"
                      >{{ t('parameters.remove') }}</NButton
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState v-if="!list.length" :message="t('common.empty')" />
        <Pagination
          :page="page"
          :size="size"
          :total="query.data.value?.total ?? 0"
          :busy="busy || query.isFetching.value"
          @page="page = $event"
          @size="setSize"
        />
      </template>
    </div>
    <NDrawer
      class="parameter-drawer editor-drawer"
      :show="editing"
      width="min(640px, 100vw)"
      :mask-closable="details && !busy"
      :close-on-esc="!busy"
      @update:show="close"
    >
      <NDrawerContent
        :title="t(details ? 'parameters.detail' : selected ? 'parameters.edit' : 'parameters.new')"
        :native-scrollbar="true"
        :closable="false"
        ><template #header
          ><div class="parameter-drawer-heading">
            <span>{{
              t(details ? 'parameters.detail' : selected ? 'parameters.edit' : 'parameters.new')
            }}</span
            ><DrawerCloseButton :disabled="busy" @close="close" /></div></template
        ><TemplateDetails v-if="details && selected" :template="selected" />
        <div v-else class="template-editor">
          <RequestError :error="error" />
          <div class="parameter-grid">
            <label
              >{{ t('parameters.name')
              }}<NInput
                v-model:value="name"
                :maxlength="128"
                :disabled="!writer || busy"
                :input-props="{ 'aria-label': t('parameters.name') }" /></label
            ><label
              >{{ t('parameters.description')
              }}<NInput
                v-model:value="description"
                :maxlength="500"
                :disabled="!writer || busy"
                :input-props="{ 'aria-label': t('parameters.description') }"
            /></label>
          </div>
          <FieldDefinitions v-model="fields" :disabled="!writer || busy" />
          <p v-if="fields.some((f) => f.sensitive)" class="parameter-note">
            {{ t('parameters.secretHint') }}
          </p>
        </div>
        <template #footer
          ><div class="parameter-drawer-footer">
            <span v-if="!details" class="parameter-note">{{ t('parameters.saveHint') }}</span>
            <div class="parameter-actions">
              <NButton :disabled="busy" @click="close">{{
                t(details ? 'common.close' : 'common.cancel')
              }}</NButton
              ><NButton
                v-if="writer && !details"
                type="primary"
                :loading="busy"
                :disabled="!name.trim() || busy || uncertain || !changed"
                @click="save"
                >{{ t('common.save') }}</NButton
              >
            </div>
          </div></template
        ></NDrawerContent
      ></NDrawer
    >
  </section>
</template>
<style scoped>
.parameter-drawer-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.templates-page {
  font-size: 13px;
  color: var(--text);
}
.template-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.template-filters > div:first-child {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
}
.template-filters label {
  white-space: nowrap;
  flex-shrink: 0;
}
.template-filter-actions {
  display: flex;
  gap: 8px;
}
.template-list-table {
  min-width: 650px;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
}
.template-filters .n-input {
  width: 220px;
  max-width: 100%;
}
.template-list-table .template-list-check {
  width: 40px;
  min-width: 40px;
}
.template-batch-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.template-list-table .template-list-actions {
  width: 180px;
}
.template-list-table td {
  background: var(--panel);
}
.template-list-actions {
  position: sticky;
  right: 0;
  border-left: 1px solid var(--border);
}
.template-list-table .identity-text {
  display: flex;
  margin-top: 4px;
}
.template-field-names {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
small {
  display: block;
  font-size: 12px;
  color: var(--muted);
  margin-top: 4px;
}
.template-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
@media (max-width: 640px) {
  .template-list-actions {
    position: static;
  }
}
</style>
