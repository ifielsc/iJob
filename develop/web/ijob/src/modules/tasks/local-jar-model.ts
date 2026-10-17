import { LosslessNumber, parse, stringify } from 'lossless-json'
export const parameterKinds = ['object', 'array', 'string', 'number', 'boolean', 'null'] as const
export type ParameterKind = (typeof parameterKinds)[number]
export interface ParameterNode {
  key: string
  name: string
  kind: ParameterKind
  text: string
  flag: boolean
  children: ParameterNode[]
}
let sequence = 0
export function parameterNode(kind: ParameterKind = 'string', name = ''): ParameterNode {
  return { key: String(++sequence), name, kind, text: '', flag: false, children: [] }
}
export const localJarLimits = { bytes: 256 * 1024, depth: 64, nodes: 10000 }
export function container(node: ParameterNode) {
  return node.kind === 'object' || node.kind === 'array'
}
export function parameterPath(root: ParameterNode, key: string): ParameterNode[] | undefined {
  if (root.key === key) return [root]
  for (const child of root.children) {
    const path = parameterPath(child, key)
    if (path) return [root, ...path]
  }
}
const numeric = (value: string) =>
  /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(value) && value.length <= 1000
// Prefix keys because lossless-json assigns __proto__ specially and permits
// equal-valued duplicate keys. Complete string tokens keep escaped text intact.
function parseExact(json: string): unknown {
  const stack: Set<string>[] = []
  const encoded = json.replace(/"(?:[^"\\]|\\.)*"|[{}[\]]/g, (token, offset: number) => {
    if (token === '{' || token === '[') {
      stack.push(new Set())
      if (stack.length > localJarLimits.depth + 2) throw Error()
    } else if (token === '}' || token === ']') stack.pop()
    else if (/^\s*:/.test(json.slice(offset + token.length))) {
      const key = JSON.parse(token) as string,
        keys = stack.at(-1)
      if (!keys || keys.has(key)) throw Error()
      keys.add(key)
      return JSON.stringify('key:' + key)
    }
    return token
  })
  const restore = (v: unknown): unknown =>
    v && typeof v === 'object' && !(v instanceof LosslessNumber)
      ? Array.isArray(v)
        ? v.map(restore)
        : Object.fromEntries(
            Object.entries(v).map(([key, value]) => [key.slice(4), restore(value)]),
          )
      : v
  return restore(parse(encoded))
}
function fromValue(value: unknown, name = '', depth = 0, count = { value: 0 }): ParameterNode {
  if (depth > localJarLimits.depth || ++count.value > localJarLimits.nodes) throw Error()
  const node = parameterNode('null', name)
  if (value instanceof LosslessNumber) {
    node.kind = 'number'
    node.text = value.value
  } else if (typeof value === 'string') {
    node.kind = 'string'
    node.text = value
  } else if (typeof value === 'boolean') {
    node.kind = 'boolean'
    node.flag = value
  } else if (value != null && typeof value === 'object') {
    node.kind = Array.isArray(value) ? 'array' : 'object'
    node.children = Object.entries(value).map(([key, item]) =>
      fromValue(item, key, depth + 1, count),
    )
  } else if (value !== null) throw Error()
  return node
}
function valueOf(node: ParameterNode): unknown {
  switch (node.kind) {
    case 'object':
      return Object.fromEntries(node.children.map((child) => [child.name, valueOf(child)]))
    case 'array':
      return node.children.map(valueOf)
    case 'string':
      return node.text
    case 'number':
      if (!numeric(node.text)) throw Error()
      return new LosslessNumber(node.text)
    case 'boolean':
      return node.flag
    case 'null':
      return null
  }
}
export function localJarDraft(json?: string) {
  const draft = {
    blocked: false,
    jarFile: '',
    sha256: '',
    entryClass: '',
    root: parameterNode('object'),
    snapshot: undefined as
      | undefined
      | {
          jarSize: string
          implementationVersion: string
          spiVersion: string
          jarFile: string
          sha256: string
          entryClass: string
        },
  }
  if (json === undefined) return draft
  try {
    if (new TextEncoder().encode(json).length > localJarLimits.bytes) throw Error()
    const v: unknown = parseExact(json)
    if (!v || typeof v !== 'object' || Array.isArray(v) || v instanceof LosslessNumber)
      throw Error()
    const c = v as Record<string, unknown>
    if (
      Object.keys(c).some(
        (key) =>
          ![
            'formatVersion',
            'jarFile',
            'sha256',
            'entryClass',
            'jarSize',
            'implementationVersion',
            'spiVersion',
            'userConfig',
          ].includes(key),
      )
    )
      throw Error()
    if (
      c.formatVersion != null &&
      (!(c.formatVersion instanceof LosslessNumber) || c.formatVersion.value !== '1')
    )
      throw Error()
    for (const key of ['jarFile', 'sha256', 'entryClass'] as const) {
      if (typeof c[key] !== 'string') throw Error()
      draft[key] = c[key]
    }
    if (c.jarSize != null || c.implementationVersion != null || c.spiVersion != null) {
      if (
        !(c.jarSize instanceof LosslessNumber) ||
        !/^[1-9]\d*$/.test(c.jarSize.value) ||
        BigInt(c.jarSize.value) > 9223372036854775807n ||
        typeof c.implementationVersion !== 'string' ||
        c.spiVersion !== '2'
      )
        throw Error()
      draft.snapshot = {
        jarSize: c.jarSize.value,
        implementationVersion: c.implementationVersion,
        spiVersion: c.spiVersion,
        jarFile: draft.jarFile,
        sha256: draft.sha256,
        entryClass: draft.entryClass,
      }
    }
    draft.root = fromValue(c.userConfig ?? null)
  } catch {
    draft.blocked = true
  }
  return draft
}
export type LocalJarDraft = ReturnType<typeof localJarDraft>
export function localJarConfig(d: LocalJarDraft) {
  if (d.blocked) throw Error('Unsupported Local JAR configuration')
  return stringify({
    formatVersion: 1,
    jarFile: d.jarFile,
    sha256: d.sha256,
    entryClass: d.entryClass,
    userConfig: valueOf(d.root),
  })!
}
export function localJarIssues(d: LocalJarDraft) {
  const errors: Record<string, string> = {},
    fail = (key: string, code = 'invalid') => {
      errors['localJar.' + key] = code
    }
  if (d.blocked) {
    fail('jarFile', 'unsupported')
    return errors
  }
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,250}\.jar$/i.test(d.jarFile)) fail('jarFile', 'fileInvalid')
  if (!/^[0-9a-f]{64}$/i.test(d.sha256)) fail('sha256', 'shaInvalid')
  if (
    d.entryClass.length > 255 ||
    !/^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+$/.test(d.entryClass) ||
    d.entryClass.startsWith('com.ifelio.ijob.') ||
    d.entryClass.startsWith('com.ifeel.ijob.')
  )
    fail('entryClass', 'entryInvalid')
  let count = 0
  const visit = (node: ParameterNode, depth: number) => {
    if (++count > localJarLimits.nodes || depth > localJarLimits.depth) {
      fail('nodes.' + node.key + '.value', 'treeLimit')
      return
    }
    if (node.kind === 'number' && !numeric(node.text))
      fail('nodes.' + node.key + '.value', 'numberInvalid')
    const names = new Set<string>()
    if (container(node))
      for (const child of node.children) {
        if (node.kind === 'object' && names.has(child.name))
          fail('nodes.' + child.key + '.name', 'duplicateKey')
        names.add(child.name)
        visit(child, depth + 1)
      }
  }
  visit(d.root, 0)
  if (
    !Object.keys(errors).length &&
    new TextEncoder().encode(localJarConfig(d)).length > localJarLimits.bytes
  )
    fail('size', 'tooLarge')
  return errors
}
