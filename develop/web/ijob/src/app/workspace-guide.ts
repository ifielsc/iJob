import type { Permission } from '../shared/api/contracts'
export interface GuideLink {
  key: string
  path: string
  permissions: Permission[]
  optional?: boolean
}
export const setupSteps: GuideLink[] = [
  { key: 'resources', path: '/resources', permissions: ['DEFINITION_READ'], optional: true },
  { key: 'tasks', path: '/tasks', permissions: ['DEFINITION_READ'] },
  { key: 'jobs', path: '/jobs', permissions: ['DEFINITION_READ'], optional: true },
  { key: 'parameters', path: '/parameters', permissions: ['DEFINITION_READ'], optional: true },
  { key: 'execute', path: '/tasks', permissions: ['DEFINITION_READ', 'EXECUTION_CONTROL'] },
  { key: 'schedules', path: '/schedules', permissions: ['DEFINITION_READ'], optional: true },
  { key: 'runs', path: '/runs', permissions: ['SUMMARY_READ'] },
]
export const reviewSteps: GuideLink[] = [
  { key: 'review', path: '/tasks', permissions: ['DEFINITION_READ'] },
  { key: 'runs', path: '/runs', permissions: ['SUMMARY_READ'] },
  { key: 'approve', path: '/events/approvals', permissions: ['APPROVAL_DECIDE'] },
  { key: 'diagnose', path: '/diagnostics', permissions: ['SUMMARY_READ'] },
]
export const resources = [
  'databases',
  'services',
  'files',
  'ssh',
  'kafka',
  'rabbitmq',
  'monitor',
] as const
type ResourceKey = (typeof resources)[number]
export const plugins: { key: string; resources: ResourceKey[] }[] = [
  { key: 'SQL', resources: ['databases'] },
  { key: 'SCRIPT', resources: ['ssh'] },
  { key: 'API_CALL', resources: ['services'] },
  { key: 'DATA_CHECK', resources: ['databases'] },
  { key: 'ETL', resources: ['databases', 'files'] },
  { key: 'BATCH_ETL', resources: ['databases'] },
  { key: 'PUSH', resources: ['databases', 'kafka', 'rabbitmq'] },
  { key: 'FILE_TRANSFER', resources: ['files'] },
  { key: 'SERVER_MONITOR', resources: ['monitor'] },
  { key: 'EVENT', resources: ['files'] },
  { key: 'LOCAL_JAR', resources: [] },
]
export const operations: GuideLink[] = [
  { key: 'parameters', path: '/parameters', permissions: ['DEFINITION_READ'] },
  { key: 'failure', path: '/runs', permissions: ['SUMMARY_READ'] },
  { key: 'retryRun', path: '/runs', permissions: ['SUMMARY_READ', 'EXECUTION_CONTROL'] },
  { key: 'approval', path: '/events/approvals', permissions: ['APPROVAL_DECIDE'] },
  { key: 'eventBinding', path: '/tasks', permissions: ['DEFINITION_READ', 'CONFIGURATION_WRITE'] },
  {
    key: 'notifyBinding',
    path: '/notifications',
    permissions: ['SENSITIVE_READ'],
  },
  { key: 'versions', path: '/tasks', permissions: ['DEFINITION_READ'] },
]
export function modulePath(key: string) {
  return key === 'overview' ? '/workbench' : `/${key}`
}
