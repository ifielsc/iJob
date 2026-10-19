// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { readFileSync } from 'node:fs'
import process from 'node:process'
import { URL } from 'node:url'

const expected = readFileSync(new URL('../.node-version', import.meta.url), 'utf8').trim()
if (process.versions.node !== expected)
  throw new Error(`Use Node ${expected}; actual ${process.versions.node}`)
process.stdout.write(`Verified Node ${expected}\n`)
