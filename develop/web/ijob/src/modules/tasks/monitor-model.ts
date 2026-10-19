// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { LosslessNumber, parse, stringify } from 'lossless-json'
import { pushId } from './push-model'
export const collectTypes = ['CPU', 'MEMORY', 'DISK', 'NETWORK', 'APPLICATION'] as const
export const matchTypes = ['PROCESS_NAME', 'COMMAND', 'PID', 'REGEX'] as const
type Obj = Record<string, unknown>
const object = (v: unknown): Obj => {
  if (!v || typeof v !== 'object' || Array.isArray(v) || v instanceof LosslessNumber) throw Error()
  return v as Obj
}
const optional = (v: unknown) => (v == null ? {} : object(v))
const text = (v: unknown, fallback = '') => {
  if (v == null) return fallback
  if (typeof v !== 'string') throw Error()
  return v
}
const bool = (v: unknown, fallback: boolean) => {
  if (v == null) return fallback
  if (typeof v !== 'boolean') throw Error()
  return v
}
export function monitorThreshold(v?: unknown) {
  const original = optional(v)
  const number = (v: unknown) => {
    if (v == null) return ''
    if (!(v instanceof LosslessNumber)) throw Error()
    return v.value
  }
  return { original, warning: number(original.warning), critical: number(original.critical) }
}
export type MonitorThreshold = ReturnType<typeof monitorThreshold>
export function monitorApplication() {
  return {
    name: '',
    matchType: 'PROCESS_NAME',
    matchValue: '',
    required: true,
    allowMultiple: false,
    enabled: true,
    cpuEnabled: false,
    memoryEnabled: false,
    cpu: monitorThreshold(),
    memory: monitorThreshold(),
    original: {} as Obj,
    rule: {} as Obj,
  }
}
export function monitorDraft(json?: string) {
  const d = {
    original: {} as Obj,
    blocked: false,
    resourceId: '',
    items: ['CPU'] as string[],
    metrics: Object.fromEntries(
      ['cpu', 'memory', 'disk', 'receiveRate', 'sendRate'].map((k) => [
        k,
        {
          enabled: k === 'cpu',
          ...monitorThreshold(k === 'cpu' ? parse('{"warning":80,"critical":90}') : undefined),
        },
      ]),
    ) as Record<
      'cpu' | 'memory' | 'disk' | 'receiveRate' | 'sendRate',
      MonitorThreshold & { enabled: boolean }
    >,
    paths: [] as { path: string; threshold: MonitorThreshold }[],
    applications: [] as ReturnType<typeof monitorApplication>[],
    notify: false,
    notifyConfigId: '',
  }
  if (!json) return d
  try {
    const c = object(parse(json))
    d.original = c
    d.resourceId = String(c.resourceId)
    if (!pushId(d.resourceId)) throw Error()
    const collect = object(c.collect),
      rules = object(c.rules),
      notify = optional(c.notify)
    if (
      !Array.isArray(collect.items) ||
      !collect.items.every((v) => collectTypes.includes(v as (typeof collectTypes)[number]))
    )
      throw Error()
    d.items = collect.items as string[]
    for (const k of ['cpu', 'memory', 'disk'] as const) {
      const r = optional(rules[k])
      d.metrics[k] = {
        enabled: rules[k] != null && bool(r.enabled, true),
        ...monitorThreshold(r.usage),
      }
    }
    const network = optional(rules.network)
    for (const k of ['receiveRate', 'sendRate'] as const)
      d.metrics[k] = {
        enabled: bool(network.enabled, true) && network[k] != null,
        ...monitorThreshold(network[k]),
      }
    const paths = optional(optional(rules.disk).paths)
    d.paths = Object.entries(paths).map(([path, v]) => ({ path, threshold: monitorThreshold(v) }))
    const applications = collect.applications ?? [],
      appRules = rules.applications ?? []
    if (!Array.isArray(applications) || !Array.isArray(appRules)) throw Error()
    const names = new Set<string>()
    const byName = new Map<string, Obj>()
    for (const v of appRules) {
      const r = object(v),
        name = text(r.name)
      if (byName.has(name)) throw Error()
      byName.set(name, r)
    }
    d.applications = applications.map((v) => {
      const a = object(v),
        name = text(a.name),
        r = byName.get(name),
        rule = r ?? {}
      if (names.has(name)) throw Error()
      names.add(name)
      const matchType = text(a.matchType)
      if (!matchTypes.includes(matchType as (typeof matchTypes)[number])) throw Error()
      const required = bool(a.required, true)
      if (rule.required != null && bool(rule.required, true) !== required) throw Error()
      return {
        name,
        matchType,
        matchValue: text(a.matchValue),
        required,
        allowMultiple: bool(a.allowMultiple, false),
        enabled: !!r && bool(rule.enabled, true),
        cpuEnabled: rule.cpu != null,
        memoryEnabled: rule.memory != null,
        cpu: monitorThreshold(rule.cpu),
        memory: monitorThreshold(rule.memory),
        original: a,
        rule,
      }
    })
    if ([...byName.keys()].some((k) => !names.has(k))) throw Error()
    d.notify = bool(notify.enabled, false)
    if (bool(notify.recovery, false)) throw Error()
    d.notifyConfigId = notify.notifyConfigId == null ? '' : String(notify.notifyConfigId)
    if (d.notifyConfigId && !pushId(d.notifyConfigId)) throw Error()
  } catch {
    d.blocked = true
  }
  return d
}
export type MonitorDraft = ReturnType<typeof monitorDraft>
export function monitorPath(path: string) {
  const raw = path.trim().replaceAll('\\', '/')
  let p = raw.startsWith('//')
    ? '//' + raw.slice(2).replace(/\/+/g, '/').replace(/^\/+/, '')
    : raw.replace(/\/+/g, '/')
  if (/^[a-z]:$/i.test(p)) p += '/'
  while (p.length > 1 && p.endsWith('/') && !/^[a-z]:\/$/i.test(p) && p !== '//') p = p.slice(0, -1)
  if (/^[a-z]:/i.test(p)) p = p[0]!.toUpperCase() + p.slice(1).toLowerCase()
  else if (p.startsWith('//')) p = p.toLowerCase()
  return p
}
const thresholdBody = (t: MonitorThreshold, active = true) =>
  !active
    ? { ...t.original }
    : {
        ...t.original,
        warning: t.warning === '' ? null : new LosslessNumber(t.warning),
        critical: t.critical === '' ? null : new LosslessNumber(t.critical),
      }
export function monitorConfig(d: MonitorDraft) {
  if (d.blocked) throw Error('Unsupported monitor configuration')
  const c = d.original,
    rules = optional(c.rules),
    network = optional(rules.network),
    enabled = (k: string) => d.items.includes(k)
  const result: Obj = { ...rules }
  for (const k of ['cpu', 'memory', 'disk'] as const) {
    const active = enabled(k.toUpperCase()) && d.metrics[k].enabled
    result[k] = {
      ...optional(rules[k]),
      enabled: active,
      usage: thresholdBody(d.metrics[k], active),
    }
  }
  result.disk = {
    ...object(result.disk),
    paths:
      enabled('DISK') && d.metrics.disk.enabled
        ? Object.fromEntries(d.paths.map((p) => [p.path, thresholdBody(p.threshold)]))
        : optional(rules.disk).paths,
  }
  const activeNetwork = d.metrics.receiveRate.enabled || d.metrics.sendRate.enabled
  result.network = {
    ...network,
    enabled: enabled('NETWORK') && activeNetwork,
    receiveRate:
      !enabled('NETWORK') || !activeNetwork
        ? network.receiveRate
        : d.metrics.receiveRate.enabled
          ? thresholdBody(d.metrics.receiveRate)
          : null,
    sendRate:
      !enabled('NETWORK') || !activeNetwork
        ? network.sendRate
        : d.metrics.sendRate.enabled
          ? thresholdBody(d.metrics.sendRate)
          : null,
  }
  result.applications = enabled('APPLICATION')
    ? d.applications.map((a) => ({
        ...a.rule,
        name: a.name,
        enabled: a.enabled,
        required: a.required,
        cpu: a.cpuEnabled ? thresholdBody(a.cpu, a.enabled) : null,
        memory: a.memoryEnabled ? thresholdBody(a.memory, a.enabled) : null,
      }))
    : []
  return stringify({
    ...c,
    resourceId: d.resourceId,
    collect: {
      ...optional(c.collect),
      items: d.items,
      applications: enabled('APPLICATION')
        ? d.applications.map((a) => ({
            ...a.original,
            name: a.name,
            matchType: a.matchType,
            matchValue: a.matchValue,
            required: a.required,
            allowMultiple: a.allowMultiple,
          }))
        : [],
    },
    rules: result,
    notify: {
      ...optional(c.notify),
      enabled: d.notify,
      notifyConfigId: d.notifyConfigId || null,
      recovery: false,
    },
  })!
}
export function monitorIssues(d: MonitorDraft) {
  const e: Record<string, string> = {},
    fail = (k: string, code = 'invalid') => {
      e['monitor.' + k] = code
    }
  if (d.blocked) {
    fail('resourceId', 'unsupported')
    return e
  }
  if (!pushId(d.resourceId)) fail('resourceId')
  if (!d.items.length || new Set(d.items).size !== d.items.length) fail('items', 'collectRequired')
  const check = (t: MonitorThreshold, key: string, percentage: boolean) => {
    if (!t.warning && !t.critical) fail(key + '.warning', 'thresholdRequired')
    for (const k of ['warning', 'critical'] as const)
      if (
        t[k] &&
        (!/^(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(t[k]) ||
          !Number.isFinite(Number(t[k])) ||
          Number(t[k]) < 0 ||
          (percentage && Number(t[k]) > 100))
      )
        fail(key + '.' + k)
    if (t.warning && t.critical && Number(t.warning) > Number(t.critical))
      fail(key + '.critical', 'thresholdOrder')
  }
  let enabledRules = 0
  for (const k of ['cpu', 'memory', 'disk', 'receiveRate', 'sendRate'] as const)
    if (
      d.metrics[k].enabled &&
      d.items.includes(k.endsWith('Rate') ? 'NETWORK' : k.toUpperCase())
    ) {
      check(d.metrics[k], 'metrics.' + k, !k.endsWith('Rate'))
      enabledRules++
    }
  if (d.items.includes('DISK') && d.metrics.disk.enabled) {
    if (d.paths.length > 128) fail('paths')
    const paths = new Set<string>()
    d.paths.forEach((p, i) => {
      const n = monitorPath(p.path)
      if (!p.path || p.path !== p.path.trim() || p.path.length > 512 || paths.has(n))
        fail('paths.' + i + '.path')
      paths.add(n)
      check(p.threshold, 'paths.' + i, true)
    })
  }
  if (d.items.includes('APPLICATION')) {
    if (d.applications.length > 64) fail('applications')
    const names = new Set<string>()
    d.applications.forEach((a, i) => {
      const base = 'applications.' + i
      if (!a.name || a.name !== a.name.trim() || a.name.length > 128 || names.has(a.name))
        fail(base + '.name')
      names.add(a.name)
      if (!a.matchValue.trim() || a.matchValue.length > 1024) fail(base + '.matchValue')
      if (!a.matchValue.includes('${')) {
        if (
          a.matchType === 'PID' &&
          (!/^[1-9]\d*$/.test(a.matchValue) || BigInt(a.matchValue) > 2147483647n)
        )
          fail(base + '.matchValue')
        if (a.matchType === 'REGEX' && a.matchValue.length > 256) fail(base + '.matchValue')
      }
      if (a.enabled) {
        enabledRules++
        if (!a.required && !a.cpuEnabled && !a.memoryEnabled)
          fail(base + '.enabled', 'effectiveCheck')
        if (a.cpuEnabled) check(a.cpu, base + '.cpu', true)
        if (a.memoryEnabled) check(a.memory, base + '.memory', false)
      }
    })
  }
  if (!enabledRules) fail('items', 'ruleRequired')
  if (d.notify && !pushId(d.notifyConfigId)) fail('notifyConfigId')
  if (!Object.keys(e).length && new TextEncoder().encode(monitorConfig(d)).length > 8 * 1024 * 1024)
    fail('items', 'tooLarge')
  return e
}
