import { test, expect } from '@playwright/test';
import { findWorkstationId, ingestLinuxWorkstation, loginAccessToken } from '../../helpers/api';
import { loginViaUi } from '../../helpers/auth-ui';
import { isStackReachable } from '../../helpers/stack';

test.describe('Arm card @mvp', () => {
  let workstationId = 0;
  const hostname = `e2e-card-${Date.now()}`;

  test.beforeAll(async ({ baseURL }) => {
    test.skip(!(await isStackReachable(baseURL!)), 'Start stack or set E2E_STACK_UP=1');
    await ingestLinuxWorkstation(hostname);
    const token = await loginAccessToken();
    workstationId = await findWorkstationId(hostname, token);
  });

  test('displays CPU RAM disk charts', async ({ page }) => {
    await loginViaUi(page);
    await page.goto(`/workstations/${workstationId}`);
    await expect(page.getByRole('heading', { name: hostname })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Метрики' })).toBeVisible();
    await expect(page.getByText('CPU', { exact: true })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Память', { exact: true })).toBeVisible();
    await expect(page.getByText('Диск (корень)', { exact: true })).toBeVisible();
  });
});
