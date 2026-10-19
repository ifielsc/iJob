// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { createRouter, createWebHistory } from 'vue-router'
import type { Router } from 'vue-router'
import type { Permission } from '../shared/api/contracts'
import { accessDecision } from '../shared/auth/access'
import { queryClient } from '../shared/api/query'
import type { Runtime } from './runtime'

declare module 'vue-router' {
  interface RouteMeta {
    permission?: Permission
    protected?: boolean
    standalone?: boolean
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/parameters',
      name: 'parameters',
      component: () => import('../modules/parameters/ParameterTemplatesView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/diagnostics',
      name: 'diagnostics',
      component: () => import('../modules/diagnostics/DiagnosticsView.vue'),
      meta: { permission: 'SUMMARY_READ' },
    },
    {
      path: '/workbench',
      name: 'workbench',
      component: () => import('../modules/workbench/WorkbenchView.vue'),
      meta: { permission: 'SUMMARY_READ' },
    },
    {
      path: '/concurrency',
      name: 'concurrency',
      component: () => import('../modules/concurrency/ConcurrencyView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/events/approvals',
      alias: '/events',
      name: 'events',
      component: () => import('../modules/approvals/ApprovalsView.vue'),
      meta: { permission: 'APPROVAL_DECIDE' },
    },
    {
      path: '/jobs',
      name: 'jobs',
      component: () => import('../modules/jobs/JobsView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/jobs/new',
      name: 'job-editor',
      component: () => import('../modules/jobs/JobEditor.vue'),
      meta: { permission: 'CONFIGURATION_WRITE' },
    },
    {
      path: '/jobs/:id([1-9]\\d*)/edit',
      name: 'job-edit-existing',
      component: () => import('../modules/jobs/JobEditor.vue'),
      meta: { permission: 'CONFIGURATION_WRITE' },
    },
    {
      path: '/jobs/:id([1-9]\\d*)',
      name: 'job-detail',
      component: () => import('../modules/jobs/JobEditor.vue'),
      meta: { permission: 'SENSITIVE_READ' },
    },
    {
      path: '/jobs/:id([1-9]\\d*)/versions',
      name: 'job-versions',
      component: () => import('../modules/jobs/JobVersions.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/jobs/:id([1-9]\\d*)/versions/:definitionId([1-9]\\d*)',
      name: 'job-version',
      component: () => import('../modules/jobs/JobEditor.vue'),
      meta: { permission: 'SENSITIVE_READ' },
    },
    {
      path: '/schedules',
      name: 'schedules',
      component: () => import('../modules/schedules/SchedulesView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/runs',
      name: 'runs',
      component: () => import('../modules/runs/RunList.vue'),
      meta: { permission: 'SUMMARY_READ' },
    },
    {
      path: '/runs/instances/:id([1-9]\\d*)',
      name: 'run-detail',
      component: () => import('../modules/runs/RunDetail.vue'),
      meta: { permission: 'SUMMARY_READ' },
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: () => import('../modules/tasks/TasksView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/resources/databases',
      alias: '/resources',
      name: 'resources',
      component: () => import('../modules/configuration/ResourcesView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/resources/services',
      name: 'service-resources',
      component: () => import('../modules/configuration/ServiceResourcesView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/resources/kafka',
      name: 'kafka-resources',
      component: () => import('../modules/configuration/KafkaResourcesView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/resources/monitor',
      name: 'monitor-resources',
      component: () => import('../modules/configuration/MonitorResourcesView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/resources/rabbitmq',
      name: 'rabbit-resources',
      component: () => import('../modules/configuration/RabbitResourcesView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/resources/files',
      name: 'file-resources',
      component: () => import('../modules/configuration/FileResourcesView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/resources/ssh',
      name: 'ssh-resources',
      component: () => import('../modules/configuration/SshResourcesView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/calendars',
      name: 'calendars',
      component: () => import('../modules/configuration/CalendarsView.vue'),
      meta: { permission: 'DEFINITION_READ' },
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('../modules/configuration/NotificationsView.vue'),
      meta: { permission: 'SENSITIVE_READ' },
    },
    {
      path: '/notifications/mail-channels',
      name: 'mail-channels',
      component: () => import('../modules/configuration/MailChannelsView.vue'),
      meta: { permission: 'SENSITIVE_READ' },
    },
    { path: '/', name: 'foundation', component: () => import('./views/FoundationView.vue') },
    {
      path: '/login',
      name: 'login',
      component: () => import('../modules/auth/LoginView.vue'),
      meta: { standalone: true },
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('../modules/auth/PasswordView.vue'),
      meta: { protected: true, standalone: true },
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('../modules/users/UsersView.vue'),
      meta: { permission: 'USER_MANAGE' },
    },
    {
      path: '/audits',
      name: 'audits',
      component: () => import('../modules/audits/AuditsView.vue'),
      meta: { permission: 'AUDIT_READ' },
    },
    ...(import.meta.env.DEV
      ? [{ path: '/development/components', component: () => import('./views/ComponentLab.vue') }]
      : []),
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

export function installAccessGuard(target: Router, runtime: Runtime) {
  target.beforeEach(async (to) => {
    if (!to.meta.protected && !to.meta.permission) return true
    if (runtime.accessIssue.value?.kind === 'unauthorized') return { name: 'login' }
    try {
      const user = await queryClient.fetchQuery(runtime.identityOptions())
      if (to.name === 'change-password')
        return (
          user.mustChangePassword ||
          user.permissions.includes('SELF_PASSWORD_CHANGE') || {
            name: 'foundation',
            query: { access: 'forbidden' },
          }
        )
      if (user.mustChangePassword || runtime.accessIssue.value?.kind === 'passwordRequired')
        return { name: 'change-password' }
      const decision = accessDecision(user, to.meta.permission)
      return decision === 'allowed' || { name: 'foundation', query: { access: decision } }
    } catch {
      return { name: 'login' }
    }
  })
}
