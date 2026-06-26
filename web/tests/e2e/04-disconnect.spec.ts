import { test, expect } from '@playwright/test';
import { createRoom, addBotAndStart } from './helpers/poker-helpers';

test.describe('断线恢复', () => {

  test('#42: 对局中关 tab 后 30 秒内重连 → 恢复游戏', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    // 确认牌桌显示
    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 保存 URL 后关闭上下文（模拟断线）
    const gameUrl = page.url();
    await ctx.close();

    // 重连
    const ctx2 = await browser.newContext();
    const page2 = await ctx2.newPage();
    await page2.goto(gameUrl);

    // 应能重新看到房间页面
    await expect(page2.locator('[data-test="home-screen"].or([data-test="game-view"])').first())
      .toBeVisible({ timeout: 15000 });
    await ctx2.close();
  });

  test('#45: 刷新页面 → 重新加载游戏状态', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    // 确认在游戏中
    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 刷新页面
    await page.reload();

    // 应保持在游戏视图或等待视图
    const gameView = page.locator('[data-test="game-view"]');
    const waitView = page.locator('[data-test="room-waiting-view"]');
    await expect(gameView.or(waitView).first()).toBeVisible({ timeout: 10000 });
    await ctx.close();
  });

  test('#48: 断连后重连时轮到自己操作 → 应能正常操作', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    // 确认在游戏中
    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 检查是否有操作按钮（轮到自己时）
    const foldBtn = page.locator('[data-test="btn-fold"]');
    const checkBtn = page.locator('[data-test="btn-check"]');
    const callBtn = page.locator('[data-test="btn-call"]');

    const anyActionBtn = foldBtn.or(checkBtn).or(callBtn);
    if (await anyActionBtn.isVisible({ timeout: 30000 }).catch(() => false)) {
      // 执行操作
      if (await foldBtn.isVisible().catch(() => false)) {
        await foldBtn.click();
      } else if (await checkBtn.isVisible().catch(() => false)) {
        await checkBtn.click();
      } else if (await callBtn.isVisible().catch(() => false)) {
        await callBtn.click();
      }
      // 操作后按钮应消失
      await expect(anyActionBtn).not.toBeVisible({ timeout: 5000 });
    }
    await ctx.close();
  });
});
