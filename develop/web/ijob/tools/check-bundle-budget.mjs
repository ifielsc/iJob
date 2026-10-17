import process from 'node:process'
import { readFileSync } from 'node:fs'
import { gzipSync } from 'node:zlib'
const manifest = JSON.parse(readFileSync('dist/.vite/manifest.json', 'utf8'))
const budget = JSON.parse(readFileSync('tools/bundle-budget.json', 'utf8'))
function closure(entry) {
  const files = new Set(),
    visited = new Set()
  function visit(key) {
    if (visited.has(key)) return
    visited.add(key)
    const chunk = manifest[key]
    if (!chunk) throw new Error(`Missing manifest entry ${key}`)
    if (chunk.file.endsWith('.js')) files.add(chunk.file)
    for (const child of chunk.imports ?? []) visit(child)
  }
  visit(entry)
  return [...files]
}
const rows = Object.entries(budget.routes).map(([entry, maximum]) => {
  const files = closure(entry)
  const gzipBytes = files.reduce(
    (sum, file) => sum + gzipSync(readFileSync('dist/' + file)).length,
    0,
  )
  return { entry, chunks: files.length, gzipBytes, maximum, pass: gzipBytes <= maximum }
})
process.stdout.write(
  JSON.stringify(
    { unit: 'gzip bytes; static JS dependency closure, excludes async imports', rows },
    null,
    2,
  ) + '\n',
)
if (rows.some((r) => !r.pass)) process.exitCode = 1
