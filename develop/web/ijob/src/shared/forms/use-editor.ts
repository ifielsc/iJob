// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { draftFingerprint } from './draft-fingerprint'
import { computed, ref, shallowRef } from 'vue'
import { useLeaveGuard } from './use-leave-guard'
import { useDialog } from 'naive-ui'
import { useI18n } from 'vue-i18n'
export function useEditor(
  snapshot: () => unknown,
  emitClose: () => void,
  hasUnpublishedInput: () => boolean = () => false,
) {
  const { t } = useI18n(),
    dialog = useDialog()
  const baseline = JSON.stringify(snapshot())
  const effectiveBaseline = draftFingerprint(snapshot())
  const changed = computed(() => effectiveBaseline !== draftFingerprint(snapshot()))
  const dirty = computed(() => baseline !== JSON.stringify(snapshot()) || hasUnpublishedInput())
  const busy = ref(false),
    error = shallowRef<unknown>(null),
    submitted = ref(false)
  function confirm(title: string, content: string) {
    return new Promise<boolean>((resolve) =>
      dialog.warning({
        title,
        content,
        positiveText: t('common.confirm'),
        negativeText: t('common.cancel'),
        onPositiveClick: () => resolve(true),
        onNegativeClick: () => resolve(false),
        onClose: () => resolve(false),
        onMaskClick: () => resolve(false),
        onEsc: () => resolve(false),
      }),
    )
  }
  async function allowClose() {
    return (
      !busy.value && (!dirty.value || (await confirm(t('common.discard'), t('common.discardHint'))))
    )
  }
  async function close() {
    if (await allowClose()) emitClose()
  }
  useLeaveGuard(() => dirty.value, allowClose)
  function focusError(root: HTMLElement | null) {
    root
      ?.querySelector<HTMLElement>(
        '[aria-invalid="true"] input, input[aria-invalid="true"], textarea[aria-invalid="true"], [aria-invalid="true"]',
      )
      ?.focus()
  }
  return { busy, error, submitted, confirm, close, focusError, changed }
}
