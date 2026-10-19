<!--
Copyright (c) 2026 ifielsc
SPDX-License-Identifier: MIT
-->

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NCollapse, NCollapseItem, NInput, NModal } from 'naive-ui'
const { t } = useI18n()
const draft = ref('')
const show = ref(false)
</script>

<template>
  <div class="page-heading">
    <div>
      <h1>{{ t('lab.title') }}</h1>
      <p>{{ t('lab.description') }}</p>
    </div>
  </div>
  <section class="panel glass">
    <label for="verification-draft">{{ t('lab.label') }}</label
    ><NInput
      v-model:value="draft"
      type="textarea"
      :input-props="{ id: 'verification-draft' }"
      :placeholder="t('lab.placeholder')"
    /><NCollapse
      ><NCollapseItem :title="t('lab.advanced')" name="advanced">{{
        t('lab.preserved')
      }}</NCollapseItem></NCollapse
    ><NButton @click="show = true">{{ t('lab.dialog') }}</NButton>
  </section>
  <NModal
    v-model:show="show"
    preset="card"
    role="dialog"
    :aria-label="t('lab.dialogTitle')"
    :title="t('lab.dialogTitle')"
    :closable="false"
    :style="{ width: 'min(480px, calc(100vw - 32px))' }"
    ><p>{{ t('lab.dialogBody') }}</p>
    <NButton @click="show = false">{{ t('lab.close') }}</NButton></NModal
  >
</template>
