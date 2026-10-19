// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

export const runGraphZh = {
  title: 'DAG 运行状态',
  hint: '按本次执行版本展示完整节点与连线；点击节点查看下方详情。',
  backRoot: '返回根作业',
  enterJob: '进入子作业',
  enterHint: '双击节点或点击右上角“进入子作业”查看子作业。',
  state_NOT_STARTED: '尚未执行',
  state_NOT_EXECUTED: '未执行',
  noInstance: '尚无运行实例',
  gatewayHint: '网关状态取最近一次判断结果',
  refreshHint: '自动刷新开启时每 2 秒更新，结束后停止',
  updated: '更新于',
  stale: '本次刷新失败，画布可能仍显示上次状态。',
}
export const runGraphEn = {
  title: 'DAG run status',
  hint: 'Complete graph from this execution version. Select a node to inspect its details below.',
  backRoot: 'Back to root job',
  enterJob: 'Enter child job',
  enterHint: 'Double-click the node or use Enter child job in the upper right.',
  state_NOT_STARTED: 'Not started',
  state_NOT_EXECUTED: 'Not executed',
  noInstance: 'No execution instance yet',
  gatewayHint: 'Gateway status reflects its latest evaluation',
  refreshHint: 'Auto-refresh updates every 2 seconds until completion',
  updated: 'Updated',
  stale: 'Refresh failed. The graph may show the last received state.',
}
