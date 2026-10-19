<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import EmptyState from '../../shared/ui/EmptyState.vue'
import { useTableOverflow } from '../../shared/ui/use-table-overflow'
import { computed, h, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { NButton, NCheckbox, NInput, useDialog, useMessage } from 'naive-ui'
import { Plus, ShieldCheck } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useRuntime, useIdentity } from '../../app/runtime'
import { can } from '../../shared/auth/access'
import AppliedFilters from '../../shared/ui/AppliedFilters.vue'
import { useListMemory } from '../../shared/ui/use-list-memory'
import RequestError from '../../shared/ui/RequestError.vue'
import PageControls from '../../shared/ui/CompactPagination.vue'
import CompactSelect from '../../shared/ui/CompactSelect.vue'
import { describeError } from '../../shared/api/errors'
import { parseAccount, parsePage, parseRoles } from '../security-contracts'
import type { Account } from '../security-contracts'
import UserEditor from './UserEditor.vue'
import UserDetails from './UserDetails.vue'
import './users.css'
const runtime = useRuntime(),
  { user } = useIdentity(),
  { t } = useI18n()
const dialog = useDialog(),
  message = useMessage(),
  deleting = ref(false)
const inspecting = ref<string>()
watch([() => runtime.session.revision, () => runtime.accessIssue.value], () => {
  inspecting.value = undefined
})
const filters = reactive({ name: '', enabled: '', roleCode: '' })
const tableScroll = ref<HTMLElement | null>(null)
const tableOverflow = useTableOverflow(tableScroll)
onBeforeUnmount(() => {
  message.destroyAll()
})
const applied = ref({ ...filters }),
  checked = ref<string[]>([])
const statusOptions = computed(() => [
  { label: t('users.all'), value: '' },
  { label: t('users.enabled'), value: 'true' },
  { label: t('users.disabled'), value: 'false' },
])
const roleOptions = computed(() => [
  { label: t('users.all'), value: '' },
  ...['ADMIN', 'OPERATOR', 'VIEWER'].map((value) => ({ value, label: t('users.' + value) })),
])
let returnFocus: HTMLElement | null = null
const page = ref(1),
  size = ref(20),
  editing = ref(false),
  selected = shallowRef<Account>()
const queryPath = computed(
  () =>
    '/api/users?' +
    new URLSearchParams({
      pageNum: String(page.value),
      pageSize: String(size.value),
      ...Object.fromEntries(Object.entries(applied.value).filter(([, value]) => value !== '')),
    }),
)
const query = useQuery(
  computed(() => ({
    staleTime: 0,
    queryKey: [
      'users',
      runtime.session.revision,
      runtime.preferences.locale,
      page.value,
      size.value,
      applied.value,
    ],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parsePage(
        await runtime.request(queryPath.value, {
          signal,
        }),
        parseAccount,
      ),
    enabled: can(user.value, 'USER_MANAGE') && !runtime.accessIssue.value,
  })),
)
const roleQuery = useQuery(
  computed(() => ({
    queryKey: ['roles', runtime.session.revision, runtime.preferences.locale],
    queryFn: async ({ signal }: { signal: AbortSignal }) =>
      parseRoles(await runtime.request('/api/roles', { signal })),
    enabled: can(user.value, 'USER_MANAGE') && !runtime.accessIssue.value,
  })),
)
watch(
  () => runtime.accessIssue.value,
  (issue) => {
    if (issue) {
      editing.value = false
      checked.value = []
      message.destroyAll()
    }
  },
)
const eligible = computed(() =>
  (query.data.value?.list ?? []).filter((row) => row.loginId !== user.value?.loginId),
)
const allChecked = computed(
  () => eligible.value.length > 0 && eligible.value.every((row) => checked.value.includes(row.id)),
)
const someChecked = computed(() => !allChecked.value && checked.value.length > 0)
watch([page, size, applied], () => {
  checked.value = []
})
watch(
  () => query.data.value,
  (value) => {
    checked.value = checked.value.filter((id) =>
      value?.list.some((row) => row.id === id && row.loginId !== user.value?.loginId),
    )
  },
)
function selectRow(id: string, value: boolean) {
  checked.value = value
    ? [...new Set([...checked.value, id])]
    : checked.value.filter((item) => item !== id)
}
async function search(reset = false) {
  if (deleting.value || runtime.accessIssue.value || query.isFetching.value) return
  const previous = JSON.stringify([applied.value, page.value])
  if (reset) Object.assign(filters, { name: '', enabled: '', roleCode: '' })
  const next = { ...filters, name: filters.name.trim() }
  if (reset || JSON.stringify(next) !== JSON.stringify(applied.value)) page.value = 1
  applied.value = next
  checked.value = []
  if (JSON.stringify([applied.value, page.value]) === previous) await query.refetch()
}
function open(account?: Account, event?: MouseEvent) {
  returnFocus = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
  selected.value = account ? { ...account } : undefined
  editing.value = true
}
async function closeEditor() {
  editing.value = false
  await nextTick()
  if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
}
function setSize(value: number) {
  size.value = value
  page.value = 1
}
async function onSaved(key: string) {
  await closeEditor()
  message.success(() => h('span', { role: 'status' }, t(key)), { duration: 3000 })
  await query.refetch()
  await runtime.refreshIdentity()
}
function remove(accounts: Account[]) {
  const targets = accounts.filter((account) => account.loginId !== user.value?.loginId)
  if (deleting.value || !targets.length) return
  const confirmation =
    targets.length === 1
      ? t('users.deleteHint', { name: targets[0]!.username })
      : t('users.batchDeleteHint', {
          count: targets.length,
          names: targets.map((row) => row.username).join('、'),
        })
  dialog.warning({
    title: t(targets.length > 1 ? 'users.batchDelete' : 'users.delete'),
    content: () => h('div', { class: 'users-delete-confirm' }, confirmation),
    positiveText: t('users.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      deleting.value = true
      const failures: { id: string; text: string }[] = []
      let removed = 0
      try {
        for (const account of targets) {
          if (runtime.accessIssue.value) break
          try {
            await runtime.request(
              '/api/users/' +
                account.id +
                '?expectedRevision=' +
                encodeURIComponent(account.revision),
              { method: 'DELETE' },
            )
            removed++
          } catch (error) {
            failures.push({
              id: account.id,
              text: account.username + '：' + describeError(error, t),
            })
          }
        }
        if (runtime.accessIssue.value) return
        if (removed)
          message.success(
            () => h('span', { role: 'status' }, t('users.deleteCount', { count: removed })),
            { duration: 3000 },
          )
        if (failures.length)
          message.error(
            () =>
              h('div', { role: 'alert', style: 'overflow-wrap:anywhere' }, [
                h('strong', t('users.deleteFailed', { count: failures.length })),
                h(
                  'ul',
                  { style: 'max-height:200px;overflow:auto;padding-left:18px' },
                  failures.map((failure) => h('li', failure.text)),
                ),
              ]),
            { duration: 0, closable: true },
          )
        if (query.data.value?.list.length === removed && page.value > 1) page.value--
        await nextTick()
        await query.refetch()
        checked.value = failures
          .map((item) => item.id)
          .filter((id) => query.data.value?.list.some((row) => row.id === id))
      } finally {
        deleting.value = false
      }
    },
  })
}
const { removeFilter } = useListMemory(filters, applied, page, size, query.isFetching)
</script>
<template>
  <div class="page-heading list-heading">
    <div>
      <h1>{{ t('users.title') }}</h1>
      <p class="subtitle">{{ t('users.subtitle') }}</p>
    </div>
    <NButton
      class="users-add list-create-action"
      type="primary"
      :disabled="!!runtime.accessIssue.value || deleting"
      @click="open(undefined, $event)"
      ><template #icon><Plus :size="18" /></template>{{ t('users.create') }}</NButton
    >
  </div>
  <section
    class="panel glass data-panel users-list-panel list-panel"
    :aria-label="t('users.accounts')"
  >
    <form class="users-filters list-filters" @submit.prevent="search()">
      <div class="users-filter users-name-filter">
        <label for="users-name">{{ t('users.keyword') }}</label
        ><NInput
          v-model:value="filters.name"
          :maxlength="128"
          :placeholder="t('users.keywordHint')"
          :disabled="deleting"
          :input-props="{ id: 'users-name' }"
        />
      </div>
      <div class="users-filter">
        <label for="users-status">{{ t('users.status') }}</label
        ><CompactSelect
          id="users-status"
          v-model:value="filters.enabled"
          :label="t('users.status')"
          :options="statusOptions"
          :disabled="deleting"
        />
      </div>
      <div class="users-filter">
        <label for="users-filter-role">{{ t('users.role') }}</label
        ><CompactSelect
          id="users-filter-role"
          v-model:value="filters.roleCode"
          :label="t('users.role')"
          :options="roleOptions"
          :disabled="deleting"
        />
      </div>
      <div class="users-filter-buttons">
        <NButton
          class="list-query-action"
          type="primary"
          attr-type="submit"
          :disabled="deleting || query.isFetching.value"
          >{{ t('users.search') }}</NButton
        ><NButton class="list-query-action" :disabled="deleting" @click="search(true)">{{
          t('users.resetFilters')
        }}</NButton>
      </div>
    </form>
    <div class="users-list-toolbar list-toolbar">
      <div class="users-bulk-actions">
        <NButton
          size="small"
          class="list-batch-action"
          type="error"
          :disabled="!checked.length || deleting || query.isFetching.value"
          :loading="deleting"
          @click="remove((query.data.value?.list ?? []).filter((row) => checked.includes(row.id)))"
          >{{ t('users.batchDelete') }}</NButton
        ><span v-if="checked.length" class="users-selection-count">{{
          t('users.selectedCount', { count: checked.length })
        }}</span>
      </div>
    </div>
    <AppliedFilters
      :values="applied"
      :busy="query.isFetching.value"
      @remove="removeFilter"
      @clear="search(true)"
    /><RequestError :error="query.error.value" />
    <p v-if="query.isPending.value && !runtime.accessIssue.value" role="status">
      {{ t('common.loading') }}
    </p>
    <template v-else-if="query.data.value && !query.isError.value"
      ><div
        ref="tableScroll"
        class="table-scroll users-table-scroll"
        :class="{ 'has-overflow': tableOverflow }"
        tabindex="0"
        :aria-label="t('users.accounts')"
      >
        <table class="users-table">
          <thead>
            <tr>
              <th class="users-check">
                <NCheckbox
                  :checked="allChecked"
                  :indeterminate="someChecked"
                  :disabled="!eligible.length || deleting || query.isFetching.value"
                  :aria-disabled="!eligible.length || deleting || query.isFetching.value"
                  :aria-label="t('users.selectPage')"
                  @update:checked="checked = $event ? eligible.map((row) => row.id) : []"
                />
              </th>
              <th>{{ t('auth.username') }}</th>
              <th>{{ t('users.displayName') }}</th>
              <th>{{ t('users.role') }}</th>
              <th>{{ t('users.status') }}</th>
              <th class="users-actions-cell">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in query.data.value.list"
              :key="row.id"
              :class="{ 'is-selected': checked.includes(row.id) }"
            >
              <td class="users-check">
                <NCheckbox
                  :checked="checked.includes(row.id)"
                  :disabled="row.loginId === user?.loginId || deleting || query.isFetching.value"
                  :aria-disabled="
                    row.loginId === user?.loginId || deleting || query.isFetching.value
                  "
                  :aria-label="t('users.selectUser', { name: row.username })"
                  @update:checked="selectRow(row.id, $event)"
                />
              </td>
              <td>
                <NButton text type="primary" @click="inspecting = row.id">{{
                  row.username
                }}</NButton
                ><small v-if="row.mustChangePassword" class="cell-note">{{
                  t('users.firstChange')
                }}</small>
              </td>
              <td>{{ row.displayName }}</td>
              <td>
                <span class="role-chip">{{ t(`users.${row.roleCode}`) }}</span>
              </td>
              <td>
                <span :class="row.enabled ? 'status-success' : 'status-disabled'">
                  {{ t(row.enabled ? 'users.enabled' : 'users.disabled') }}</span
                >
              </td>
              <td class="users-actions-cell">
                <div class="user-row-actions">
                  <NButton size="small" text type="primary" @click="inspecting = row.id">{{
                    t('common.details')
                  }}</NButton>
                  <NButton
                    size="small"
                    text
                    type="primary"
                    :aria-label="`${t('common.edit')} ${row.username}`"
                    :disabled="deleting"
                    @click="open(row, $event)"
                    >{{ t('common.edit') }}</NButton
                  >
                  <NButton
                    size="small"
                    text
                    type="error"
                    :aria-label="`${t('users.delete')} ${row.username}`"
                    :disabled="deleting || row.loginId === user?.loginId"
                    @click="remove([row])"
                    >{{ t('users.delete') }}</NButton
                  >
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-if="!query.data.value.list.length"
        :message="
          t(Object.values(applied).some(Boolean) ? 'experience.noMatch' : 'common.empty')
        " />
      <PageControls
        :page="page"
        :size="size"
        :total="query.data.value.total"
        :busy="query.isFetching.value || deleting"
        @page="page = $event"
        @size="setSize"
    /></template>
  </section>
  <UserDetails v-if="inspecting" :id="inspecting" @close="inspecting = undefined" />
  <section class="panel glass roles-panel">
    <div class="section-heading">
      <h2><ShieldCheck :size="20" aria-hidden="true" /> {{ t('users.roles') }}</h2>
    </div>
    <RequestError :error="roleQuery.error.value" /><NButton
      v-if="roleQuery.isError.value"
      @click="roleQuery.refetch()"
      >{{ t('common.retry') }}</NButton
    >
    <p v-if="roleQuery.isPending.value && !runtime.accessIssue.value" role="status">
      {{ t('common.loading') }}
    </p>
    <div v-else-if="!roleQuery.isError.value" class="table-scroll">
      <table class="roles-table">
        <thead>
          <tr>
            <th>{{ t('users.role') }}</th>
            <th>{{ t('users.permissions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="role in roleQuery.data.value" :key="role.roleCode">
            <td>{{ t(`users.${role.roleCode}`) }}</td>
            <td>
              <ul class="permission-list">
                <li v-for="permission in role.permissions" :key="permission">
                  {{ t(`permission.${permission}`) }}
                </li>
              </ul>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p>{{ t('users.rolesHint') }}</p>
  </section>
  <UserEditor
    v-if="editing"
    :account="selected"
    :page="page"
    :size="size"
    :query-path="queryPath"
    @close="closeEditor"
    @saved="onSaved"
  />
</template>
<style scoped>
.user-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}
</style>
