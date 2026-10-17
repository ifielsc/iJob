<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { computed, nextTick, ref, shallowRef, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NCheckbox, NInput, useDialog, useMessage } from 'naive-ui'
import { Plus } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useIdentity, useRuntime } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import { describeError } from '../../shared/api/errors'
import RequestError from '../../shared/ui/RequestError.vue'
import IdentityText from '../../shared/ui/IdentityText.vue'
import PageControls from '../../shared/ui/CompactPagination.vue'
import { parsePage } from '../security-contracts'
import { parseMailChannel, type MailChannel } from './mail-channel-model'
import MailChannelEditor from './MailChannelEditor.vue'
import MailChannelDetails from './MailChannelDetails.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import './configuration.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n(),
  dialog = useDialog(),
  message = useMessage()
const allowed = computed(() => can(user.value, 'SENSITIVE_READ') && !runtime.accessIssue.value)
const writer = computed(() => can(user.value, 'CONFIGURATION_WRITE') && allowed.value)
let returnFocus: HTMLElement | null = null
const inspecting = ref(''),
  busy = ref(false),
  enabled = ref(''),
  appliedEnabled = ref('')
const name = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(20),
  editing = ref(false),
  selected = shallowRef<MailChannel>(),
  testing = ref(''),
  checked = ref<string[]>([])
const query = useQuery(
  computed(() => ({
    queryKey: [
      'mail-channels',
      applied.value,
      appliedEnabled.value,
      page.value,
      size.value,
      runtime.session.revision,
    ],
    enabled: allowed.value,
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parsePage(
        await runtime.request(
          `/api/mail-channels?name=${encodeURIComponent(applied.value)}&enabled=${appliedEnabled.value}&pageNum=${page.value}&pageSize=${size.value}`,
          { signal },
        ),
        parseMailChannel,
      ),
  })),
)
const rows = computed(() =>
  allowed.value && !query.isError.value ? (query.data.value?.list ?? []) : [],
)
const allChecked = computed(
  () => !!rows.value.length && rows.value.every((row) => checked.value.includes(row.id)),
)
watch([page, size, applied, appliedEnabled], () => {
  checked.value = []
})
watch(rows, (value) => {
  checked.value = checked.value.filter((id) => value.some((row) => row.id === id))
})
function selectRow(id: string, value: boolean) {
  checked.value = value
    ? [...new Set([...checked.value, id])]
    : checked.value.filter((item) => item !== id)
}
function remove(targets: MailChannel[], batch = false) {
  if (!writer.value || busy.value || testing.value || !targets.length) return
  const ids = targets.map((row) => row.id)
  dialog.warning({
    title: t(batch ? 'config.batchDelete' : 'config.delete'),
    content: t('mailChannels.deleteHint', { names: targets.map((row) => row.name).join('、') }),
    positiveText: t('config.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      if (!writer.value || busy.value || testing.value) return
      busy.value = true
      try {
        await runtime.request('/api/mail-channels/' + (batch ? 'batch' : ids[0]), {
          method: 'DELETE',
          ...(batch ? { body: { ids } } : {}),
        })
        checked.value = checked.value.filter((id) => !ids.includes(id))
        message.success(t('config.deleteCount', { count: ids.length }))
        if (rows.value.length === ids.length && page.value > 1) page.value--
        await nextTick()
        await query.refetch()
      } catch (error) {
        message.error(describeError(error, t))
      } finally {
        busy.value = false
      }
    },
  })
}
watch(allowed, (value) => {
  if (!value) {
    selected.value = undefined
    editing.value = false
    inspecting.value = ''
    checked.value = []
  }
})
function remember(event?: MouseEvent) {
  returnFocus = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
}
async function open(row?: MailChannel, event?: MouseEvent) {
  if (!writer.value || busy.value) return
  remember(event)
  busy.value = true
  try {
    const detail = row
      ? parseMailChannel(await runtime.request('/api/mail-channels/' + row.id))
      : undefined
    if (!allowed.value) return
    selected.value = detail
    editing.value = true
  } catch (error) {
    message.error(describeError(error, t))
  } finally {
    busy.value = false
  }
}
function inspect(row: MailChannel, event: MouseEvent) {
  remember(event)
  inspecting.value = row.id
}
async function search(reset = false) {
  if (busy.value || testing.value) return
  if (reset) {
    name.value = ''
    enabled.value = ''
  }
  const same =
    applied.value === name.value.trim() &&
    appliedEnabled.value === enabled.value &&
    page.value === 1
  applied.value = name.value.trim()
  appliedEnabled.value = enabled.value
  page.value = 1
  if (same) await query.refetch()
}
function resize(value: number) {
  if (busy.value || testing.value) return
  size.value = value
  page.value = 1
}
async function close() {
  editing.value = false
  inspecting.value = ''
  selected.value = undefined
  await nextTick()
  if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
}
function saved() {
  void close()
  void query.refetch()
}
function check(row: MailChannel) {
  if (testing.value || busy.value || !writer.value || !row.enabled) return
  dialog.warning({
    title: t('mailChannels.testTitle'),
    content: row.name + '：' + t('mailChannels.testHint'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      if (testing.value) return
      testing.value = row.id
      try {
        const result = (await runtime.request('/api/mail-channels/' + row.id + '/test-connection', {
          method: 'POST',
        })) as { connected: boolean; failureCode?: string }
        if (result.connected) message.success(t('mailChannels.connected'))
        else
          message.error(
            t(
              'mailChannels.' +
                (result.failureCode === 'AUTHENTICATION_FAILED'
                  ? 'AUTHENTICATION_FAILED'
                  : 'CONNECTION_FAILED'),
            ),
          )
      } catch (error) {
        message.error(describeError(error, t))
      } finally {
        testing.value = ''
      }
    },
  })
}
</script>
<template>
  <MailChannelDetails v-if="inspecting && allowed" :id="inspecting" @close="close" />
  <div class="page-heading list-heading">
    <div>
      <h1>{{ t('mailChannels.title') }}</h1>
      <p class="subtitle">{{ t('mailChannels.subtitle') }}</p>
    </div>
    <NButton
      v-if="writer"
      class="notification-create list-create-action"
      type="primary"
      :disabled="busy"
      @click="open(undefined, $event)"
      ><template #icon><Plus :size="16" /></template>{{ t('mailChannels.create') }}</NButton
    >
  </div>
  <section v-if="allowed" class="panel glass notification-list-panel list-panel">
    <form class="configuration-filter notification-filter list-filters" @submit.prevent="search()">
      <div class="filter-item">
        <label for="mail-filter">{{ t('mailChannels.name') }}</label
        ><NInput v-model:value="name" :input-props="{ id: 'mail-filter' }" :maxlength="128" />
      </div>
      <div class="filter-item">
        <label for="mail-filter-enabled">{{ t('config.status') }}</label
        ><CompactSelect
          id="mail-filter-enabled"
          v-model:value="enabled"
          :label="t('config.status')"
          :options="[
            { value: '', label: t('config.all') },
            { value: 'true', label: t('config.enabled') },
            { value: 'false', label: t('config.disabled') },
          ]"
        />
      </div>
      <div class="list-query-actions">
        <NButton
          class="list-query-action"
          attr-type="submit"
          type="primary"
          :loading="query.isFetching.value"
          >{{ t('config.search') }}</NButton
        ><NButton class="list-query-action" @click="search(true)">{{
          t('config.resetFilters')
        }}</NButton>
      </div>
    </form>
    <div v-if="writer" class="notification-toolbar list-toolbar">
      <div>
        <NButton
          size="small"
          class="list-batch-action"
          type="error"
          :disabled="
            busy || !!testing || query.isFetching.value || query.isError.value || !checked.length
          "
          @click="
            remove(
              rows.filter((row) => checked.includes(row.id)),
              true,
            )
          "
          >{{ t('config.batchDelete') }}</NButton
        >
        <span>{{ t('config.selectedCount', { count: checked.length }) }}</span>
      </div>
    </div>
    <RequestError :error="query.error.value" />
    <p v-if="query.isPending.value" role="status">{{ t('common.loading') }}</p>
    <div v-else-if="allowed && !query.isError.value" class="table-scroll notification-table-scroll">
      <table class="configuration-table notification-table mail-channel-table">
        <thead>
          <tr>
            <th v-if="writer" class="notification-check">
              <NCheckbox
                :checked="allChecked"
                :indeterminate="!allChecked && !!checked.length"
                :disabled="busy || !!testing || query.isFetching.value || !rows.length"
                :aria-label="t('config.selectPage')"
                @update:checked="checked = $event ? rows.map((row) => row.id) : []"
              />
            </th>
            <th class="mail-name">{{ t('mailChannels.name') }}</th>
            <th class="mail-from">{{ t('mailChannels.from') }}</th>
            <th class="mail-host">{{ t('mailChannels.host') }}</th>
            <th class="notification-status">{{ t('config.status') }}</th>
            <th class="notification-actions-cell">{{ t('common.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in rows"
            :key="row.id"
            :class="{ 'is-selected': checked.includes(row.id) }"
          >
            <td v-if="writer" class="notification-check">
              <NCheckbox
                :checked="checked.includes(row.id)"
                :disabled="busy || !!testing || query.isFetching.value"
                :aria-label="t('mailChannels.select', { name: row.name })"
                @update:checked="selectRow(row.id, $event)"
              />
            </td>
            <td class="resource-identity">
              <NButton text type="primary" @click="inspect(row, $event)">{{ row.name }}</NButton
              ><IdentityText :value="row.id" />
            </td>
            <td>{{ row.from }}</td>
            <td>{{ row.host }}:{{ row.port }}</td>
            <td>
              <span :class="row.enabled ? 'status-success' : 'status-disabled'">
                {{ t(row.enabled ? 'config.enabled' : 'config.disabled') }}</span
              >
            </td>
            <td class="notification-actions-cell">
              <div class="configuration-actions">
                <NButton
                  text
                  type="primary"
                  :disabled="busy || !!testing"
                  :aria-label="t(writer ? 'common.edit' : 'common.details') + ' ' + row.name"
                  @click="writer ? open(row, $event) : inspect(row, $event)"
                  >{{ t(writer ? 'common.edit' : 'common.details') }}</NButton
                ><NButton
                  v-if="writer"
                  text
                  :disabled="busy || !!testing || !row.enabled"
                  :loading="testing === row.id"
                  @click="check(row)"
                  >{{ t('mailChannels.test') }}</NButton
                >
                <NButton
                  v-if="writer"
                  text
                  type="error"
                  :disabled="busy || !!testing"
                  :aria-label="t('config.delete') + ' ' + row.name"
                  @click="remove([row])"
                  >{{ t('config.delete') }}</NButton
                >
              </div>
            </td>
          </tr>
          <tr v-if="!rows.length && !query.isFetching.value && !query.isError.value">
            <td :colspan="writer ? 6 : 5" class="list-empty-cell">
              <EmptyState :message="t('common.empty')" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <PageControls
      :page="page"
      :size="size"
      :total="query.data.value?.total ?? 0"
      :busy="busy || !!testing || query.isFetching.value"
      @page="page = $event"
      @size="resize"
    />
  </section>
  <MailChannelEditor
    v-if="editing && allowed"
    :key="selected?.id ?? 'new'"
    :row="selected"
    :readonly="!writer"
    @close="close"
    @saved="saved"
  />
</template>
<style scoped>
.compact-shell .mail-channel-table {
  min-width: 940px;
}
.mail-channel-table .mail-name {
  width: 220px;
}
.mail-channel-table .mail-from {
  width: 22%;
}
.mail-channel-table .mail-host {
  width: 21%;
}
.compact-shell .mail-channel-table .notification-status {
  width: 80px;
}
.compact-shell .mail-channel-table .notification-actions-cell {
  width: 190px;
}
.mail-channel-table td {
  overflow-wrap: anywhere;
}
.mail-channel-table :deep(.identity-text) {
  white-space: nowrap;
}
@media (max-width: 600px) {
  .compact-shell .mail-channel-table .notification-actions-cell {
    position: static;
  }
}
</style>
