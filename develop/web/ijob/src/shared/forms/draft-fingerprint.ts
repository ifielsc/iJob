// Normalize only fields whose submission contract trims whitespace. Never trim code or secrets.
export function draftFingerprint(value: unknown): string {
  return JSON.stringify(value, (key, item) => {
    if (['userConfig', 'variables', 'inputVariables', 'body', 'headers'].includes(key))
      return JSON.stringify(item)
    if (key === 'protocolDrafts') return undefined
    if (['name', 'code', 'displayName', 'username'].includes(key) && typeof item === 'string')
      return item.trim()
    if (item && typeof item === 'object' && 'mode' in item && 'configured' in item) {
      const emptySecret =
        /^(password|secret|token|passphrase|privateKey)$/i.test(key) &&
        item.mode === 'replace' &&
        item.value === ''
      const mode = emptySecret ? 'keep' : item.mode
      return { mode, value: mode === 'replace' ? item.value : undefined }
    }
    return item
  })
}
