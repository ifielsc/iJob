// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
export interface FileChoice {
  path: string
  directory: boolean
  name: string
  parent: string
  root: string
}
export function exactGlob(name: string) {
  return name.replace(/[\\*?{}[\]]/g, (c) => '\\' + c)
}
export function pathParts(path: string) {
  return path.split('/').filter(Boolean)
}
export function resourcePath(root: string, relative: string) {
  return root.replace(/[\\/]+$/, '') + (relative ? '/' + relative : '') || '/'
}
