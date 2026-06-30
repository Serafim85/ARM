import { test, expect } from '@playwright/test';
import { isStackReachable } from '../../helpers/stack';

test.describe('Stack smoke', () => {
  test('frontend responds with status < 500', async ({ page, baseURL }) => {
    test.skip(!(await isStackReachable(baseURL!)), 'Start stack or set E2E_STACK_UP=1');

    const response = await page.goto(baseURL ?? '/');
    expect(response?.status()).toBeLessThan(500);
  });
});
