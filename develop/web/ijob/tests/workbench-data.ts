// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

export function workbenchData() {
  return {
    from: '2026-09-27T10:00:00',
    to: '2026-09-28T10:00:00',
    queriedAt: '2026-09-28T10:00:01',
    timeZone: 'UTC',
    businessZone: 'Asia/Shanghai',
    total: '128',
    statuses: {
      SUCCESS: '112',
      FAILED: '6',
      TIMED_OUT: '2',
      STOPPED: '3',
      SKIPPED: '1',
      WAITING: '2',
      RUNNING: '1',
      WAITING_EVENT: '1',
      PAUSED: '0',
    } as Record<string, string>,
    successRateDenominator: '120',
    successRatePercent: '93.33' as string | null,
    currentActiveRoots: { RUNNING: '3', WAITING: '4', PAUSED: '1', WAITING_EVENT: '2' },
    approvalsVisible: true,
    pendingApprovals: '2' as string | null,
    recentFailures: [
      {
        id: '9007199254740993',
        targetId: '9007199254740995',
        targetType: 'TASK',
        name: '库存校验',
        status: 'FAILED',
        createTime: '2026-09-28T09:42:00',
      },
    ],
    node: {
      mode: 'STANDALONE',
      roles: ['API'],
      workerRoleConfigured: false,
      schedulerRoleConfigured: false,
      scheduleEnabled: false,
      clusterWorkerHealth: 'UNKNOWN',
    },
  }
}
