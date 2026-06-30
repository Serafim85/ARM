import { test, expect } from '@playwright/test';
import { loginViaUi } from '../../helpers/auth-ui';
import { isStackReachable } from '../../helpers/stack';

test.describe('Login smoke @mvp', () => {
  test.beforeEach(async ({ baseURL }) => {
    test.skip(!(await isStackReachable(baseURL!)), 'Start stack or set E2E_STACK_UP=1');
  });

  test('login page is reachable', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Вход в систему' })).toBeVisible();
  });

  test('operator can sign in with test user', async ({ page }) => {
    await loginViaUi(page);
    await expect(page).toHaveURL(/\/workstations/);
    await expect(page.getByRole('heading', { name: 'Рабочие станции' })).toBeVisible();
  });
});
