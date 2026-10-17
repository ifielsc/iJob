<script setup lang="ts">
import { ChevronDown } from '@lucide/vue'
import { NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'
defineProps<{ expanded: boolean; count?: number; controls?: string; label?: string }>()
defineEmits<{ toggle: [] }>()
const { t } = useI18n()
</script>
<template>
  <NButton
    text
    type="primary"
    class="advanced-filter"
    :aria-expanded="expanded"
    :aria-controls="controls"
    @click="$emit('toggle')"
    >{{ label ?? t('unified.advanced') }}{{ !expanded && count ? ` (${count})` : ''
    }}<ChevronDown
      :size="14"
      :style="{ transform: expanded ? 'rotate(180deg)' : undefined, marginLeft: '4px' }"
  /></NButton>
</template>
