<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed, inject } from 'vue'
import { BaseEdge, getBezierPath } from '@vue-flow/core'
import type { EdgeProps } from '@vue-flow/core'
import { routingKey } from './use-edge-routing'
const props = defineProps<EdgeProps>()
const routing = inject(routingKey)!
const route = computed(() => {
  const saved = routing.routes.get(props.id)
  if (
    saved &&
    Math.abs(saved.start.x - props.sourceX) < 0.1 &&
    Math.abs(saved.start.y - props.sourceY) < 0.1 &&
    Math.abs(saved.end.x - props.targetX) < 0.1 &&
    Math.abs(saved.end.y - props.targetY) < 0.1
  )
    return saved
  const [path, x, y] = getBezierPath(props)
  return { path, label: { x, y }, kind: 'pending' }
})
</script>
<template>
  <g :data-routing="route.kind">
    <BaseEdge
      :id="id"
      :path="route.path"
      :label="label"
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
  </g>
</template>
