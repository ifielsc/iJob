// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { readFileSync, readdirSync, existsSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import ts from 'typescript'
const root = path.resolve('src')
const graph = new Map(),
  counts = { runtime: 0, typeOnly: 0, dynamic: 0 }
function walk(dir) {
  for (const item of readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, item.name)
    if (item.isDirectory()) walk(file)
    else if (/\.(ts|vue)$/.test(file)) graph.set(file, [])
  }
}
walk(root)
function resolve(from, source) {
  if (!source.startsWith('.')) return
  const target = path.resolve(path.dirname(from), source)
  return [target, target + '.ts', target + '.vue', path.join(target, 'index.ts')].find(
    (p) => graph.has(p) && existsSync(p),
  )
}
for (const [file, edges] of graph) {
  let source = readFileSync(file, 'utf8')
  if (file.endsWith('.vue'))
    source = [...source.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)]
      .map((m) => m[1])
      .join('\n')
  const ast = ts.createSourceFile(file + '.ts', source, ts.ScriptTarget.Latest, true)
  function visit(node) {
    if (
      (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
      node.moduleSpecifier &&
      ts.isStringLiteral(node.moduleSpecifier)
    ) {
      const target = resolve(file, node.moduleSpecifier.text)
      if (target) {
        const clause = node.importClause
        const bindings = clause?.namedBindings
        const typeOnly =
          node.isTypeOnly ||
          clause?.isTypeOnly ||
          (bindings &&
            ts.isNamedImports(bindings) &&
            !clause.name &&
            bindings.elements.length > 0 &&
            bindings.elements.every((e) => e.isTypeOnly))
        if (typeOnly) counts.typeOnly++
        else {
          counts.runtime++
          edges.push(target)
        }
      }
    }
    if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword)
      counts.dynamic++
    ts.forEachChild(node, visit)
  }
  visit(ast)
}
let next = 0
const index = new Map(),
  low = new Map(),
  stack = [],
  active = new Set(),
  cycles = []
function visit(node) {
  index.set(node, next)
  low.set(node, next++)
  stack.push(node)
  active.add(node)
  for (const target of graph.get(node)) {
    if (!index.has(target)) {
      visit(target)
      low.set(node, Math.min(low.get(node), low.get(target)))
    } else if (active.has(target)) low.set(node, Math.min(low.get(node), index.get(target)))
  }
  if (low.get(node) === index.get(node)) {
    const component = []
    let member
    do {
      member = stack.pop()
      active.delete(member)
      component.push(path.relative(root, member).replaceAll('\\', '/'))
    } while (member !== node)
    if (component.length > 1 || graph.get(node).includes(node)) cycles.push(component.sort())
  }
}
for (const node of graph.keys()) if (!index.has(node)) visit(node)
process.stdout.write(
  JSON.stringify({ files: graph.size, ...counts, staticRuntimeCycles: cycles }, null, 2) + '\n',
)
if (cycles.length) process.exitCode = 1
