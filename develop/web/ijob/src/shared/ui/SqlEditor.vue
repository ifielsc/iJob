<script setup lang="ts">
import './sql-editor.css'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NButton, NCheckbox, useMessage } from 'naive-ui'
import { Maximize2, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { EditorState, Compartment } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { basicSetup } from 'codemirror'
import { sql } from '@codemirror/lang-sql'
import { openSearchPanel, closeSearchPanel, searchPanelOpen } from '@codemirror/search'
import { syntaxHighlighting, HighlightStyle, StreamLanguage } from '@codemirror/language'
import { shell } from '@codemirror/legacy-modes/mode/shell'
import { powerShell } from '@codemirror/legacy-modes/mode/powershell'
import { javascript } from '@codemirror/legacy-modes/mode/javascript'
import { python } from '@codemirror/legacy-modes/mode/python'
import { tags } from '@lezer/highlight'
import { useRuntime } from '../../app/runtime'
const props = defineProps<{
  modelValue: string
  disabled?: boolean
  readOnly?: boolean
  invalid?: boolean
  language?: string
  title?: string
  contextKey?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [string]; expanded: [boolean] }>()
const runtime = useRuntime(),
  { t, locale } = useI18n(),
  message = useMessage()
const hasSelection = ref(false),
  hasContent = ref(false),
  copying = ref(false)
const host = ref<HTMLElement | null>(null),
  largeHost = ref<HTMLElement | null>(null),
  modal = ref<HTMLDialogElement | null>(null)
const expanded = ref(false),
  wrap = ref(true),
  expandButton = ref<InstanceType<typeof NButton> | null>(null)
const themeSlot = new Compartment(),
  wrapSlot = new Compartment(),
  accessSlot = new Compartment(),
  languageSlot = new Compartment(),
  phraseSlot = new Compartment()
let view: EditorView | undefined
let activeContext = props.contextKey ?? 'SQL'
const states = new Map<string, { state: EditorState; top: number; left: number }>()
const editorTitle = computed(
  () =>
    props.title ??
    t(
      props.language === 'JSON'
        ? 'apiCall.body'
        : !props.language || props.language === 'SQL'
          ? 'tasks.editorTitle'
          : 'tasks.scriptEditorTitle',
    ),
)
const languages = {
  JSON: StreamLanguage.define(javascript),
  SHELL: StreamLanguage.define(shell),
  POWERSHELL: StreamLanguage.define(powerShell),
  PYTHON: StreamLanguage.define(python),
}
function languageExtension() {
  return !props.language || props.language === 'SQL'
    ? sql()
    : (languages[props.language as keyof typeof languages] ?? [])
}
const phrases = computed<Record<string, string>>(() =>
  locale.value === 'zh-CN'
    ? {
        Find: '查找',
        Replace: '替换',
        next: '下一个',
        previous: '上一个',
        all: '全部',
        'match case': '区分大小写',
        regexp: '正则表达式',
        'by word': '完整词',
        replace: '替换',
        'replace all': '全部替换',
        close: '关闭',
        'Go to line': '跳转行',
        go: '跳转',
        'Selection deleted': '已删除所选内容',
      }
    : ({} as Record<string, string>),
)
function theme() {
  const dark = runtime.preferences.theme === 'dark'
  return [
    syntaxHighlighting(
      HighlightStyle.define([
        { tag: tags.keyword, color: dark ? '#C7B7FF' : '#5A40A5' },
        { tag: tags.string, color: dark ? '#90D9B5' : '#087653' },
        { tag: tags.number, color: dark ? '#FFC88B' : '#9A4D00' },
        { tag: tags.comment, color: dark ? '#BDCCE2' : '#52627B' },
        { tag: [tags.name, tags.operator, tags.punctuation], color: dark ? '#EDF3FF' : '#24324B' },
      ]),
    ),
    EditorView.theme(
      {
        '&': {
          backgroundColor: 'var(--panel)',
          color: 'var(--text)',
          height: '100%',
          fontSize: '13px',
        },
        '.cm-scroller': { overflow: 'auto', fontFamily: 'Consolas, monospace' },
        '.cm-content': { caretColor: 'var(--text)', minHeight: '100%' },
        '.cm-gutters': {
          backgroundColor: 'var(--panel)',
          color: 'var(--muted)',
          borderColor: 'var(--border)',
        },
        // The selection layer sits below the text: an opaque line fill would hide it.
        '.cm-activeLine': { backgroundColor: 'transparent' },
        '.cm-activeLineGutter': {
          backgroundColor: dark ? 'rgba(255, 255, 255, 0.04)' : 'rgba(36, 50, 75, 0.04)',
        },
        '.cm-panels': {
          backgroundColor: 'var(--panel)',
          color: 'var(--text)',
          borderColor: 'var(--border)',
        },
        '.cm-textfield, .cm-button': {
          background: 'var(--panel)',
          color: 'var(--text)',
          border: '1px solid var(--controlBorder)',
          font: 'inherit',
        },
        '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, & .cm-selectionBackground':
          {
            backgroundColor: dark ? '#405C89' : '#B4D3FF',
          },
        '.cm-cursor': { borderLeftColor: 'var(--text)' },
      },
      { dark: runtime.preferences.theme === 'dark' },
    ),
  ]
}
function access() {
  return [
    EditorState.readOnly.of(!!props.disabled || !!props.readOnly),
    EditorView.editable.of(!props.disabled && !props.readOnly),
    EditorView.contentAttributes.of({
      'aria-label': t(
        props.language === 'JSON'
          ? 'apiCall.body'
          : !props.language || props.language === 'SQL'
            ? 'tasks.sql'
            : 'tasks.script',
      ),
      'aria-invalid': String(!!props.invalid),
      id: props.language === 'JSON' ? 'task-body' : 'task-script',
      spellcheck: 'false',
    }),
  ]
}
function stateFor(value: string) {
  return EditorState.create({
    doc: value,
    extensions: [
      basicSetup,
      languageSlot.of(languageExtension()),
      themeSlot.of(theme()),
      wrapSlot.of(EditorView.lineWrapping),
      accessSlot.of(access()),
      phraseSlot.of(EditorState.phrases.of(phrases.value)),
      EditorView.updateListener.of((update) => {
        hasSelection.value = update.state.selection.ranges.some((range) => !range.empty)
        hasContent.value = update.state.doc.length > 0
        if (update.docChanged) emit('update:modelValue', update.state.doc.toString())
      }),
    ],
  })
}
onMounted(() => {
  view = new EditorView({ parent: host.value!, state: stateFor(props.modelValue) })
  hasContent.value = view.state.doc.length > 0
})
watch(
  () => [props.modelValue, props.contextKey ?? 'SQL'] as const,
  ([value, context]) => {
    if (view && context !== activeContext) {
      states.set(activeContext, {
        state: view.state,
        top: view.scrollDOM.scrollTop,
        left: view.scrollDOM.scrollLeft,
      })
      const saved = states.get(context)
      view.setState(saved?.state ?? stateFor(value))
      activeContext = context
      view.dispatch({
        effects: [
          languageSlot.reconfigure(languageExtension()),
          themeSlot.reconfigure(theme()),
          accessSlot.reconfigure(access()),
          phraseSlot.reconfigure(EditorState.phrases.of(phrases.value)),
          wrapSlot.reconfigure(wrap.value ? EditorView.lineWrapping : []),
        ],
      })
      view.scrollDOM.scrollTop = saved?.top ?? 0
      view.scrollDOM.scrollLeft = saved?.left ?? 0
      view.requestMeasure()
    }
    if (view && value !== view.state.doc.toString())
      view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
  },
)
watch(
  () => [props.disabled, props.readOnly, props.invalid, locale.value, props.language],
  () => view?.dispatch({ effects: accessSlot.reconfigure(access()) }),
)
watch(
  () => props.language,
  () => view?.dispatch({ effects: languageSlot.reconfigure(languageExtension()) }),
)
watch(
  () => runtime.preferences.theme,
  () => view?.dispatch({ effects: themeSlot.reconfigure(theme()) }),
)
watch(phrases, () =>
  view?.dispatch({ effects: phraseSlot.reconfigure(EditorState.phrases.of(phrases.value)) }),
)
watch(wrap, () =>
  view?.dispatch({ effects: wrapSlot.reconfigure(wrap.value ? EditorView.lineWrapping : []) }),
)
async function enlarge() {
  if (!view || props.disabled) return
  const top = view.scrollDOM.scrollTop,
    left = view.scrollDOM.scrollLeft
  expanded.value = true
  emit('expanded', true)
  await nextTick()
  modal.value?.showModal()
  largeHost.value?.append(view.dom)
  view.requestMeasure()
  requestAnimationFrame(() => {
    if (view) {
      view.scrollDOM.scrollTop = top
      view.scrollDOM.scrollLeft = left
      view.focus()
    }
  })
}
function finish() {
  if (!expanded.value || !view) return
  const top = view.scrollDOM.scrollTop,
    left = view.scrollDOM.scrollLeft
  host.value?.append(view.dom)
  modal.value?.close()
  expanded.value = false
  emit('expanded', false)
  view.requestMeasure()
  requestAnimationFrame(() => {
    if (view) {
      view.scrollDOM.scrollTop = top
      view.scrollDOM.scrollLeft = left
    }
    expandButton.value?.$el?.focus()
  })
}
function escape(event: KeyboardEvent) {
  if (event.key === 'Tab' && modal.value) {
    const focusable = [
      ...modal.value.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), [tabindex="0"], [contenteditable="true"]',
      ),
    ].filter((el) => el.getClientRects().length > 0 && el.getAttribute('aria-disabled') !== 'true')
    const target = event.shiftKey ? focusable.at(-1) : focusable[0]
    const boundary = event.shiftKey ? focusable[0] : focusable.at(-1)
    if (target && document.activeElement === boundary) {
      event.preventDefault()
      event.stopPropagation()
      target.focus()
    }
    return
  }
  if (event.key !== 'Escape') return
  // Consume Escape before the drawer listener, including native dialog cancellation.
  event.preventDefault()
  event.stopPropagation()
  if (view && searchPanelOpen(view.state)) {
    closeSearchPanel(view)
    view.focus()
  } else finish()
}
function find() {
  if (view) openSearchPanel(view)
}
function selectAll() {
  if (!view) return
  view.dispatch({ selection: { anchor: 0, head: view.state.doc.length } })
  view.focus()
}
async function copySelection(cut = false) {
  const editor = view
  if (
    !editor ||
    copying.value ||
    (!hasSelection.value && !props.readOnly) ||
    (cut && (props.disabled || props.readOnly))
  )
    return
  const state = editor.state
  const context = activeContext
  const text =
    props.readOnly && !hasSelection.value
      ? state.doc.toString()
      : state.selection.ranges
          .filter((range) => !range.empty)
          .map((range) => state.sliceDoc(range.from, range.to))
          .join(state.lineBreak)
  copying.value = true
  try {
    try {
      await navigator.clipboard.writeText(text)
    } catch {
      // Support HTTP/LAN deployments where the async Clipboard API is unavailable.
      const input = document.createElement('textarea')
      input.value = text
      input.style.cssText = 'position:fixed;opacity:0;width:1px;height:1px;pointer-events:none'
      ;(expanded.value ? modal.value! : document.body).append(input)
      try {
        input.select()
        if (!document.execCommand('copy')) throw new Error('Clipboard unavailable')
      } finally {
        input.remove()
      }
    }
    // Clipboard permission may be asynchronous: never delete a newer draft or selection.
    if (
      cut &&
      view === editor &&
      activeContext === context &&
      !props.disabled &&
      !props.readOnly &&
      editor.state.doc === state.doc &&
      editor.state.selection.eq(state.selection)
    ) {
      editor.dispatch({
        ...state.replaceSelection(''),
        userEvent: 'delete.cut',
        scrollIntoView: true,
      })
    }
    message.success(t('unified.copied'))
  } catch {
    message.error(t('unified.copyFailed'))
  } finally {
    copying.value = false
    if (view === editor) editor.focus()
  }
}
function focus() {
  view?.focus()
}
defineExpose({ focus })
onBeforeUnmount(() => {
  modal.value?.close()
  view?.destroy()
  view = undefined
  states.clear()
})
</script>
<template>
  <div class="sql-editor" :class="{ 'is-invalid': invalid }">
    <div class="sql-editor-tools">
      <NButton size="small" :disabled="!hasContent" @mousedown.prevent @click="selectAll">{{
        t('tasks.selectAllText')
      }}</NButton>
      <NButton
        size="small"
        :disabled="(!hasSelection && !(readOnly && hasContent)) || copying"
        @mousedown.prevent
        @click="copySelection()"
        >{{ t('unified.copy') }}</NButton
      >
      <NButton
        v-if="!readOnly"
        size="small"
        :disabled="disabled || !hasSelection || copying"
        @mousedown.prevent
        @click="copySelection(true)"
        >{{ t('tasks.cutText') }}</NButton
      >
      <NButton ref="expandButton" size="small" :disabled="disabled" @click="enlarge"
        ><template #icon><Maximize2 :size="15" /></template
        >{{ t(readOnly ? 'details.expandJson' : 'tasks.expand') }}</NButton
      >
    </div>
    <div ref="host" class="sql-inline-host" />
    <dialog
      ref="modal"
      class="sql-large-editor"
      :aria-label="editorTitle"
      @cancel.prevent="finish"
      @keydown.capture="escape"
    >
      <header>
        <h2>{{ editorTitle }}</h2>
        <NButton quaternary circle :aria-label="t('common.close')" @click="finish"
          ><X :size="18"
        /></NButton>
      </header>
      <div class="sql-large-toolbar">
        <NButton size="small" :disabled="!hasContent" @mousedown.prevent @click="selectAll">{{
          t('tasks.selectAllText')
        }}</NButton>
        <NButton
          size="small"
          :disabled="(!hasSelection && !(readOnly && hasContent)) || copying"
          @mousedown.prevent
          @click="copySelection()"
          >{{ t('unified.copy') }}</NButton
        >
        <NButton
          v-if="!readOnly"
          size="small"
          :disabled="disabled || !hasSelection || copying"
          @mousedown.prevent
          @click="copySelection(true)"
          >{{ t('tasks.cutText') }}</NButton
        >
        <NButton size="small" @click="find">{{ t('tasks.find') }}</NButton
        ><NCheckbox v-model:checked="wrap">{{ t('tasks.wrap') }}</NCheckbox>
      </div>
      <div ref="largeHost" class="sql-large-host" />
      <footer>
        <span v-if="!readOnly">{{ t('tasks.editorHint') }}</span>
        <NButton type="primary" @click="finish">{{
          t(readOnly ? 'common.close' : 'tasks.finish')
        }}</NButton>
      </footer>
    </dialog>
  </div>
</template>
