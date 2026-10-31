// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { ObjectDirective } from 'vue'

export declare const clickoutside: ObjectDirective<
  HTMLElement,
  ((event: MouseEvent) => void) | undefined
>
export declare const mousemoveoutside: ObjectDirective<
  HTMLElement,
  ((event: MouseEvent) => void) | undefined
>
export declare const zindexable: ObjectDirective<
  HTMLElement,
  { enabled?: boolean; zIndex?: number } | undefined
>
