import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { messageTranslations, ApiError, describeError } from '../../src/shared/api/errors'
import { zhCN, enUS } from '../../src/shared/i18n/messages'
import { isSupportedPlugin, pluginLabel, pluginTypes } from '../../src/shared/plugins'
import { routePresentation } from '../../src/shared/navigation/route-presentation'
const valueAt = (root: unknown, path: string): unknown =>
  path.split('.').reduce<unknown>((v, key) => (v as Record<string, unknown>)?.[key], root)
describe('frontend contracts', () => {
  it('maps only backend keys that exist to real bilingual frontend messages', () => {
    const backend = readFileSync(
      '../../api/ijob/ijob-common/src/main/resources/messages.properties',
      'utf8',
    )
    const keys = new Set(
      backend
        .split(/\r?\n/)
        .filter((s) => !s.startsWith('#'))
        .map((s) => s.split(/[=:]/)[0]?.trim()),
    )
    for (const [source, target] of Object.entries(messageTranslations)) {
      expect(keys.has(source), source).toBe(true)
      expect(typeof valueAt(zhCN, target), target).toBe('string')
      expect(typeof valueAt(enUS, target), target).toBe('string')
    }
    expect(
      describeError(
        new ApiError('generic', 400, { messageKey: 'future.key', message: 'Safe original' }),
        (k) => k,
      ),
    ).toBe('Safe original')
  })
  it('keeps unknown plugins visible without treating them as supported', () => {
    for (const type of pluginTypes) {
      expect(isSupportedPlugin(type)).toBe(true)
      expect(typeof valueAt(zhCN, `pluginNames.${type}`)).toBe('string')
    }
    expect(isSupportedPlugin('FUTURE_PLUGIN')).toBe(false)
    expect(pluginLabel('FUTURE_PLUGIN', (k) => k)).toBe('FUTURE_PLUGIN')
  })
  it('registers every application route with a valid compact presentation', () => {
    const source = readFileSync('src/app/router.ts', 'utf8')
    const names = [...source.matchAll(/name: '([^']+)'/g)].map((m) => m[1]!)
    for (const name of names.filter((n) => !['login', 'change-password'].includes(n))) {
      expect(routePresentation[name], name).toBeDefined()
      expect(typeof valueAt(zhCN, routePresentation[name]!.titleKey)).toBe('string')
      expect(routePresentation[name]!.compact).toBe(true)
    }
  })
})
