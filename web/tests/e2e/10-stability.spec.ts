import { test, expect } from '@playwright/test';
import { createRoom, addBotAndStart } from './helpers/poker-helpers';

test.describe('性能 & 稳定性', () => {

  test('#104: 多人对局不卡顿', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    // 添加 6 个 bot（7 人对局）
    await addBotAndStart(page, 6);

    // 等待牌桌出现
    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 15000 });

    // 验证页面仍然响应（座位可见）
    const seats = page.locator('[data-test="seat"]');
    await expect(async () => {
      const count = await seats.count();
      expect(count).toBeGreaterThanOrEqual(7);
    }).toPass({ timeout: 20000 });

    // 验证底池可见
    await expect(page.locator('.pot')).toBeVisible({ timeout: 10000 });
    await ctx.close();
  });

  test('#107: 长时间挂机后页面仍在', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 等 30 秒
    await page.waitForTimeout(30000);

    // 页面应仍然存在
    const table = page.locator('[data-test="poker-table"]');
    const gameView = page.locator('[data-test="game-view"]');
    const waitView = page.locator('[data-test="room-waiting-view"]');
    // 三个中至少一个可见
    await expect(table.or(gameView).or(waitView).first()).toBeVisible({ timeout: 5000 });
    await ctx.close();
  });

  test('#106: 连续多局不崩溃', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    // 等待第 1 局结束
    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });

    // 等待自动续局或手动下一局
    const nextBtn = page.locator('[data-test="btn-next-hand"]');
    if (await nextBtn.isVisible({ timeout: 10000 }).catch(() => false)) {
      await nextBtn.click();
    }

    // 等待牌桌重新出现（第 2 局开始）
    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 30000 });
    await ctx.close();
  });
});
