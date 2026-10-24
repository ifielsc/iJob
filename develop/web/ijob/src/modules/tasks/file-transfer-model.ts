// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { parse, stringify, LosslessNumber } from 'lossless-json'
export const archives = ['NONE', 'ZIP', 'GZIP', 'TAR', 'TAR_GZ', 'SEVEN_ZIP'] as const
export const limits = {
  maxScanDepth: [32, 0, 64],
  maxScannedEntries: [10000, 1, 100000],
  maxSourceFileBytes: [268435456, 1, 1073741824],
  maxSourceTotalBytes: [1073741824, 1, 10737418240],
  maxStagingBytes: [2147483648, 1, 21474836480],
  maxArchiveEntries: [10000, 1, 100000],
  maxExtractedEntryBytes: [268435456, 1024, 1073741824],
  maxExtractedTotalBytes: [1073741824, 1024, 10737418240],
  maxCompressionRatio: [100, 1, 1000],
} as const
export type LimitKey = keyof typeof limits
export const validFileId = (s: string) => /^[1-9]\d*$/.test(s) && BigInt(s) <= 9223372036854775807n
export const bundled = (s: string) => ['ZIP', 'TAR', 'TAR_GZ', 'SEVEN_ZIP'].includes(s)
export function transferDraft(text?: string) {
  const d = {
    original: text ?? '{}',
    blocked: false,
    mode: 'TRANSFER',
    sourceResourceId: '',
    targetResourceId: '',
    sourcePath: '',
    targetPath: '',
    filePattern: '*',
    recursive: false,
    decompressType: 'NONE',
    compressType: 'NONE',
    remotePublishMode: text ? 'DIRECT' : 'TEMP_RENAME',
    overwrite: true,
    deleteSource: false,
    archiveName: '',
    compressionLevel: 'DEFAULT',
    limits: Object.fromEntries(Object.entries(limits).map(([k, v]) => [k, v[0]])) as Record<
      LimitKey,
      number | null
    >,
  }
  if (!text) return d
  try {
    const c = parse(text) as Record<string, unknown>
    if (!c || typeof c !== 'object' || Array.isArray(c)) throw Error()
    for (const key of [
      'remotePublishMode',
      'mode',
      'sourcePath',
      'targetPath',
      'filePattern',
      'decompressType',
      'compressType',
      'archiveName',
      'compressionLevel',
    ] as const) {
      if (c[key] != null) {
        if (typeof c[key] !== 'string') throw Error()
        d[key] = c[key]
      }
    }
    for (const key of ['recursive', 'overwrite', 'deleteSource'] as const)
      if (c[key] != null) {
        if (typeof c[key] !== 'boolean') throw Error()
        d[key] = c[key]
      }
    for (const key of ['sourceResourceId', 'targetResourceId'] as const)
      if (c[key] != null) {
        d[key] = String(c[key])
        if (!validFileId(d[key])) throw Error()
      }
    for (const key of Object.keys(limits) as LimitKey[])
      if (c[key] != null) {
        const n = Number(String(c[key]))
        if (!/^-?\d+$/.test(String(c[key])) || !Number.isSafeInteger(n)) throw Error()
        d.limits[key] = n
      }
    if (
      !['DIRECT', 'TEMP_RENAME'].includes(d.remotePublishMode) ||
      !['TRANSFER', 'READ_ONLY'].includes(d.mode) ||
      !archives.includes(d.compressType as (typeof archives)[number]) ||
      !archives.includes(d.decompressType as (typeof archives)[number]) ||
      !['DEFAULT', 'FAST', 'BEST'].includes(d.compressionLevel)
    )
      throw Error()
  } catch {
    d.blocked = true
  }
  return d
}
export type TransferDraft = ReturnType<typeof transferDraft>
export function transferIssues(d: TransferDraft) {
  const e: Record<string, string> = {}
  const fail = (key: string) => {
    e['transfer.' + key] = 'invalid'
  }
  if (d.blocked) {
    e['transfer.mode'] = 'unsupported'
    return e
  }
  for (const key of d.mode === 'TRANSFER'
    ? (['sourceResourceId', 'targetResourceId'] as const)
    : (['sourceResourceId'] as const))
    if (!validFileId(d[key])) fail(key)
  for (const key of ['sourcePath', 'targetPath'] as const)
    if (d[key].length > 2048 || /[\0\r\n]/.test(d[key])) fail(key)
  if (
    !d.filePattern.trim() ||
    d.filePattern.length > 512 ||
    /\//.test(d.filePattern) ||
    /\\(?![*?{}[\]])/.test(d.filePattern)
  )
    fail('filePattern')
  if (bundled(d.compressType) && (d.archiveName.length > 255 || /[/\\\0\r\n]/.test(d.archiveName)))
    fail('archiveName')
  if (d.mode === 'TRANSFER' && d.deleteSource && d.sourceResourceId === d.targetResourceId)
    fail('deleteSource')
  for (const key of Object.keys(limits) as LimitKey[]) {
    const n = d.limits[key]
    if (n == null || !Number.isInteger(n) || n < limits[key][1] || n > limits[key][2]) fail(key)
  }
  if (d.limits.maxSourceTotalBytes! < d.limits.maxSourceFileBytes!) fail('maxSourceTotalBytes')
  if (d.limits.maxExtractedTotalBytes! < d.limits.maxExtractedEntryBytes!)
    fail('maxExtractedTotalBytes')
  return e
}
export function transferConfig(d: TransferDraft) {
  if (d.blocked) throw Error('Unsupported file transfer configuration')
  return stringify({
    ...(parse(d.original) as object),
    ...d.limits,
    mode: d.mode,
    sourceResourceId: new LosslessNumber(d.sourceResourceId),
    targetResourceId: d.mode === 'TRANSFER' ? new LosslessNumber(d.targetResourceId) : undefined,
    sourcePath: d.sourcePath || null,
    targetPath: d.mode === 'TRANSFER' ? d.targetPath || null : undefined,
    filePattern: d.filePattern,
    recursive: d.recursive,
    compressType: d.compressType,
    decompressType: d.decompressType,
    remotePublishMode: d.remotePublishMode,
    overwrite: d.overwrite,
    deleteSource: d.mode === 'TRANSFER' && d.deleteSource,
    archiveName: bundled(d.compressType) ? d.archiveName || null : undefined,
    compressionLevel: d.compressType === 'ZIP' ? d.compressionLevel : 'DEFAULT',
  })!
}
