import { test, expect } from '@playwright/test';
import { createRoom, addBotAndStart } from './helpers/poker-helpers';

test.describe('实时同步验证', () => {

  test('#99: 公共牌发出后可见', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 等待社区牌出现（至少有卡槽）
    const communityCards = page.locator('[data-test="community-cards"]');
    await expect(communityCards).toBeVisible({ timeout: 15000 });
    await ctx.close();
  });

  test('#100: 底池金额显示', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 底池有数值
    const pot = page.locator('.pot');
    await expect(pot).toBeVisible({ timeout: 10000 });
    const potText = await pot.textContent();
    expect(potText).toBeTruthy();
    // 底池至少包含数字
    expect(potText!).toMatch(/\d/);
    await ctx.close();
  });

  test('#102: 游戏阶段文本显示', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 游戏阶段标签
    const phase = page.locator('.game-phase');
    await expect(phase).toBeVisible({ timeout: 5000 });
    const phaseText = await phase.textContent();
    expect(phaseText).toBeTruthy();
    await ctx.close();
  });

  test('#103: 玩家数量在页面显示', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 座位数量 ≥ 3（房主 + 2 bot）
    const seats = page.locator('[data-test="seat"]');
    await expect(async () => {
      const count = await seats.count();
      expect(count).toBeGreaterThanOrEqual(3);
    }).toPass({ timeout: 15000 });
    await ctx.close();
  });
});
