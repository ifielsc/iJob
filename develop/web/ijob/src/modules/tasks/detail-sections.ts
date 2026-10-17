import { isLosslessNumber } from 'lossless-json'

type Section = { title: string; keys: string[] }
// Keys stay at their original configuration paths, including unknown extension fields.
export const pluginDetailSections: Record<string, Section[]> = {
  SQL: [
    { title: 'details.query', keys: ['resourceId', 'script'] },
    {
      title: 'details.execution',
      keys: ['delimiter', 'transaction', 'continueOnError', 'queryTimeoutSeconds'],
    },
    { title: 'details.response', keys: ['minRows', 'maxRows', 'resultVariable'] },
  ],
  SCRIPT: [
    {
      title: 'details.scriptConfiguration',
      keys: ['executionMode', 'sshResourceId', 'engine', 'interpreter', 'script'],
    },
    {
      title: 'details.execution',
      keys: ['workingDir', 'env', 'charset', 'scriptCharset', 'outputCharset'],
    },
  ],
  EVENT: [
    {
      title: 'details.eventConfiguration',
      keys: [
        'action',
        'eventCode',
        'businessKey',
        'correlationKey',
        'idempotencyKey',
        'occurredAt',
        'summary',
        'payload',
        'suppressSameInbound',
      ],
    },
    {
      title: 'details.execution',
      keys: ['timeoutSeconds', 'lookbackSeconds', 'allowedOperatorIds'],
    },
    {
      title: 'details.fileCondition',
      keys: [
        'fileResourceId',
        'filePath',
        'filePattern',
        'recursive',
        'minStableAgeSeconds',
        'maxMatches',
        'fileCaseSensitive',
        'maxDepth',
        'maxScannedEntries',
        'scanTimeoutSeconds',
      ],
    },
  ],
  ETL: [
    { title: 'etl.tab_source', keys: ['source'] },
    { title: 'etl.tab_rules', keys: ['transforms', 'validations'] },
    { title: 'etl.tab_target', keys: ['target'] },
    {
      title: 'etl.tab_incremental',
      keys: ['message', 'incremental', 'execution', 'allowNonIdempotentRetry'],
    },
  ],
  BATCH_ETL: [
    {
      title: 'batchEtl.tab_tables',
      keys: [
        'sourceResourceId',
        'targetResourceId',
        'sourceSchema',
        'targetSchema',
        'targetNaming',
        'tables',
      ],
    },
    {
      title: 'batchEtl.tab_write',
      keys: [
        'loadMode',
        'nullPolicy',
        'clearBeforeImport',
        'clearMode',
        'acknowledgePartialCommit',
        'acknowledgeNonTransactionalTruncate',
        'allowNonIdempotentRetry',
      ],
    },
    {
      title: 'batchEtl.tab_incremental',
      keys: [
        'execution',
        'extractMode',
        'logicalKeys',
        'incremental',
        'fetchSize',
        'writeBatchSize',
        'queryTimeoutSeconds',
        'maxTables',
        'maxRowsPerTable',
        'maxTotalRows',
      ],
    },
  ],
  SERVER_MONITOR: [
    { title: 'monitor.tab_collect', keys: ['resourceId', 'collect'] },
    { title: 'monitor.tab_rules', keys: ['rules'] },
    { title: 'monitor.tab_notify', keys: ['notify'] },
  ],
  LOCAL_JAR: [
    {
      title: 'localJar.artifact',
      keys: ['jarFile', 'sha256', 'entryClass', 'jarSize', 'implementationVersion', 'spiVersion'],
    },
    { title: 'localJar.parameters', keys: ['userConfig'] },
  ],
  FILE_TRANSFER: [
    {
      title: 'files.sourceGroup',
      keys: ['mode', 'sourceResourceId', 'sourcePath', 'filePattern', 'recursive'],
    },
    { title: 'files.targetGroup', keys: ['targetResourceId', 'targetPath'] },
    {
      title: 'files.archiveGroup',
      keys: ['decompressType', 'compressType', 'archiveName', 'compressionLevel'],
    },
    { title: 'files.writeGroup', keys: ['remotePublishMode', 'overwrite', 'deleteSource'] },
    {
      title: 'files.advanced',
      keys: [
        'maxScanDepth',
        'maxScannedEntries',
        'maxSourceFileBytes',
        'maxSourceTotalBytes',
        'maxStagingBytes',
        'maxArchiveEntries',
        'maxExtractedEntryBytes',
        'maxExtractedTotalBytes',
        'maxCompressionRatio',
      ],
    },
  ],
  PUSH: [
    { title: 'push.source', keys: ['sourceResourceId', 'sql'] },
    { title: 'push.target', keys: ['target'] },
    { title: 'push.format', keys: ['serializer', 'compression'] },
    {
      title: 'details.execution',
      keys: [
        'fetchSize',
        'batchSize',
        'maxInflight',
        'publishTimeoutSeconds',
        'retryTimes',
        'maxMessageBytes',
        'maxMessages',
        'maxTotalPayloadBytes',
      ],
    },
  ],
  API_CALL: [
    {
      title: 'details.request',
      keys: ['resourceId', 'method', 'path', 'headers', 'headerNames', 'body', 'webService'],
    },
    { title: 'apiCall.remoteStop', keys: ['remoteStop', 'remoteStopHeaderNames'] },
    { title: 'details.response', keys: ['successCondition', 'extractors', 'maxResponseBytes'] },
  ],
  DATA_CHECK: [
    { title: 'dataCheck.querySettings', keys: ['resourceId', 'queryTimeoutSeconds'] },
    { title: 'dataCheck.checks', keys: ['checks'] },
    { title: 'dataCheck.handlers', keys: ['handlers'] },
  ],
}

export function detailSections(plugin: string, value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || isLosslessNumber(value))
    return []
  const definitions = pluginDetailSections[plugin]
  if (!definitions) return []
  const entries = Object.entries(value)
  const known = new Set(definitions.flatMap((section) => section.keys))
  return [
    ...definitions,
    {
      title: 'details.otherConfiguration',
      keys: entries.map(([key]) => key).filter((key) => !known.has(key)),
    },
  ]
    .map((section) => ({
      title: section.title,
      value: Object.fromEntries(
        section.keys
          .filter((key) => Object.hasOwn(value, key))
          .map((key) => [key, (value as Record<string, unknown>)[key]]),
      ),
    }))
    .filter((section) => Object.keys(section.value).length)
}
