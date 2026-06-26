import { test, expect } from '@playwright/test';
import { createRoom, joinRoom } from './helpers/poker-helpers';

test('创建房间并显示房间号', async ({ browser }) => {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const roomId = await createRoom(page, '测试玩家A');
  expect(roomId).toBeTruthy();
  expect(roomId.length).toBeGreaterThanOrEqual(4);
  await ctx.close();
});

test('两个玩家加入同一房间', async ({ browser }) => {
  const ctxA = await browser.newContext();
  const pageA = await ctxA.newPage();
  const roomId = await createRoom(pageA, '玩家A');

  const ctxB = await browser.newContext();
  const pageB = await ctxB.newPage();
  await joinRoom(pageB, '玩家B', roomId);

  await expect(pageA.locator('text=玩家B')).toBeVisible({ timeout: 5000 });
  await expect(pageB.locator('text=玩家A')).toBeVisible({ timeout: 5000 });

  await ctxA.close();
  await ctxB.close();
});

test('房主添加机器人并看到机器人出现在列表中', async ({ browser }) => {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await createRoom(page, '房主');
  await page.click('[data-test="btn-add-bot"]');
  await page.waitForTimeout(500);
  await expect(page.locator('text=🤖')).toBeVisible({ timeout: 3000 });
  await ctx.close();
});
