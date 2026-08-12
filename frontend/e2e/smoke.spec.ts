import { expect, test } from '@playwright/test';

test.describe('app shell', () => {
  test('home page loads with both practice modes and the disclaimer', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByRole('link', { name: /listening practice/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /speaking practice/i })).toBeVisible();
    await expect(
      page.getByText(/not affiliated with, authorized by, or endorsed by CELPIP/i),
    ).toBeVisible();
  });

  test('header navigation reaches both modes', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('link', { name: 'Listening', exact: true }).click();
    await expect(page.getByRole('heading', { name: /listening practice/i })).toBeVisible();

    await page.getByRole('link', { name: 'Speaking', exact: true }).click();
    await expect(page.getByRole('heading', { name: /speaking practice/i })).toBeVisible();
  });
});
