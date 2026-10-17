import { expect, test } from '@playwright/test'

test('production approval chunk loads and displays exact IDs and actual decisions', async ({
  page,
}) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  const row = {
    id: '9223372036854775807',
    instanceId: '9007199254740993',
    rootInstanceId: '9007199254740993',
    taskName: 'Production approval',
    rootTargetName: 'Production approval',
    waitType: 'APPROVAL',
    status: 'APPROVED',
    decisionBy: 'reviewer',
    decisionComment: 'Verified decision',
  }
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const path = new URL(route.request().url()).pathname
      const data =
        path === '/api/auth/me'
          ? {
              loginId: 'prod-approver',
              username: 'operator',
              displayName: 'Operator',
              roleCode: 'OPERATOR',
              permissions: ['SUMMARY_READ', 'APPROVAL_DECIDE'],
              mustChangePassword: false,
            }
          : path === '/api/event-waits'
            ? { list: [row], total: 1 }
            : row
      await route.fulfill({ json: { success: true, code: 200, data } })
    },
  )
  await page.goto('/events/approvals?status=APPROVED&waitId=' + row.id)
  await expect(page.locator('.approval-drawer')).toContainText('Verified decision')
  await expect(page.locator('.approval-drawer')).toContainText(row.id)
  await expect(
    page.locator('.approval-footer').getByRole('button', { name: '通过', exact: true }),
  ).toHaveCount(0)
  expect(errors).toEqual([])
})
