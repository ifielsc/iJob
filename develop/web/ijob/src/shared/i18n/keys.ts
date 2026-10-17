import type { zhCN } from './messages'
type LeafKeys<T> = {
  [K in keyof T & string]: T[K] extends string ? K : `${K}.${LeafKeys<T[K]>}`
}[keyof T & string]
export type MessageKey = LeafKeys<typeof zhCN>
export function messageKey<K extends MessageKey>(key: K): K {
  return key
}
