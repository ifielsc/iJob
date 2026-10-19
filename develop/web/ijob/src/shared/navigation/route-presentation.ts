// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { MessageKey } from '../i18n/keys'

export const routePresentation: Readonly<
  Record<string, { titleKey: MessageKey; compact: boolean }>
> = {
  foundation: { titleKey: 'foundation.title', compact: true },
  diagnostics: { titleKey: 'diagnostics.title', compact: true },
  workbench: { titleKey: 'workbench.title', compact: true },
  concurrency: { titleKey: 'concurrency.title', compact: true },
  users: { titleKey: 'users.title', compact: true },
  events: { titleKey: 'approvals.title', compact: true },
  audits: { titleKey: 'audits.title', compact: true },
  resources: { titleKey: 'config.titleResources', compact: true },
  'ssh-resources': { titleKey: 'config.titleSsh', compact: true },
  'file-resources': { titleKey: 'files.title', compact: true },
  'kafka-resources': { titleKey: 'mq.kafkaTitle', compact: true },
  'rabbit-resources': { titleKey: 'mq.rabbitTitle', compact: true },
  'monitor-resources': { titleKey: 'monitorResource.title', compact: true },
  'service-resources': { titleKey: 'apiCall.title', compact: true },
  calendars: { titleKey: 'config.titleCalendars', compact: true },
  notifications: { titleKey: 'config.titleNotifications', compact: true },
  'mail-channels': { titleKey: 'mailChannels.title', compact: true },
  tasks: { titleKey: 'tasks.title', compact: true },
  jobs: { titleKey: 'jobs.title', compact: true },
  'job-editor': { titleKey: 'jobs.title', compact: true },
  'job-edit-existing': { titleKey: 'jobs.title', compact: true },
  'job-detail': { titleKey: 'jobs.title', compact: true },
  'job-version': { titleKey: 'jobs.title', compact: true },
  'job-versions': { titleKey: 'jobs.title', compact: true },
  schedules: { titleKey: 'schedules.title', compact: true },
  parameters: { titleKey: 'parameters.title', compact: true },
  runs: { titleKey: 'runs.title', compact: true },
  'run-detail': { titleKey: 'runs.title', compact: true },
}
export function presentationForRoute(name: unknown) {
  return (
    routePresentation[String(name)] ?? { titleKey: 'foundation.title' as const, compact: false }
  )
}
