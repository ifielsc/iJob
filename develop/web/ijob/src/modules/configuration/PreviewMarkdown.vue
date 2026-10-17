<script setup lang="ts">
import { computed } from 'vue'
import { markdownBlocks } from './notification-preview'
const props = defineProps<{ text: string }>()
const blocks = computed(() => markdownBlocks(props.text))
</script>
<template>
  <div class="preview-markdown">
    <template v-for="(block, i) in blocks" :key="i">
      <hr v-if="block.kind === 'hr'" />
      <component :is="block.kind === 'heading' ? 'h3' : block.kind === 'list' ? 'ul' : 'p'" v-else>
        <component :is="block.kind === 'list' ? 'li' : 'span'">
          <component
            :is="token.strong ? 'strong' : 'span'"
            v-for="(token, j) in block.tokens"
            :key="j"
            >{{ token.text }}</component
          >
        </component>
      </component>
    </template>
  </div>
</template>
<style scoped>
.preview-markdown {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
}
.preview-markdown :is(p, ul) {
  margin: 0 0 12px;
}
.preview-markdown ul {
  padding-left: 20px;
}
.preview-markdown h3 {
  margin: 0 0 16px;
  font-size: 17px;
}
.preview-markdown hr {
  margin: 16px 0;
  border: 0;
  border-top: 1px solid var(--border);
}
</style>
