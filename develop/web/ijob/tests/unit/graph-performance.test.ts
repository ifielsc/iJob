// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

// @vitest-environment node
import { expect, it } from 'vitest'
import { computed, reactive } from 'vue'
import { parse } from 'lossless-json'
import { freshDraft, json, issues, type JobDraft } from '../../src/modules/jobs/model'
import { draftFingerprint } from '../../src/shared/forms/draft-fingerprint'
for (const [nodeCount, edgeCount] of [
  [100, 99],
  [500, 499],
  [500, 5000],
])
  it(`measures reactive draft serialization ${nodeCount} nodes / ${edgeCount} edges`, () => {
    const draft = reactive<JobDraft>({
      ...freshDraft(),
      name: 'Budget fixture',
      nodes: Array.from({ length: nodeCount! }, (_, i) => ({
        nodeId: 'n' + i,
        name: 'Node ' + i,
        kind: 'EXECUTION',
        nodeType: 'TASK',
        refId: '1',
        position: { x: i * 200, y: 0 },
        policy: { failStrategy: 'STOP', executeCondition: 'ALL_SUCCESS' },
      })),
      edges: [],
    })
    for (let distance = 1; draft.edges.length < edgeCount!; distance++)
      for (let from = 0; from + distance < nodeCount! && draft.edges.length < edgeCount!; from++)
        draft.edges.push({
          id: 'e' + draft.edges.length,
          fromNodeId: 'n' + from,
          toNodeId: 'n' + (from + distance),
          conditionText: '',
        })
    // Match JobEditor's editable projection and compare its old/new read paths on the same proxy.
    const editable = () => ({
      ...draft,
      nodes: draft.nodes.map((n) => ({
        nodeId: n.nodeId,
        name: n.name,
        kind: n.kind,
        nodeType: n.nodeType,
        refId: n.refId,
        policy: n.policy,
        gateway: n.gateway,
        position: n.position,
      })),
    })
    const baseline = json(editable())
    const serialized = computed(() => json(editable()))
    const currentFingerprint = computed(() => draftFingerprint(parse(serialized.value)))
    const baselineFingerprint = draftFingerprint(parse(baseline))
    const before: number[] = [],
      after: number[] = []
    const historySnapshot = () => json({ draft, errors: {}, literals: {} })
    function sample(optimized: boolean, i: number) {
      draft.nodes[0]!.name = 'Edited ' + i + (optimized ? 'a' : 'b')
      const start = performance.now()
      if (optimized) {
        void (serialized.value !== baseline)
        void (currentFingerprint.value !== baselineFingerprint)
        void serialized.value // watch reads the cached scalar, no nested JSON encoding
      } else {
        void (json(editable()) !== baseline)
        void (draftFingerprint(parse(json(editable()))) !== draftFingerprint(parse(baseline)))
        json([json(editable()), {}, {}])
      }
      historySnapshot()
      return performance.now() - start
    }
    // Alternate order to avoid consistently warming one implementation with the other.
    for (let i = 0; i < 24; i++) {
      if (i % 2) {
        after.push(sample(true, i))
        before.push(sample(false, i))
      } else {
        before.push(sample(false, i))
        after.push(sample(true, i))
      }
    }
    const percentile = (samples: number[]) => {
      const ordered = samples.slice(4).sort((a, b) => a - b)
      return Math.round(ordered[Math.ceil(ordered.length * 0.95) - 1]! * 100) / 100
    }
    const bytes = Buffer.byteLength(historySnapshot())
    const p95 = percentile(after)
    console.log(
      JSON.stringify({
        nodeCount,
        edgeCount,
        measuredSamples: 20,
        warmupSamples: 4,
        beforeP95Ms: percentile(before),
        afterP95Ms: p95,
        snapshotBytes: bytes,
        history40Bytes: bytes * 40,
      }),
    )
    expect(issues(draft)).toEqual([])
    // Ceiling covers projected dirty/changed/history reads, not paint or network latency.
    expect(p95).toBeLessThan(500)
    expect(bytes * 40).toBeLessThan(64 * 1024 * 1024)
  }, 30000)
