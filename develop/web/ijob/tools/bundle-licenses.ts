// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, join, parse, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Plugin } from 'vite'
import supplementalLicenses from './third-party/supplemental-licenses.json'

interface PackageInfo {
  name: string
  version: string
  license?: string | { type?: string }
}

function packageFor(id: string): { root: string; info: PackageInfo; file: string } | undefined {
  const file = id.replace(/^\0/, '').split('?')[0]!
  if (!file.replaceAll('\\', '/').includes('/node_modules/')) return
  let folder = dirname(file)
  while (folder !== parse(folder).root) {
    const metadata = join(folder, 'package.json')
    if (existsSync(metadata)) {
      const info = JSON.parse(readFileSync(metadata, 'utf8')) as PackageInfo
      if (info.name && info.version) return { root: folder, info, file }
    }
    folder = dirname(folder)
  }
  throw new Error('Cannot identify bundled dependency')
}

export function bundleLicenses(): Plugin {
  const supplements = supplementalLicenses as Record<
    string,
    { file: string; source: string; sha256: string }
  >
  return {
    name: 'ijob-bundle-license-inventory',
    apply: 'build',
    generateBundle(_options, bundle) {
      const records = new Map<
        string,
        {
          name: string
          version: string
          declaredLicense: string
          licenseTextStatus: 'present' | 'missing-upstream-text'
          modules: Set<string>
          chunks: Set<string>
          notices: { file: string; sha256: string; text: string }[]
        }
      >()
      const record = (id: string, chunk?: string) => {
        const found = packageFor(id)
        if (!found) return
        const { root, info, file } = found
        const key = `${info.name}@${info.version}`
        let value = records.get(key)
        if (!value) {
          const declaredLicense =
            typeof info.license === 'string' ? info.license : info.license?.type
          if (!declaredLicense) throw new Error(`Missing dependency license: ${key}`)
          const notices = readdirSync(root, { withFileTypes: true })
            .filter((entry) => entry.isFile() && /^(licen[cs]e|notice|copying)/i.test(entry.name))
            .sort((a, b) => a.name.localeCompare(b.name))
            .map((entry) => {
              const bytes = readFileSync(join(root, entry.name))
              return {
                file: entry.name,
                sha256: createHash('sha256').update(bytes).digest('hex'),
                text: bytes.toString('utf8'),
              }
            })
          const supplement = supplements[key]
          if (!notices.length && supplement) {
            const bytes = readFileSync(
              fileURLToPath(new URL(`./third-party/${supplement.file}`, import.meta.url)),
            )
            const sha256 = createHash('sha256').update(bytes).digest('hex')
            if (sha256 !== supplement.sha256) throw new Error(`License hash mismatch: ${key}`)
            notices.push({
              file: supplement.source,
              sha256,
              text: bytes.toString('utf8'),
            })
          }
          if (!notices.length) {
            this.warn(`Dependency declares ${declaredLicense} but ships no license text: ${key}`)
          }
          value = {
            name: info.name,
            version: info.version,
            declaredLicense,
            licenseTextStatus: notices.length ? 'present' : 'missing-upstream-text',
            modules: new Set(),
            chunks: new Set(),
            notices,
          }
          records.set(key, value)
        }
        value.modules.add(relative(root, file).replaceAll('\\', '/'))
        if (chunk) value.chunks.add(chunk)
      }
      for (const chunk of Object.values(bundle)) {
        if (chunk.type !== 'chunk') continue
        for (const [id, details] of Object.entries(chunk.modules)) {
          if (details.renderedLength > 0) record(id, chunk.fileName)
        }
      }
      // Style extraction is separate from JavaScript lengths; retain imported styles conservatively.
      for (const id of this.getModuleIds()) {
        if (/\.(css|scss|sass|less)(\?|$)/.test(id)) record(id)
      }
      const components = [...records.values()]
        .sort((a, b) => `${a.name}@${a.version}`.localeCompare(`${b.name}@${b.version}`))
        .map((value) => ({
          ...value,
          modules: [...value.modules].sort(),
          chunks: [...value.chunks].sort(),
          notices: value.notices.map(({ file, sha256 }) => ({ file, sha256 })),
        }))
      this.emitFile({
        type: 'asset',
        fileName: '.vite/third-party-components.json',
        source: JSON.stringify(
          { scope: 'Rendered chunk modules plus conservative dependency style inputs', components },
          null,
          2,
        ),
      })
      const text = [...records.values()]
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((value) =>
          [
            `${value.name}@${value.version} — ${value.declaredLicense}`,
            ...(value.notices.length
              ? []
              : ['Upstream package omits license text; redistribution review remains pending.']),
            ...value.notices.map((notice) => `${notice.file}\n${notice.text}`),
          ].join('\n\n'),
        )
        .join('\n\n' + '='.repeat(72) + '\n\n')
      this.emitFile({ type: 'asset', fileName: 'THIRD_PARTY_NOTICES.txt', source: text + '\n' })
    },
  }
}
