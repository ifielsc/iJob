<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, inject } from 'vue'
import { BaseEdge, EdgeLabelRenderer, getBezierPath } from '@vue-flow/core'
import type { EdgeProps } from '@vue-flow/core'
import { routingKey } from './use-edge-routing'
import { nearestPathPoint } from './edge-routing'
const props = defineProps<EdgeProps>()
const routing = inject(routingKey)!
const labelPosition = computed(() => routing.labels.value.get(props.id))
const route = computed(() => {
  const saved = routing.routes.get(props.id)
  if (
    routing.mode.value === 'avoid' &&
    saved &&
    Math.abs(saved.start.x - props.sourceX) < 0.1 &&
    Math.abs(saved.start.y - props.sourceY) < 0.1 &&
    Math.abs(saved.end.x - props.targetX) < 0.1 &&
    Math.abs(saved.end.y - props.targetY) < 0.1
  )
    return saved
  const [path, x, y] = getBezierPath(props)
  return {
    path,
    points: undefined,
    label: { x, y },
    kind:
      routing.mode.value === 'simple'
        ? 'simple'
        : routing.dragging.value
          ? 'preview'
          : routing.available.value
            ? 'pending'
            : 'fallback',
  }
})
const guideStart = computed(() =>
  labelPosition.value && route.value.points
    ? nearestPathPoint(labelPosition.value, route.value.points)
    : route.value.label,
)
</script>
<template>
  <g :data-routing="route.kind">
    <BaseEdge
      :id="id"
      :path="route.path"
      :label="undefined"
      :label-x="route.label.x"
      :label-y="route.label.y"
      :label-show-bg="true"
      :label-bg-padding="[5, 3]"
      :label-bg-border-radius="4"
      :marker-start="markerStart"
      :marker-end="markerEnd"
      :interaction-width="interactionWidth"
      :style="style"
    />
    <path
      v-if="label && labelPosition"
      :d="`M ${guideStart.x} ${guideStart.y} L ${labelPosition.x} ${labelPosition.y}`"
      class="job-label-guide"
    />
  </g>
  <EdgeLabelRenderer v-if="label && labelPosition">
    <button
      type="button"
      class="job-route-label nodrag nopan"
      :class="{ selected }"
      :title="data?.labelTitle || String(label)"
      :style="{
        transform: `translate(-50%, -50%) translate(${labelPosition.x}px, ${labelPosition.y}px)`,
      }"
      @click.stop="routing.select(id)"
    >
      {{ label }}
    </button>
  </EdgeLabelRenderer>
</template>
