# iJob UI directives

Independent first-party implementation, MIT, Copyright (c) 2026 ifielsc.
No original vdirs implementation is included or derived into this package.

The workspace override resolves **both** Naive UI and vueuc's `vdirs` imports
to this local package. Its own identity is `@ijob/ui-directives@1.0.0`; the
dependency alias is retained solely for the consumers' existing import contract.
Do not change only one consumer: floating followers and dialogs must share one
layer allocator. No installation scripts, generated code, or runtime Vue import
are needed. CommonJS and ES module consumers use the same implementation.

The compatibility boundary is the three root exports used by the pinned
Naive UI 2.45.3 and vueuc 0.4.66 versions:

- `clickoutside`: deliver the mouseup event only when both mousedown and mouseup
  occur outside. The capture modifier controls listener phase; handlers can be
  replaced or disabled. Do not close an overlay on an inside-to-outside drag.
- `mousemoveoutside`: deliver outside mousemove events in the bubble phase.
- `zindexable`: when enabled, reserve an automatic layer starting at 2000 or
  apply an explicit zIndex (including zero). Keep the rank while visible and the
  inline style during leave transitions; release reservations on disable/unmount.
  Reopening advances the automatic rank while other automatic layers remain.
  When all automatic layers close, allocation restarts at 2000. Explicit layers
  do not change automatic allocation.

DOM listeners are scoped to the element's document, removed on unmount, and
never installed at module import. Shadow DOM composed paths count as inside.
This is not a general replacement for unused/deep vdirs APIs. When updating
Naive UI/vueuc, review their imports and rerun directive contracts, component
integration, Basic browser/production checks and distribution provenance checks.
