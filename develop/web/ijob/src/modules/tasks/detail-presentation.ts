import { isLosslessNumber } from 'lossless-json'
import { detailSections, pluginDetailSections } from './detail-sections'
import { eventActionFields, type EventAction } from './event-model'

export function detailPresentation(plugin: string, value: unknown) {
  const object =
    value !== null && typeof value === 'object' && !Array.isArray(value) && !isLosslessNumber(value)
      ? (value as Record<string, unknown>)
      : undefined
  const all = detailSections(plugin, value)
  const versionKeys =
    plugin === 'LOCAL_JAR'
      ? ['formatVersion']
      : ['ETL', 'BATCH_ETL'].includes(plugin)
        ? ['version']
        : []
  const other =
    all.find((section) => section.title === 'details.otherConfiguration')?.value ??
    (pluginDetailSections[plugin] ? {} : (object ?? {}))
  const metadata = Object.fromEntries(
    Object.entries(other).filter(([key]) => versionKeys.includes(key)),
  )
  const extensions = Object.fromEntries(
    Object.entries(other).filter(([key]) => !versionKeys.includes(key)),
  )
  const sections = all.filter((section) => section.title !== 'details.otherConfiguration')
  if (
    plugin === 'BATCH_ETL' &&
    object &&
    Array.isArray(object.tables) &&
    object.tables.every((v) => typeof v === 'string')
  ) {
    const naming = object.targetNaming as { prefix?: unknown; suffix?: unknown } | undefined
    const section = sections.find((s) => Object.hasOwn(s.value, 'tables'))
    if (section)
      section.value = {
        ...section.value,
        tables: object.tables.map((name) => ({
          sourceTable: name,
          targetTable:
            (typeof naming?.prefix === 'string' ? naming.prefix : '') +
            name +
            (typeof naming?.suffix === 'string' ? naming.suffix : ''),
        })),
      }
  }
  if (
    plugin === 'ETL' &&
    ['KAFKA', 'RABBITMQ'].includes(
      String((object?.source as Record<string, unknown> | undefined)?.type),
    )
  ) {
    const section = sections.find((s) => s.title === 'etl.tab_incremental')
    if (section) section.title = 'etl.messageTab'
  }
  const missing = object
    ? (pluginDetailSections[plugin] ?? [])
        .flatMap((section) => section.keys)
        .filter((key) => !Object.hasOwn(object, key) && !['headers', 'charset'].includes(key))
        .filter((key) => applicable(plugin, key, object))
    : []
  return { object, sections, metadata, extensions, missing }
}

// Only suppress absent fields when the saved mode explicitly makes them inapplicable.
// Do not infer current defaults for older versions, or hide any saved field.
function applicable(plugin: string, key: string, value: Record<string, unknown>) {
  if (plugin === 'ETL') {
    const source = value.source as Record<string, unknown> | undefined
    const message = ['KAFKA', 'RABBITMQ'].includes(String(source?.type))
    if (key === 'message' && ['DATABASE', 'FILE'].includes(String(source?.type))) return false
    if (message && ['incremental', 'allowNonIdempotentRetry'].includes(key)) return false
  }
  if (plugin === 'SCRIPT') {
    if (key === 'sshResourceId' && value.executionMode === 'LOCAL') return false
    if (key === 'interpreter' && ['SHELL', 'BAT', 'POWERSHELL'].includes(String(value.engine)))
      return false
  }
  if (
    plugin === 'EVENT' &&
    typeof value.action === 'string' &&
    Object.hasOwn(eventActionFields, value.action)
  )
    return key === 'action' || eventActionFields[value.action as EventAction].includes(key)
  if (plugin === 'FILE_TRANSFER') {
    if (
      value.mode === 'READ_ONLY' &&
      ['targetResourceId', 'targetPath', 'remotePublishMode', 'overwrite', 'deleteSource'].includes(
        key,
      )
    )
      return false
    if (key === 'archiveName' && ['NONE', 'GZIP'].includes(String(value.compressType))) return false
    if (
      key === 'compressionLevel' &&
      ['NONE', 'GZIP', 'TAR', 'TAR_GZ'].includes(String(value.compressType))
    )
      return false
  }
  if (plugin === 'BATCH_ETL') {
    if (
      [
        'fetchSize',
        'writeBatchSize',
        'queryTimeoutSeconds',
        'maxTables',
        'maxRowsPerTable',
        'maxTotalRows',
      ].includes(key)
    )
      return false
    if (key === 'incremental' && value.extractMode === 'FULL') return false
    if (
      ['clearMode', 'acknowledgeNonTransactionalTruncate'].includes(key) &&
      value.clearBeforeImport === false
    )
      return false
  }
  return true
}
