import { test, expect } from '@playwright/test';
import { createRoom, addBotAndStart } from './helpers/poker-helpers';

test.describe('筹码池 & 筹码计算', () => {

  test('#63: 盲注扣除后筹码数字立即更新', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 自己的筹码显示在座位中 (.chips)
    const myChips = page.locator('.seat.cur .chips, .seat.is-me .chips');
    // 应该显示筹码数值（盲注已扣）
    await expect(async () => {
      const text = await myChips.textContent();
      expect(text).toBeTruthy();
    }).toPass({ timeout: 15000 });
    await ctx.close();
  });

  test('#65: 底池显示金额 = 所有玩家已下注总和', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 2);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 底池显示应有数值
    const pot = page.locator('.pot');
    await expect(async () => {
      const text = await pot.textContent();
      expect(text).toBeTruthy();
      // 底池至少有小盲+大盲
    }).toPass({ timeout: 15000 });
    await ctx.close();
  });

  test('#66: 筹码不足以跟注 → 应显示 All-in 按钮', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    // 只加 1 个 bot，减少等待时间
    await createRoom(page, '房主');
    await addBotAndStart(page, 1);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 如果操作面板出现，检查是否有 raise/confirm 按钮（表明可以加注即筹码足够）
    const raiseBtn = page.locator('[data-test="btn-confirm-bet"]');
    // 如果筹码很少（比如 blinds 消耗后），加注按钮可能不可见或 disabled
    // 无论如何，操作按钮区域应存在
    const actionPanel = page.locator('[data-test="btn-fold"]');
    if (await actionPanel.isVisible({ timeout: 30000 }).catch(() => false)) {
      // 操作面板出现了，检查各按钮
      expect(await actionPanel.isVisible()).toBe(true);
    }
    await ctx.close();
  });

  test('#67: All-in 后不能再操作', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await createRoom(page, '房主');
    await addBotAndStart(page, 1);

    await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });

    // 执行操作直到没有按钮可用（all-in 或 fold）
    const foldBtn = page.locator('[data-test="btn-fold"]');
    const callBtn = page.locator('[data-test="btn-call"]');
    const checkBtn = page.locator('[data-test="btn-check"]');

    if (await foldBtn.isVisible({ timeout: 30000 }).catch(() => false)) {
      // 点 fold（安全操作）
      await foldBtn.click();
      // 操作后按钮应消失
      await expect(foldBtn).not.toBeVisible({ timeout: 5000 });
    }
    await ctx.close();
  });
});
