import { test, expect } from '@playwright/test';
import { createRoom, addBotAndStart } from './helpers/poker-helpers';

test('发牌后每个玩家有 2 张底牌', async ({ browser }) => {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await createRoom(page, '房主');
  await addBotAndStart(page, 2);

  // 等待牌桌出现
  await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

  // 自己的底牌可见
  // PlayerSeat 组件用 .seat 类，自己的座位应该有 .cur 高亮
  // 或者用 .cards 内的 PlayingCard 组件验证
  const myCards = page.locator('.seat.cur .cards .card');
  await expect(myCards).toHaveCount(2, { timeout: 5000 });
});

test('FLOP 后 3 张公共牌', async ({ browser }) => {
  const page = await (await browser.newContext()).newPage();
  await createRoom(page, '房主');
  await addBotAndStart(page, 2);

  // 等待至少 3 张公共牌激活（社区牌卡槽 .card-slot.active）
  const activeSlots = page.locator('[data-test="community-cards"] .card-slot.active');
  await expect(async () => {
    const count = await activeSlots.count();
    expect(count).toBeGreaterThanOrEqual(3);
  }).toPass({ timeout: 90000 });
});

test('局结束后显示 HandResult', async ({ browser }) => {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await createRoom(page, '房主');
  await addBotAndStart(page, 2);

  // 等待 HandResult 出现（局结束），允许更长时间
  await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });
  await ctx.close();
});
