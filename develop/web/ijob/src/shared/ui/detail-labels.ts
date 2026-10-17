type Translate = (key: string) => string
type Exists = (key: string) => boolean
const resourceScopes: Record<string, string[]> = {
  fileResource: ['files', 'config'],
  monitorResource: ['monitorResource', 'config'],
  serviceResource: ['apiCall', 'config'],
  mqResource: ['mq', 'config'],
  eventTask: ['eventTask', 'files'],
}
export function detailLabel(
  scope: string,
  path: string,
  key: string,
  t: Translate,
  te: Exists,
): string {
  if (scope === 'etl' && key === 'type' && ['source', 'target'].includes(path))
    return t('etl.' + path + 'Type')
  if (scope === 'etl' && !path) {
    const titles: Record<string, string> = {
      source: 'etl.sourceTitle',
      target: 'etl.targetTitle',
      incremental: 'etl.incrementalTitle',
      execution: 'etl.execution',
      message: 'etl.messageTab',
    }
    if (titles[key]) return t(titles[key])
  }
  if (scope === 'etl' && path === 'message' && key === 'maxTotalBytes')
    return t('etl.messageTotalBytes')
  if (scope === 'localJar' && key === 'userConfig') return t('localJar.parameters')
  if (key === 'formatVersion') return t('details.version')
  if (scope === 'fileResource' && key === 'timeout') return t('details.timeoutMilliseconds')
  if (key.endsWith('Configured'))
    return (
      detailLabel(scope, path, key.slice(0, -10), t, te) + ' · ' + t('details.configurationState')
    )
  if (scope === 'monitor') {
    if (path === 'rules.disk.paths') return key
    if (key === 'enabled') return t('monitor.enable')
    if (/^rules\.applications\.\d+$/.test(path) && ['cpu', 'memory'].includes(key))
      return t(key === 'cpu' ? 'details.appCpu' : 'monitor.appMemory')
    if (key === 'applications' && path === 'rules') return t('monitor.appRules')
  }
  if (
    [
      'jdbcUrl',
      'schema',
      'accessKey',
      'secretKey',
      'sessionToken',
      'webhook',
      'endpoint',
      'token',
      'virtualHost',
      'exchange',
      'routingKey',
    ].includes(key)
  )
    return t('details.' + key)
  for (const namespace of resourceScopes[scope] ?? [scope]) {
    const candidates =
      namespace === 'etl'
        ? /^(transforms|validations)\.\d+\.properties/.test(path)
          ? ['form_' + key, key, 'field_' + key]
          : [key, 'field_' + key, 'form_section_' + key, 'form_' + key]
        : [key]
    for (const candidate of candidates)
      if (te(namespace + '.' + candidate)) return t(namespace + '.' + candidate)
  }
  return te('details.' + key) ? t('details.' + key) : key
}
// User-defined map keys are data, even when they match a translated schema field.
export function literalChildren(scope: string, path: string, key: string) {
  if (scope === 'localJar' && !path && ['userConfig', 'parameters'].includes(key)) return true
  if (scope === 'eventTask' && !path && key === 'payload') return true
  if (scope === 'tasks' && !path && key === 'env') return true
  if (scope === 'apiCall' && ['body', 'headers', 'parameters'].includes(key)) return true
  if (
    scope === 'etl' &&
    ['parameters', 'columns', 'columnNullPolicies', 'replace', 'metadataColumns'].includes(key)
  )
    return true
  return false
}
