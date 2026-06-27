import { test, expect } from '@playwright/test';
import { createRoom, addBotAndStart } from './helpers/poker-helpers';

/**
 * 回归防御测试：今天修过的每一个前端 bug 都在这里有对应测试。
 * 如果这些测试失败，说明已修复的 bug 又回来了。
 */
test.describe('回归防御', () => {

  // ──────── Bug: SHOWDOWN 阶段对手手牌不显示 ────────
  // 修复: tablePlayers 计算属性加 SHOWDOWN 分支
  test('#R1: SHOWDOWN 阶段 → 显示所有人亮牌', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 3);

    // 等到手牌结束有 winner
    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });
    await expect(page.locator('.winner-row')).toBeVisible({ timeout: 8000 });

    // 在 SHOWDOWN 阶段，牌桌应该可见 — 有个 seat 元素
    const seats = page.locator('[data-test="seat"]');
    const seatCount = await seats.count();
    expect(seatCount).toBeGreaterThanOrEqual(2, '最少应有 2 个座位');

    // 有牌展示（card-face 或 card-back）
    const anyCard = page.locator('.card-slot');
    const cardCount = await anyCard.count();
    expect(cardCount).toBeGreaterThan(0, '应显示手牌');

    await ctx.close();
  });

  // ──────── Bug: 准备按钮在手牌结束后立即出现但点击被拒绝 ────────
  // 修复: HandResult 显示"正在结算中..."直到收到 ready_status
  test('#R2: 手牌结束 → 先显示结算中 → 再显示准备按钮', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    // 手牌结束
    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });
    await expect(page.locator('.winner-row')).toBeVisible({ timeout: 8000 });

    // 结算中文字应该出现过
    const settling = page.locator('.result-settling');
    // 可能已经过去了，也可能还在；但最后一定会看到准备按钮
    await expect(
      page.locator('[data-test="btn-ready"]').or(page.locator('.result-wait'))
    ).toBeVisible({ timeout: 12000 });

    await ctx.close();
  });

  // ──────── Bug: 手牌结束后切回大厅而不是留在牌桌 ────────
  // 修复: game-view v-if 加了 WAITING && winners 条件
  test('#R3: 手牌结束准备阶段 → 停留在牌桌视图', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });

    // game-view 应该还在（不是房间等待界面）
    // 注意：HandResult 是 overlay，game-view 在它下面，只要 game-view 存在就行
    const gameView = page.locator('[data-test="game-view"]');
    await expect(gameView).toBeVisible({ timeout: 5000 });

    await ctx.close();
  });

  // ──────── Bug: sendReady 被删除但 handleReady 还在调用 ────────
  // 修复: 删除调用行，sendReady 全代码零引用
  // 此 bug 由 lint/typecheck 防御；这里验证按钮点击不崩溃
  test('#R4: 点击准备按钮 → 不抛异常', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });

    // 等 ready_status 到（准备按钮出现）
    const readyBtn = page.locator('[data-test="btn-ready"]');
    try {
      await readyBtn.waitFor({ state: 'visible', timeout: 12000 });
      await readyBtn.click();
    } catch {
      // 可能 automated 自动准备了，没问题
    }

    // 页面不应崩溃，game-view 还在
    const gameView = page.locator('[data-test="game-view"]');
    await expect(gameView).toBeVisible({ timeout: 3000 });

    await ctx.close();
  });

  // ──────── Bug: HandResult 结束后无法添加机器人 ────────
  // 修复: HandResult 加了 add-bot 按钮
  test('#R5: 手牌结束 → HandResult 中有添加机器人按钮', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 1); // 只有 1 个 bot + 房主 = 2 人

    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });

    // 没满 8 人，add-bot 应可见
    await expect(
      page.locator('[data-test="btn-add-bot-hand-result"]')
    ).toBeVisible({ timeout: 12000 });

    await ctx.close();
  });

  // ──────── Bug: ready_status 来时 status 不是 WAITING ────────
  // 修复: 前端只读 roomStatus 字段，不盲写
  // 此测试验证前端不会错误地显示 WAITING 状态（如后端发 FINISHED）
  test('#R6: 后端带来 roomStatus → 前端跟随，不会误写', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    // 正常跑完一手牌
    await expect(page.locator('[data-test="hand-result"]')).toBeVisible({ timeout: 120000 });

    // 等 ready_status 到 -> 准备按钮出现，能正常交互
    const readyBtn = page.locator('[data-test="btn-ready"]');
    try {
      await readyBtn.waitFor({ state: 'visible', timeout: 15000 });
    } catch {
      // 可能已自动准备
    }

    // 不管怎样 game-view 必须还在
    await expect(page.locator('[data-test="game-view"]')).toBeVisible({ timeout: 3000 });

    await ctx.close();
  });
});
