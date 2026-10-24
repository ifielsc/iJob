// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { requestKey } from './shared/api/request'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './app/App.vue'
import { i18n } from './shared/i18n'
import { queryClient } from './shared/api/query'
import { createRuntime, runtimeKey } from './app/runtime'
import { installAccessGuard, router } from './app/router'
import './shared/theme/global.css'

const app = createApp(App)
app.use(createPinia())
app.use(i18n)
app.use(VueQueryPlugin, { queryClient })
const runtime = app.runWithContext(createRuntime)
app.provide(runtimeKey, runtime)
app.provide(requestKey, runtime.request)
installAccessGuard(router, runtime)
app.use(router)
// Resolve standalone routes before mounting so /login never briefly mounts the
// authenticated shell and starts an anonymous identity request.
void router.isReady().then(() => app.mount('#app'))
