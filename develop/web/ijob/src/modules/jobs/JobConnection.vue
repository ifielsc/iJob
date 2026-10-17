<script setup lang="ts">
import { computed, inject } from 'vue'
import type { ConnectionLineProps } from '@vue-flow/core'
import { routingKey } from './use-edge-routing'
const props = defineProps<ConnectionLineProps>()
const routing = inject(routingKey)!
const route = computed(() =>
  routing.preview({
    id: 'connection-preview',
    source: props.sourceNode.id,
    target: props.targetNode?.id ?? '',
    start: { x: props.sourceX, y: props.sourceY },
    end: { x: props.targetX, y: props.targetY },
    sourceSide: props.sourcePosition,
    targetSide: props.targetPosition,
  }),
)
</script>
<template>
  <path class="vue-flow__connection-path" fill="none" :d="route.path" :marker-end="markerEnd" />
</template>
