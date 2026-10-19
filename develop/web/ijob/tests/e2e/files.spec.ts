// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { test, expect, type Page } from '@playwright/test'
import { setupFiles } from '../files-fixture'
async function editTask(page: Page, name: string, en = false) {
  await page.goto('/tasks')
  await page.getByRole('button', { name: (en ? 'Edit' : '编辑') + ' ' + name, exact: true }).click()
}
for (const theme of ['light', 'dark'])
  for (const locale of ['zh-CN', 'en-US']) {
    const en = locale === 'en-US'
    test('transfer ' + theme + ' ' + locale, async ({ page }, info) => {
      const state = await setupFiles(page)
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await editTask(page, 'Transfer fixture', en)
      await expect(page.locator('.file-task-fields')).toBeVisible()
      const publication = page.locator('[id="task-transfer.remotePublishMode"]')
      await expect(publication.locator('xpath=..')).toContainText(en ? 'Direct upload' : '直接上传')
      await publication.click()
      await page
        .locator('.n-base-select-option')
        .filter({ hasText: en ? 'Upload then rename' : '临时上传后更名' })
        .click()
      await expect(page.locator('.transfer-group h3')).toHaveCount(4)
      for (const prefix of ['source', 'target']) {
        const resource = await page
          .locator('[id="task-transfer.' + prefix + 'ResourceId"]')
          .boundingBox()
        const directory = await page
          .locator('[id="task-transfer.' + prefix + 'Path"]')
          .boundingBox()
        expect(
          Math.abs(resource!.y + resource!.height / 2 - directory!.y - directory!.height / 2),
        ).toBeLessThan(3)
        expect(directory!.x).toBeGreaterThan(resource!.x + resource!.width)
      }
      await expect(page.locator('[id="task-transfer.sourcePath"]')).toHaveValue('/')
      await expect(page.locator('.task-heading-identity')).toContainText('#9007199254740993')
      await page.locator('[id="task-transfer.compressType"]').click()
      await page.locator('.n-base-select-option').filter({ hasText: /^ZIP$/ }).click()
      await page.locator('[id="task-transfer.archiveName"]').fill('reports.zip')
      await page.waitForTimeout(250)
      await page.screenshot({ path: info.outputPath('transfer-desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 320, height: 844 })
      const source = await page.locator('[id="task-transfer.sourceResourceId"]').boundingBox()
      const sourcePath = await page.locator('[id="task-transfer.sourcePath"]').boundingBox()
      expect(sourcePath!.y).toBeGreaterThan(source!.y + source!.height)
      const label = await page.locator('label[for="task-enabled"]').boundingBox(),
        toggle = await page.locator('#task-enabled').boundingBox()
      expect(Math.abs(label!.y + label!.height / 2 - toggle!.y - toggle!.height / 2)).toBeLessThan(
        3,
      )
      expect((await page.locator('.task-drawer').boundingBox())!.width).toBeLessThanOrEqual(320)
      await page.screenshot({ path: info.outputPath('transfer-mobile.png'), fullPage: true })
      await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
      await expect.poll(() => state.writes.length).toBe(1)
      expect(state.writes[0]!.body.pluginConfig).toContain('"remotePublishMode":"TEMP_RENAME"')
      expect(state.writes[0]!.body.pluginConfig).toContain('"sourceResourceId":9007199254740993')
      expect(state.writes[0]!.body.pluginConfig).toContain('"targetResourceId":9223372036854775807')
      expect(state.writes[0]!.body.pluginConfig).toContain('"extra":0.1234567890123456789')
    })
    test('probe ' + theme + ' ' + locale, async ({ page }, info) => {
      const state = await setupFiles(page)
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await editTask(page, 'Probe fixture', en)
      await expect(page.locator('.file-probe-fields')).toBeVisible()
      await expect(page.locator('[id="task-event.filePath"]')).toHaveValue('/')
      await expect(page.locator('.file-probe-fields')).toContainText(en ? 'Scans once' : '单次扫描')
      const count = page.locator('[id="task-event.maxMatches"]')
      await count.fill('')
      await count.pressSequentially('1e2.5-')
      await expect(count).toHaveValue('125')
      await page.waitForTimeout(250)
      await page.screenshot({ path: info.outputPath('probe-desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 320, height: 844 })
      await page.screenshot({ path: info.outputPath('probe-mobile.png'), fullPage: true })
      await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
      await expect.poll(() => state.writes.length).toBe(1)
      const c = String(state.writes[0]!.body.pluginConfig)
      expect(c).toContain('"maxMatches":125')
      expect(c).not.toContain('"payload"')
      expect(c).toContain('"extra":9223372036854775807')
    })
    test('SFTP resource ' + theme + ' ' + locale, async ({ page }, info) => {
      const state = await setupFiles(page)
      await page.addInitScript(
        (p) => localStorage.setItem('ijob:preferences:v1:guest', JSON.stringify(p)),
        { theme, locale },
      )
      await page.goto('/resources/files')
      await page
        .getByRole('button', { name: (en ? 'Edit' : '编辑') + ' SFTP fixture', exact: true })
        .click()
      await expect(page.locator('#file-knownHosts')).toHaveCount(0)
      await expect(page.locator('.file-fingerprint')).toContainText('SHA256:')
      await page.locator('#file-name').fill('Renamed')
      await page.waitForTimeout(250)
      await page.screenshot({ path: info.outputPath('resource-desktop.png'), fullPage: true })
      await page.setViewportSize({ width: 320, height: 844 })
      expect((await page.locator('.file-drawer').boundingBox())!.width).toBeLessThanOrEqual(320)
      await page.screenshot({ path: info.outputPath('resource-mobile.png'), fullPage: true })
      await page.getByRole('button', { name: en ? 'Save' : '保存', exact: true }).click()
      await expect.poll(() => state.writes.length).toBe(1)
      const c = state.writes[0]!.body.fileConfig as Record<string, unknown>
      expect(c.password).toBeUndefined()
      expect(c.hostKey).toBeUndefined()
      expect(c.preserveExisting).toBe(true)
      expect(state.probes).toHaveLength(0)
    })
  }
for (const protocol of ['LOCAL', 'FTP', 'S3_COMPATIBLE'])
  test('edit ' + protocol, async ({ page }) => {
    const state = await setupFiles(page)
    await page.goto('/resources/files')
    await page.getByRole('button', { name: '编辑 ' + protocol + ' fixture', exact: true }).click()
    await page.locator('#file-name').fill('Renamed')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect.poll(() => state.writes.length).toBe(1)
    const c = state.writes[0]!.body.fileConfig as Record<string, unknown>
    expect(c.protocol).toBe(protocol)
    expect(c.password).toBeUndefined()
    expect(c.secretKey).toBeUndefined()
  })
test('new SFTP trust is explicit and cancelling never saves', async ({ page }) => {
  const state = await setupFiles(page)
  await page.goto('/resources/files')
  await page.getByRole('button', { name: '新增文件资源', exact: true }).click()
  await page.locator('#file-name').fill('New SFTP')
  await page.locator('#file-protocol').click()
  await page
    .locator('.n-base-select-option')
    .filter({ hasText: /^SFTP$/ })
    .click()
  await page.locator('#file-host').fill('files.example.invalid')
  await page.locator('#file-username').fill('fixture')
  await page.locator('#file-password').fill('fixture-password')
  await expect(page.locator('#file-privateKey')).toHaveCount(0)
  await expect(page.locator('.n-base-select-option:visible')).toHaveCount(0)
  await page.locator('#file-auth').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: /^私钥$/ })
    .click()
  await expect(page.locator('#file-password')).toHaveCount(0)
  await page.locator('#file-privateKey').fill('/keys/id_ed25519')
  await expect(page.locator('#file-privateKey')).toHaveAttribute('type', 'text')
  await expect(page.locator('.n-base-select-option:visible')).toHaveCount(0)
  await page.locator('#file-auth').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: /^密码$/ })
    .click()
  await expect(page.locator('#file-password')).toHaveValue('fixture-password')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('.n-dialog')).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toHaveCount(0)
  expect(state.writes).toHaveLength(0)
  await expect(page.locator('#file-password')).toHaveValue('fixture-password')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '确认信任', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect((state.writes[0]!.body.fileConfig as Record<string, unknown>).hostKey).toBe(
    'fixture-public-key',
  )
  expect(state.writes[0]!.body.fileConfig).not.toHaveProperty('privateKey')
})
test('readonly file detail does not expose writing actions', async ({ page }) => {
  const state = await setupFiles(page, false)
  await page.goto('/resources/files')
  await expect(page.getByRole('button', { name: '新增文件资源', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '查看 SFTP fixture', exact: true }).click()
  await expect(page.locator('#file-name')).toBeDisabled()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
  expect(state.writes).toHaveLength(0)
})
test('invalid probe root focuses required field without saving', async ({ page }) => {
  const state = await setupFiles(page)
  await editTask(page, 'Probe fixture')
  await page.locator('[id="task-event.filePath"]').fill('')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.locator('[id="task-event.filePath"]')).toBeFocused()
  expect(state.writes).toHaveLength(0)
})
test('switching protocol restores private drafts and drops irrelevant payload fields', async ({
  page,
}) => {
  const state = await setupFiles(page)
  await page.goto('/resources/files')
  await page.getByRole('button', { name: '新增文件资源', exact: true }).click()
  await page.locator('#file-name').fill('Protocol draft')
  await page.locator('#file-basePath').fill('E:/fixture')
  await page.locator('#file-protocol').click()
  await page.locator('.n-base-select-option').filter({ hasText: /^FTP$/ }).click()
  await page.locator('#file-password').fill('draft-secret')
  await page.locator('#file-protocol').click()
  await page
    .locator('.n-base-select-option')
    .filter({ hasText: /^LOCAL$/ })
    .click()
  await expect(page.locator('#file-basePath')).toHaveValue('E:/fixture')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => state.writes.length).toBe(1)
  expect(state.writes[0]!.body.fileConfig).not.toHaveProperty('password')
})
test('closing also protects drafts cached under another protocol', async ({ page }) => {
  const state = await setupFiles(page)
  await page.goto('/resources/files')
  await page.getByRole('button', { name: '新增文件资源', exact: true }).click()
  await page.locator('#file-protocol').click()
  await page.locator('.n-base-select-option:visible').filter({ hasText: /^FTP$/ }).click()
  await page.locator('#file-password').fill('draft-secret')
  await page.locator('#file-protocol').click()
  await page
    .locator('.n-base-select-option:visible')
    .filter({ hasText: /^LOCAL$/ })
    .click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.n-dialog')).toBeVisible()
  await page.locator('.n-dialog').getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('#file-protocol').click()
  await page.locator('.n-base-select-option:visible').filter({ hasText: /^FTP$/ }).click()
  await expect(page.locator('#file-password')).toHaveValue('draft-secret')
  expect(state.writes).toHaveLength(0)
})

test('unknown publication outcome keeps draft and disables repeats', async ({ page }) => {
  const state = await setupFiles(page, true, 'network')
  await editTask(page, 'Transfer fixture')
  await page.locator('[id="task-transfer.sourcePath"]').fill('retained')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.locator('[id="task-transfer.sourcePath"]')).toHaveValue('retained')
  expect(state.writes).toHaveLength(1)
})

for (const failure of ['service', 'network'])
  test('failed SFTP probe remains editable and retryable: ' + failure, async ({ page }) => {
    const state = await setupFiles(page)
    let attempts = 0
    await page.route('**/api/resources/ssh/host-key', async (route) => {
      if (++attempts > 1) return route.fallback()
      return failure === 'network'
        ? route.abort()
        : route.fulfill({
            status: 503,
            json: {
              success: false,
              code: 503,
              messageKey: 'error.business.ssh-host-key-probe-failed',
            },
          })
    })
    await page.goto('/resources/files')
    await page.getByRole('button', { name: '新增文件资源', exact: true }).click()
    await page.locator('#file-name').fill('Retryable SFTP')
    await page.locator('#file-protocol').click()
    await page
      .locator('.n-base-select-option:visible')
      .filter({ hasText: /^SFTP$/ })
      .click()
    await page.locator('#file-host').fill('first.example.invalid')
    await page.locator('#file-username').fill('fixture')
    await page.locator('#file-password').fill('fixture-password')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect.poll(() => attempts).toBe(1)
    await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
    await expect(page.locator('#file-host')).toBeEnabled()
    expect(state.writes).toHaveLength(0)
    await page.locator('#file-host').fill('second.example.invalid')
    await expect(page.locator('#file-password')).toHaveValue('fixture-password')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await page.locator('.n-dialog').getByRole('button', { name: '确认信任', exact: true }).click()
    await expect.poll(() => state.writes.length).toBe(1)
    expect((state.writes[0]!.body.fileConfig as Record<string, unknown>).host).toBe(
      'second.example.invalid',
    )
  })

test('a failed saved-resource connection test does not block editing', async ({ page }) => {
  await setupFiles(page)
  await page.route('**/api/resources/*/connection-test', (route) =>
    route.fulfill({
      status: 503,
      json: { success: false, code: 503 },
    }),
  )
  await page.goto('/resources/files')
  const row = page.locator('tr').filter({ hasText: 'SFTP fixture' })
  await row.getByRole('button', { name: /连接测试/ }).click()
  await page.locator('.n-dialog').getByRole('button', { name: '连接测试', exact: true }).click()
  await page.getByRole('button', { name: '编辑 SFTP fixture', exact: true }).click()
  await expect(page.locator('#file-host')).toBeEnabled()
  await page.locator('#file-host').fill('corrected.example.invalid')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
})

test('uncertain FILE write still disables duplicate submission', async ({ page }) => {
  const state = await setupFiles(page, true, 'network')
  await page.goto('/resources/files')
  await page.getByRole('button', { name: '编辑 SFTP fixture', exact: true }).click()
  await page.locator('#file-name').fill('Uncertain edit')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  expect(state.writes).toHaveLength(1)
})
