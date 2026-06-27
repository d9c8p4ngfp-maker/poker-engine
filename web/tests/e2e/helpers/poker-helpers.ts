import { Browser, Page, BrowserContext, expect } from '@playwright/test';

/**
 * 创建独立的浏览器上下文（代表一个玩家）
 */
export async function newPlayerContext(browser: Browser): Promise<{
  context: BrowserContext;
  page: Page;
}> {
  const context = await browser.newContext();
  const page = await context.newPage();
  return { context, page };
}

/**
 * 创建房间的完整流程（两步）：
 *   首页填昵称 → 点创建 → 跳转配置页 → 提交 → 进入房间
 * 返回房间 URL 中的 roomId
 */
export async function createRoom(page: Page, nickname: string): Promise<string> {
  await page.goto('/');
  await page.fill('input[placeholder*="昵称"]', nickname);
  await page.click('[data-test="btn-create"]');

  // 配置页
  await expect(page.locator('[data-test="btn-submit"]')).toBeVisible({ timeout: 5000 });
  await page.click('[data-test="btn-submit"]');

  await page.waitForURL(/\/room\/.+/);
  const url = page.url();
  return url.split('/room/')[1];
}

/**
 * 加入房间
 */
export async function joinRoom(page: Page, nickname: string, roomId: string): Promise<void> {
  await page.goto('/');
  await page.fill('input[placeholder*="昵称"]', nickname);
  await page.fill('input[placeholder*="房间"]', roomId);
  await page.click('[data-test="btn-join"]');
  await page.waitForURL(/\/room\/.+/);
}

/**
 * 房主添加机器人并开始游戏
 */
export async function addBotAndStart(page: Page, botCount: number = 2): Promise<void> {
  for (let i = 0; i < botCount; i++) {
    await page.click('[data-test="btn-add-bot"]');
    await page.waitForTimeout(500);
  }
  await page.click('[data-test="btn-start-game"]');
  await expect(page.locator('[data-test="poker-table"]')).toBeVisible({ timeout: 10000 });
}

/**
 * 执行操作并等待下个状态
 */
export async function performAction(
  page: Page,
  action: 'fold' | 'check' | 'call'
): Promise<void> {
  const btnMap: Record<string, string> = {
    fold: 'btn-fold',
    check: 'btn-check',
    call: 'btn-call',
  };
  await page.click(`[data-test="${btnMap[action]}"]`);
  await expect(page.locator(`[data-test="${btnMap[action]}"]`)).not.toBeVisible({ timeout: 5000 });
}

/**
 * 关闭玩家上下文
 */
export async function closePlayer(context: BrowserContext): Promise<void> {
  await context.close();
}
