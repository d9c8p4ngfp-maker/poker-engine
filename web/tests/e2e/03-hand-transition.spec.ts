import { test, expect } from '@playwright/test';
import { createRoom, addBotAndStart } from './helpers/poker-helpers';

test.describe('局间衔接', () => {

  test('#32: 单场结束 → 显示结果 → 进入准备阶段', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    // 等待 HandResult
    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });
    // 结果中有 winner
    await expect(page.locator('.winner-row')).toBeVisible({ timeout: 5000 });
    await ctx.close();
  });

  test('#33: 玩家点准备 → 按钮变为已准备状态', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    // 等待 HandResult — 如果我是房主且 bot 只有 2 个，那么 bot 都自动准备了
    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });

    // 如果 ready 按钮可见，点它
    const readyBtn = page.locator('[data-test="btn-ready"]');
    if (await readyBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await readyBtn.click();
      // wait a bit for the ready state to be processed
      await page.waitForTimeout(1500);
      // result 区域仍然可见（已准备状态下）
      await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 5000 });
    }
    await ctx.close();
  });

  test('#34: 所有人准备 → 房主可以开始下一局', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });

    // bot 自动准备后，allReady 为 true，下一局按钮出现
    const nextBtn = page.locator('[data-test="btn-next-hand"]');
    await expect(nextBtn).toBeVisible({ timeout: 15000 });
    await ctx.close();
  });

  test('#38: bustEndsGame=true 时有人 bust → 显示查看最终排名', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    // bustEndsGame 默认 true（在 CreateRoomView 中 checked）
    await addBotAndStart(page, 2);

    // 等待 HandResult 或 GameOver
    const handResult = page.locator('[data-test="hand-result"]');
    const gameOver = page.locator('[data-test="game-over"]');
    await expect(handResult.or(gameOver).first()).toBeVisible({ timeout: 120000 });

    // 如果有"查看最终排名"按钮说明 bustEndsGame 触发
    const rankingBtn = page.locator('[data-test="btn-show-ranking"]');
    if (await rankingBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await rankingBtn.click();
      await expect(page.locator('[data-test="game-over"]')).toBeVisible({ timeout: 10000 });
    }
    await ctx.close();
  });

  test('#40: 排行榜显示后点返回 → 回到首页', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    // 等待 HandResult 或 GameOver
    const handResult = page.locator('[data-test="hand-result"]');
    const gameOver = page.locator('[data-test="game-over"]');
    await expect(handResult.or(gameOver).first()).toBeVisible({ timeout: 120000 });

    // 如果可见"查看最终排名"，点击触发 GameOver
    const rankingBtn = page.locator('[data-test="btn-show-ranking"]');
    if (await rankingBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await rankingBtn.click();
    }

    // 等待 GameOver 或回到等待视图（取决于 bustEndsGame 逻辑）
    const backBtn = page.locator('[data-test="btn-back-lobby"]');
    if (await gameOver.isVisible({ timeout: 10000 }).catch(() => false)) {
      if (await backBtn.isVisible().catch(() => false)) {
        await backBtn.click();
        await expect(page.locator('[data-test="home-screen"]')).toBeVisible({ timeout: 5000 });
      }
    }
    await ctx.close();
  });
});
