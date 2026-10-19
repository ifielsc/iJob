// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { isSupportedPlugin, type SupportedPlugin } from '../../shared/plugins'
import { exactId, record, integer } from '../../shared/api/values'
export { exactId, record, pageData } from '../../shared/api/values'
export { terminal, runRow, logRows, type Run, type Log } from '../../shared/runtime/contracts'
import {
  localJarDraft,
  localJarConfig,
  localJarIssues,
  type LocalJarDraft,
} from './local-jar-model'
import { monitorDraft, monitorConfig, monitorIssues, type MonitorDraft } from './monitor-model'
import {
  batchEtlDraft,
  batchEtlConfig,
  batchEtlIssues,
  type BatchEtlDraft,
} from './batch-etl-model'
import { etlDraft, etlConfig, etlIssues, type EtlDraft } from './etl-model'
import { pushDraft, pushConfig, pushIssues, type PushDraft } from './push-model'
import {
  transferDraft,
  transferConfig,
  transferIssues,
  type TransferDraft,
} from './file-transfer-model'
import {
  dataCheckDraft,
  dataCheckConfig,
  dataCheckIssues,
  type DataCheckDraft,
} from './data-check-model'
import { parse, stringify, isSafeNumber } from 'lossless-json'
import { eventDraft, eventConfig, eventIssues, type EventDraft } from './event-model'
import { ApiError } from '../../shared/api/errors'
import {
  apiDraft,
  apiIssues,
  apiConfig,
  apiHeaders,
  apiStopHeaders,
  type ApiDraft,
} from './api-call-model'
import { headerBody } from '../configuration/public/contracts'
export interface TaskRow {
  headerNames?: string[]
  remoteStopHeaderNames?: string[]
  id: string
  name: string
  pluginType: string
  enabled: boolean
  currentRevision: string
  currentTaskDefinitionId: string
  updateTime: string
  groups: string[]
  pluginConfig?: string
  retryTimes: number
  retryInterval: number
  timeout: number | null
  description: string
}
export function taskRow(v: unknown, detail = false): TaskRow {
  const r = record(v)
  if (
    typeof r.name !== 'string' ||
    typeof r.pluginType !== 'string' ||
    typeof r.enabled !== 'boolean' ||
    !Array.isArray(r.groups) ||
    !r.groups.every((x) => typeof x === 'string') ||
    (detail && typeof r.pluginConfig !== 'string') ||
    (r.remoteStopHeaderNames != null &&
      (!Array.isArray(r.remoteStopHeaderNames) ||
        !r.remoteStopHeaderNames.every((v) => typeof v === 'string'))) ||
    (detail &&
      r.pluginType === 'API_CALL' &&
      (!Array.isArray(r.headerNames) || !r.headerNames.every((v) => typeof v === 'string')))
  )
    throw new ApiError('protocol')
  return {
    remoteStopHeaderNames:
      Array.isArray(r.remoteStopHeaderNames) &&
      r.remoteStopHeaderNames.every((v) => typeof v === 'string')
        ? (r.remoteStopHeaderNames as string[])
        : [],
    headerNames:
      Array.isArray(r.headerNames) && r.headerNames.every((v) => typeof v === 'string')
        ? (r.headerNames as string[])
        : [],
    id: exactId(r.id),
    name: r.name,
    pluginType: r.pluginType,
    enabled: r.enabled,
    currentRevision: exactId(r.currentRevision),
    currentTaskDefinitionId: exactId(r.currentTaskDefinitionId),
    updateTime: typeof r.updateTime === 'string' ? r.updateTime : '',
    groups: r.groups as string[],
    pluginConfig: detail ? (r.pluginConfig as string) : undefined,
    retryTimes: integer(r.retryTimes),
    retryInterval: integer(r.retryInterval),
    timeout: r.timeout == null ? null : integer(r.timeout),
    description: typeof r.description === 'string' ? r.description : '',
  }
}
export interface Draft {
  localJar: LocalJarDraft
  monitor: MonitorDraft
  batchEtl: BatchEtlDraft
  etl: EtlDraft
  push: PushDraft
  transfer: TransferDraft
  api: ApiDraft
  check: DataCheckDraft
  event: EventDraft
  pluginType: SupportedPlugin
  executionMode: 'LOCAL' | 'SSH'
  sshResourceId: string
  local: ScriptDraft
  remote: ScriptDraft
  name: string
  resourceId: string
  script: string
  enabled: boolean
  description: string
  groups: string
  delimiter: string
  transaction: boolean
  continueOnError: boolean
  queryTimeoutSeconds: number | null
  retryTimes: number | null
  retryInterval: number | null
  timeout: number | null
  minRows: number | null
  maxRows: number | null
  resultVariable: string
}
export type ScriptEngine = 'SHELL' | 'BAT' | 'POWERSHELL' | 'PYTHON'
export interface ScriptDraft {
  engine: ScriptEngine
  interpreter: string
  script: string
  workingDir: string
  scriptCharset: string
  outputCharset: string
  env: { name: string; value: string }[]
}
function scriptDraft(c: Record<string, unknown> = {}): ScriptDraft {
  if (c.engine != null && !['SHELL', 'BAT', 'POWERSHELL', 'PYTHON'].includes(String(c.engine)))
    throw new ApiError('protocol')
  const env = c.env == null ? {} : record(c.env)
  if (Object.values(env).some((v) => typeof v !== 'string')) throw new ApiError('protocol')
  const charset = (value: unknown) =>
    typeof value === 'string' && value.trim() ? value : undefined
  return {
    engine: (c.engine as ScriptEngine) ?? 'SHELL',
    interpreter: typeof c.interpreter === 'string' ? c.interpreter : 'python3',
    script: typeof c.script === 'string' ? c.script : '',
    workingDir: typeof c.workingDir === 'string' ? c.workingDir : '',
    scriptCharset: charset(c.scriptCharset) ?? charset(c.charset) ?? 'UTF-8',
    outputCharset: charset(c.outputCharset) ?? charset(c.charset) ?? 'UTF-8',
    env: Object.entries(env).map(([name, value]) => ({ name, value: value as string })),
  }
}
export function activeScript(d: Draft) {
  return d.executionMode === 'SSH' ? d.remote : d.local
}
export function sqlConfig(text: string): Record<string, unknown> {
  try {
    return record(parse(text, undefined, { parseNumber: (s) => (isSafeNumber(s) ? Number(s) : s) }))
  } catch {
    throw new ApiError('protocol')
  }
}
export function draftFor(row?: TaskRow): Draft {
  if (row && !isSupportedPlugin(row.pluginType)) throw new ApiError('protocol')
  const check = dataCheckDraft(row?.pluginType === 'DATA_CHECK' ? row.pluginConfig : undefined)
  const c =
    row &&
    ![
      'EVENT',
      'DATA_CHECK',
      'FILE_TRANSFER',
      'PUSH',
      'ETL',
      'BATCH_ETL',
      'SERVER_MONITOR',
      'LOCAL_JAR',
    ].includes(row.pluginType)
      ? sqlConfig(row.pluginConfig!)
      : {}
  const isScript = row?.pluginType === 'SCRIPT'
  if (isScript && c.executionMode != null && !['LOCAL', 'SSH'].includes(String(c.executionMode)))
    throw new ApiError('protocol')
  return {
    check,
    localJar: localJarDraft(row?.pluginType === 'LOCAL_JAR' ? row.pluginConfig : undefined),
    monitor: monitorDraft(row?.pluginType === 'SERVER_MONITOR' ? row.pluginConfig : undefined),
    batchEtl: batchEtlDraft(row?.pluginType === 'BATCH_ETL' ? row.pluginConfig : undefined),
    etl: etlDraft(row?.pluginType === 'ETL' ? row.pluginConfig : undefined),
    push: pushDraft(row?.pluginType === 'PUSH' ? row.pluginConfig : undefined),
    transfer: transferDraft(row?.pluginType === 'FILE_TRANSFER' ? row.pluginConfig : undefined),
    event: eventDraft(row?.pluginType === 'EVENT' ? row.pluginConfig : undefined),
    api: apiDraft(
      row?.pluginType === 'API_CALL' ? row.pluginConfig : undefined,
      row?.pluginType === 'API_CALL' ? row.headerNames : [],
      row?.pluginType === 'API_CALL' ? row.remoteStopHeaderNames : [],
    ),
    pluginType:
      row?.pluginType === 'LOCAL_JAR'
        ? 'LOCAL_JAR'
        : row?.pluginType === 'SERVER_MONITOR'
          ? 'SERVER_MONITOR'
          : row?.pluginType === 'BATCH_ETL'
            ? 'BATCH_ETL'
            : row?.pluginType === 'ETL'
              ? 'ETL'
              : row?.pluginType === 'PUSH'
                ? 'PUSH'
                : row?.pluginType === 'FILE_TRANSFER'
                  ? 'FILE_TRANSFER'
                  : row?.pluginType === 'DATA_CHECK'
                    ? 'DATA_CHECK'
                    : row?.pluginType === 'EVENT'
                      ? 'EVENT'
                      : row?.pluginType === 'API_CALL'
                        ? 'API_CALL'
                        : isScript
                          ? 'SCRIPT'
                          : 'SQL',
    executionMode: isScript && c.executionMode === 'SSH' ? 'SSH' : 'LOCAL',
    sshResourceId: isScript && c.sshResourceId != null ? exactId(c.sshResourceId) : '',
    local: scriptDraft(isScript && c.executionMode !== 'SSH' ? c : {}),
    remote: scriptDraft(isScript && c.executionMode === 'SSH' ? c : {}),
    name: row?.name ?? '',
    resourceId:
      row?.pluginType === 'DATA_CHECK'
        ? check.resourceId
        : c.resourceId == null
          ? ''
          : exactId(c.resourceId),
    script: typeof c.script === 'string' ? c.script : '',
    enabled: row?.enabled ?? true,
    description: row?.description ?? '',
    groups: row?.groups.join(', ') ?? '',
    delimiter: typeof c.delimiter === 'string' ? c.delimiter : ';',
    transaction: typeof c.transaction === 'boolean' ? c.transaction : true,
    continueOnError: typeof c.continueOnError === 'boolean' ? c.continueOnError : false,
    queryTimeoutSeconds:
      c.queryTimeoutSeconds === undefined ? 600 : (c.queryTimeoutSeconds as number | null),
    retryTimes: row?.retryTimes ?? 0,
    retryInterval: row?.retryInterval ?? 0,
    timeout: row?.timeout ?? null,
    minRows: c.minRows == null ? null : (c.minRows as number),
    maxRows: c.maxRows == null ? null : (c.maxRows as number),
    resultVariable: typeof c.resultVariable === 'string' ? c.resultVariable : '',
  }
}
export function issues(d: Draft): Record<string, string> {
  const e: Record<string, string> = {}
  if (!d.name.trim()) e.name = 'required'
  else if (d.name.trim().length > 128) e.name = 'tooLong'
  if (d.pluginType === 'SQL') {
    try {
      exactId(d.resourceId)
    } catch {
      e.resourceId = 'required'
    }
    if (!d.script.trim()) e.script = 'required'
    else if (d.script.length > 1000000 || d.script.includes('\0')) e.script = 'tooLong'
    if (!d.delimiter.trim() || d.delimiter.length > 16 || d.delimiter.includes('${'))
      e.delimiter = 'invalid'
  } else if (d.pluginType === 'DATA_CHECK') {
    try {
      exactId(d.resourceId)
    } catch {
      e.resourceId = 'required'
    }
    Object.assign(e, dataCheckIssues(d.check))
    if (
      !Object.keys(e).length &&
      new TextEncoder().encode(dataCheckConfig(d.check, d.resourceId)).length > 8 * 1024 * 1024
    )
      e['check.checks'] = 'configTooLarge'
  } else if (d.pluginType === 'LOCAL_JAR') {
    Object.assign(e, localJarIssues(d.localJar))
  } else if (d.pluginType === 'SERVER_MONITOR') {
    Object.assign(e, monitorIssues(d.monitor))
  } else if (d.pluginType === 'BATCH_ETL') {
    Object.assign(e, batchEtlIssues(d.batchEtl, d.retryTimes ?? 0))
  } else if (d.pluginType === 'ETL') {
    Object.assign(e, etlIssues(d.etl, d.retryTimes ?? 0))
  } else if (d.pluginType === 'PUSH') {
    Object.assign(e, pushIssues(d.push))
  } else if (d.pluginType === 'FILE_TRANSFER') {
    Object.assign(e, transferIssues(d.transfer))
  } else if (d.pluginType === 'EVENT') {
    Object.assign(e, eventIssues(d.event))
    if (['WAIT_EVENT', 'WAIT_APPROVAL'].includes(d.event.action) && d.timeout !== null)
      e.timeout = 'invalid'
  } else if (d.pluginType === 'API_CALL') {
    Object.assign(e, apiIssues(d.api))
  } else {
    const s = activeScript(d)
    if (!s.script.trim()) e.script = 'required'
    if (d.executionMode === 'SSH') {
      try {
        exactId(d.sshResourceId)
      } catch {
        e.resourceId = 'required'
      }
      if (!['SHELL', 'PYTHON'].includes(s.engine)) e.engine = 'invalid'
      if (s.workingDir.trim() && (!s.workingDir.startsWith('/') || s.workingDir.length > 4096))
        e.workingDir = 'sshDirectoryInvalid'
    }
    if (s.workingDir.includes('\0')) e.workingDir = 'invalid'
    if (s.engine === 'PYTHON' && !s.interpreter.trim()) e.interpreter = 'required'
    else if (
      s.engine === 'PYTHON' &&
      (s.interpreter.includes('\0') ||
        (d.executionMode === 'SSH' && !/^[A-Za-z0-9_./+~-]{1,512}$/.test(s.interpreter)))
    )
      e.interpreter = 'interpreterInvalid'
    for (const key of ['scriptCharset', 'outputCharset'] as const)
      if (s[key] && !/^[A-Za-z0-9][A-Za-z0-9._:+-]*$/.test(s[key])) e[key] = 'invalid'
    const names = new Set<string>()
    s.env.forEach((v, i) => {
      if (
        !v.name.trim() ||
        /[=\0]/.test(v.name) ||
        names.has(v.name) ||
        (d.executionMode === 'SSH' && !/^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(v.name))
      )
        e['env-name-' + i] = 'envNameInvalid'
      if (v.value.includes('\0')) e['env-value-' + i] = 'invalid'
      names.add(v.name)
    })
    if (new TextEncoder().encode(JSON.stringify(pluginConfig(d))).length > 8 * 1024 * 1024)
      e.script = 'configTooLarge'
  }
  const range = (key: keyof Draft, min: number, max: number, optional = false) => {
    const v = d[key]
    if (optional && v === null) return
    if (typeof v !== 'number' || !Number.isInteger(v) || v < min || v > max) e[key] = 'invalid'
  }
  range('retryTimes', 0, 100)
  range('retryInterval', 0, 3600)
  range('timeout', 1, 2147483647, true)
  if (d.pluginType === 'SQL') {
    range('queryTimeoutSeconds', 1, 86400)
    range('minRows', 0, 2147483647, true)
    range('maxRows', 0, 2147483647, true)
    if (d.minRows !== null && d.maxRows !== null && d.minRows > d.maxRows) e.minRows = 'rowRange'
    if (d.transaction && d.continueOnError) e.continueOnError = 'invalid'
    if (
      d.resultVariable &&
      (d.resultVariable.trim().length === 0 ||
        d.resultVariable.length > 128 ||
        d.resultVariable.includes('${'))
    )
      e.resultVariable = 'invalid'
  }
  if ((d.retryTimes ?? 0) * (d.retryInterval ?? 0) > 86400) e.retryInterval = 'retryRange'
  if (d.description.length > 500) e.description = 'tooLong'
  const groups = d.groups.trim() ? d.groups.split(',').map((g) => g.trim().toLowerCase()) : []
  if (new Set(groups).size > 16 || groups.some((g) => !/^[a-z0-9][a-z0-9._-]{0,63}$/.test(g)))
    e.groups = 'invalid'
  return e
}
export function taskBody(d: Draft, row?: TaskRow) {
  return {
    name: d.name.trim(),
    pluginType: d.pluginType,
    pluginConfig:
      d.pluginType === 'LOCAL_JAR'
        ? localJarConfig(d.localJar)
        : d.pluginType === 'SERVER_MONITOR'
          ? monitorConfig(d.monitor)
          : d.pluginType === 'BATCH_ETL'
            ? batchEtlConfig(d.batchEtl)
            : d.pluginType === 'ETL'
              ? etlConfig(d.etl)
              : d.pluginType === 'PUSH'
                ? pushConfig(d.push)
                : d.pluginType === 'FILE_TRANSFER'
                  ? transferConfig(d.transfer)
                  : d.pluginType === 'DATA_CHECK'
                    ? dataCheckConfig(d.check, d.resourceId)
                    : d.pluginType === 'EVENT'
                      ? eventConfig(d.event)
                      : d.pluginType === 'API_CALL'
                        ? stringify(
                            apiConfig(
                              d.api,
                              row?.pluginConfig
                                ? (parse(row.pluginConfig) as Record<string, unknown>)
                                : {},
                            ),
                          )!
                        : JSON.stringify(pluginConfig(d, row)),
    ...(d.pluginType === 'API_CALL'
      ? {
          preservedHeaderNames: headerBody(apiHeaders(d.api)).preservedHeaderNames,
          preservedRemoteStopHeaderNames: d.api.remoteStop.enabled
            ? headerBody(apiStopHeaders(d.api)).preservedHeaderNames
            : [],
          headerSourceDefinitionId: row?.currentTaskDefinitionId,
        }
      : {}),
    enabled: d.enabled,
    description: d.description,
    groups: d.groups.trim() ? d.groups.split(',').map((g) => g.trim().toLowerCase()) : [],
    retryTimes: d.retryTimes,
    retryInterval: d.retryInterval,
    timeout: d.timeout,
  }
}
function pluginConfig(d: Draft, row?: TaskRow) {
  const previous = row && row.pluginType === d.pluginType ? sqlConfig(row.pluginConfig!) : {}
  if (d.pluginType === 'SCRIPT') {
    const s = activeScript(d)
    return {
      ...previous,
      executionMode: d.executionMode,
      sshResourceId: d.executionMode === 'SSH' ? d.sshResourceId : undefined,
      engine: s.engine,
      interpreter: s.engine === 'PYTHON' ? s.interpreter : undefined,
      script: s.script,
      workingDir: s.workingDir || null,
      charset: undefined,
      scriptCharset: s.scriptCharset || 'UTF-8',
      outputCharset: s.outputCharset || 'UTF-8',
      env: Object.fromEntries(s.env.map((v) => [v.name, v.value])),
    }
  }
  return {
    ...previous,
    resourceId: d.resourceId,
    script: d.script,
    delimiter: d.delimiter,
    transaction: d.transaction,
    continueOnError: d.continueOnError,
    queryTimeoutSeconds: d.queryTimeoutSeconds,
    minRows: d.minRows,
    maxRows: d.maxRows,
    resultVariable: d.resultVariable || null,
  }
}
export function variables(text: string): Record<string, unknown> {
  if (!text.trim()) return {}
  return record(
    parse(text, undefined, {
      parseNumber: (s) => {
        if (!isSafeNumber(s)) throw new Error('unsafe number')
        return Number(s)
      },
    }),
  )
}
