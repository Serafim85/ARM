import { expect, type Page } from '@playwright/test';
import { e2ePassword, e2eUser } from './stack';

export async function loginViaUi(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByLabel(/логин|email/i).fill(e2eUser);
  await page.locator('input[type="password"]').fill(e2ePassword);
  await page.getByRole('button', { name: /войти/i }).click();
  await expect(page).not.toHaveURL(/\/login/);
}
