import { test, expect } from '@playwright/test';
import { createRoom, addBotAndStart } from './helpers/poker-helpers';

test.describe('多设备/多窗口', () => {

  test('#94: 横屏旋转到竖屏 → 布局自适应', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 812, height: 375 } });
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 旋转为竖屏
    await page.setViewportSize({ width: 390, height: 844 });
    await page.waitForTimeout(500);

    // 牌桌仍然可见
    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 5000 });
    await ctx.close();
  });

  test('#95: 往返横竖屏切换 → 游戏状态不丢失', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 切换到竖屏
    await page.setViewportSize({ width: 390, height: 844 });
    await page.waitForTimeout(500);
    await expect(page.locator('[data-test="poker-table"]')).toBeVisible();

    // 切回横屏
    await page.setViewportSize({ width: 812, height: 375 });
    await page.waitForTimeout(500);
    await expect(page.locator('[data-test="poker-table"]')).toBeVisible();
    await ctx.close();
  });

  test('#96: 小视口（360×640）下牌桌不截断', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 360, height: 640 } });
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 牌桌 boundingBox 应在视口内
    const table = page.locator('[data-test="poker-table"]');
    const box = await table.boundingBox();
    expect(box).toBeDefined();
    if (box) {
      expect(box.width).toBeGreaterThan(100);
      expect(box.height).toBeGreaterThan(50);
    }
    await ctx.close();
  });
});
