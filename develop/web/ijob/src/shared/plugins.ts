// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { messageKey } from './i18n/keys'
export const pluginTypes = [
  'SQL',
  'SCRIPT',
  'API_CALL',
  'EVENT',
  'DATA_CHECK',
  'FILE_TRANSFER',
  'PUSH',
  'ETL',
  'BATCH_ETL',
  'SERVER_MONITOR',
  'LOCAL_JAR',
] as const
export type SupportedPlugin = (typeof pluginTypes)[number]
export function isSupportedPlugin(type: string): type is SupportedPlugin {
  return (pluginTypes as readonly string[]).includes(type)
}
export function pluginLabel(type: string, t: (key: string) => string) {
  return isSupportedPlugin(type) ? `${type} · ${t(messageKey(`pluginNames.${type}`))}` : type
}
