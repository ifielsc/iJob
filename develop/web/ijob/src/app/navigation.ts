import {
  Activity,
  Bell,
  CalendarDays,
  Clock3,
  Database,
  FileCode2,
  ChartNoAxesCombined,
  GitBranch,
  House,
  Layers3,
  ListChecks,
  ShieldCheck,
  Users,
} from '@lucide/vue'
import type { Component } from 'vue'
import type { Permission } from '../shared/api/contracts'

export interface NavigationEntry {
  key: string
  icon: Component
  permission: Permission
  system?: boolean
}
export const navigation: NavigationEntry[] = [
  { key: 'overview', icon: House, permission: 'SUMMARY_READ' },
  { key: 'tasks', icon: FileCode2, permission: 'DEFINITION_READ' },
  { key: 'jobs', icon: GitBranch, permission: 'DEFINITION_READ' },
  { key: 'parameters', icon: ListChecks, permission: 'DEFINITION_READ' },
  { key: 'schedules', icon: Clock3, permission: 'DEFINITION_READ' },
  { key: 'calendars', icon: CalendarDays, permission: 'DEFINITION_READ' },
  { key: 'runs', icon: Activity, permission: 'SUMMARY_READ' },
  { key: 'resources', icon: Database, permission: 'DEFINITION_READ' },
  { key: 'events', icon: ListChecks, permission: 'APPROVAL_DECIDE' },
  { key: 'notifications', icon: Bell, permission: 'SENSITIVE_READ' },
  { key: 'concurrency', icon: Layers3, permission: 'DEFINITION_READ' },
  { key: 'diagnostics', icon: ChartNoAxesCombined, permission: 'SUMMARY_READ' },
  { key: 'users', icon: Users, permission: 'USER_MANAGE', system: true },
  { key: 'audits', icon: ShieldCheck, permission: 'AUDIT_READ', system: true },
]
