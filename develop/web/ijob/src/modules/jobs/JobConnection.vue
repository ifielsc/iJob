<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { computed } from 'vue'
import { getBezierPath } from '@vue-flow/core'
import type { ConnectionLineProps } from '@vue-flow/core'
const props = defineProps<ConnectionLineProps>()
// A live gesture follows only its endpoints; route around obstacles once connected.
const path = computed(() => getBezierPath(props)[0])
</script>
<template>
  <path
    class="vue-flow__connection-path"
    data-routing="preview"
    fill="none"
    :d="path"
    :marker-end="markerEnd"
  />
</template>
