import js from '@eslint/js'
import ts from 'typescript-eslint'
import vue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'
import path from 'node:path'

const moduleBoundary = {
  meta: {
    type: 'problem',
    schema: [],
    messages: { internal: 'Use shared infrastructure or the owning module public entry point.' },
  },
  create(context) {
    const filename = context.filename.replaceAll('\\', '/')
    const runtimeAdapters = [
      'shared/ui/use-list-memory.ts',
      'shared/ui/TimezoneControl.vue',
      'shared/ui/SqlEditor.vue',
      'shared/forms/use-leave-guard.ts',
    ]
    const shared = filename.includes('/src/shared/')
    const owner = filename.match(/\/src\/modules\/([^/]+)\//)?.[1]
    function check(node) {
      const value = node.source?.value
      if (typeof value !== 'string' || !value.startsWith('.')) return
      const target = path.resolve(path.dirname(filename), value).replaceAll('\\', '/')
      const match = target.match(/\/src\/modules\/([^/]+)\/(.*)/)
      if (
        shared &&
        (match ||
          (target.includes('/src/app/') &&
            !(
              target.endsWith('/app/runtime') &&
              runtimeAdapters.some((p) => filename.endsWith('/src/' + p))
            )))
      )
        context.report({ node: node.source, messageId: 'internal' })
      if (owner && match && match[1] !== owner && !match[2].startsWith('public/'))
        context.report({ node: node.source, messageId: 'internal' })
    }
    return {
      ImportExpression: check,
      ImportDeclaration: check,
      ExportNamedDeclaration: check,
      ExportAllDeclaration: check,
    }
  },
}

export default ts.config(
  { ignores: ['dist/**', 'node_modules/**', 'playwright-report/**', 'test-results*/**'] },
  js.configs.recommended,
  ...ts.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['src/modules/**/*.{ts,vue}', 'src/shared/**/*.{ts,vue}'],
    plugins: { boundaries: { rules: { 'module-public-api': moduleBoundary } } },
    rules: { 'boundaries/module-public-api': 'error' },
  },
  {
    files: ['**/*.vue'],
    languageOptions: { parserOptions: { parser: ts.parser }, globals: { document: 'readonly' } },
    rules: {
      'no-undef': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/html-self-closing': 'off',
    },
  },
  {
    files: ['public/**/*.js'],
    languageOptions: { globals: { document: 'readonly', localStorage: 'readonly' } },
  },
  prettier,
)
