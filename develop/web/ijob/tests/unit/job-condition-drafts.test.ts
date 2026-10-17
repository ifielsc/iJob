import { describe, expect, it } from 'vitest'
import { hasActiveConditionDraft } from '../../src/modules/jobs/condition-drafts'
describe('unparsed condition drafts', () => {
  const drafts = { removed: { right: '[' }, route: { right: '{' } }
  it('does not keep deleted edge and node drafts dirty after the graph was saved', () => {
    expect(hasActiveConditionDraft([], drafts)).toBe(false)
  })
  it('ignores a default route draft but restores it when undo makes the route conditional', () => {
    expect(hasActiveConditionDraft([{ id: 'route', defaultRoute: true }], drafts)).toBe(false)
    expect(hasActiveConditionDraft([{ id: 'route', defaultRoute: false }], drafts)).toBe(true)
  })
  it('still protects incomplete input on existing conditional edges', () => {
    expect(hasActiveConditionDraft([{ id: 'route' }], drafts)).toBe(true)
    expect(hasActiveConditionDraft([{ id: 'route' }], { route: {} })).toBe(false)
  })
})
