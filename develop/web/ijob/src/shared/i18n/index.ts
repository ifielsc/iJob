// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { createI18n } from 'vue-i18n'
import { zhCN, enUS } from './messages'

export const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})
