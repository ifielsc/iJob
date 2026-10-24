// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
import { actionTotals, displayOrder, type Plan } from './model'

function escape(value: string) {
  return value.replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!,
  )
}

/** Only public outcome metadata is included; configuration and credential drafts are excluded. */
export function htmlReport(
  plan: Plan,
  options: {
    title: string
    locale: string
    timezone: string
    label: (key: string) => string
    time: (value?: string | null) => string
    issue: (code: string) => string
  },
) {
  const label = (key: string) => escape(options.label(key)),
    text = escape
  const metadata = [
    [label('recordId'), text(plan.id)],
    [label('operator'), text(plan.actor ?? '—')],
    [label('createdAt'), text(options.time(plan.createdAt))],
    [label('startedAt'), text(options.time(plan.startedAt))],
    [label('completedAt'), text(options.time(plan.completedAt))],
    [label('displayZone'), text(options.timezone)],
    [label('status'), label('states.' + plan.status)],
  ]
  const counts = actionTotals(plan.steps)
    .map((s) => `<span>${label('states.' + s.action)} <b>${s.count}</b></span>`)
    .join('')
  const rows = displayOrder(plan.steps)
    .map(
      (s) =>
        `<tr><td>${label('kinds.' + s.kind)}</td><td>${s.name === s.kind ? label('kinds.' + s.kind) : text(s.name)}</td><td>${label('states.' + s.action)}</td><td>${label('states.' + s.status)}</td><td>${text(s.targetId ?? '—')}</td><td>${s.issues.map((i) => text(options.issue(i))).join('<br>') || '—'}</td></tr>`,
    )
    .join('')
  return `<!doctype html><html lang="${text(options.locale)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"><title>${text(options.title)}</title><style>body{font:14px/1.6 system-ui,sans-serif;color:#172033;background:#fff;margin:32px auto;padding:0 20px;max-width:1100px}h1{font-size:24px}dl{display:grid;grid-template-columns:160px 1fr;gap:6px}dt{font-weight:600}dd{margin:0;overflow-wrap:anywhere}.counts{display:flex;gap:24px;flex-wrap:wrap;padding:16px;background:#eef4ff}.table{overflow:auto}table{border-collapse:collapse;width:100%;min-width:600px}th,td{border:1px solid #c8d1df;padding:8px;text-align:left;overflow-wrap:anywhere}th{background:#f3f6fa}small{color:#46536a}@media print{body{margin:0;max-width:none;padding:0}.table{overflow:visible}table{min-width:0}thead{display:table-header-group}tr{break-inside:avoid}}</style></head><body><h1>${text(options.title)}</h1><dl>${metadata.map(([k, v]) => `<dt>${k}</dt><dd>${v}</dd>`).join('')}</dl><p>${label('reportNote')}</p><div class="counts">${counts}</div><h2>${label('result')}</h2><div class="table"><table><thead><tr>${['type', 'name', 'action', 'status', 'target', 'issuesTitle'].map((k) => `<th>${label(k)}</th>`).join('')}</tr></thead><tbody>${rows}</tbody></table></div><p><small>${label('disabled')}</small></p></body></html>`
}
