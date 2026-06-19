# Phase 2c: 前端牌桌面 UI — Implementation Plan

> **目标:** 手机优先的扑克牌桌面 UI，含牌面渲染、玩家座位、动作面板、倒计时

---

## 架构

```
web/src/
  components/
    poker/
      PlayingCard.vue       [NEW] 单张扑克牌（正面/背面）
      CardFace.vue          [NEW] 牌面内部（花色+点数，CSS 渲染）
      CommunityCards.vue    [NEW] 公共牌行（5格）
      PlayerSeat.vue        [NEW] 单个玩家座位
      PokerTable.vue        [NEW] 牌桌面布局（椭圆形座位排列）
      ActionPanel.vue       [NEW] 动作按钮面板
      HandResult.vue        [NEW] 摊牌结果显示
  stores/
    room.ts                 [MODIFY] isMyTurn 计算属性完善
  views/
    RoomView.vue            [MODIFY] 集成 PokerTable
```

---

### Task 1: PlayingCard + CardFace

**Files:**
- Create: `web/src/components/poker/CardFace.vue`
- Create: `web/src/components/poker/PlayingCard.vue`
- Create: `web/src/components/poker/__tests__/PlayingCard.test.ts`

**CardFace** — 纯展示组件
- Props: `rank: string` (A/K/Q/J/T/9-2), `suit: string` (h/d/c/s)
- 用 CSS 渲染数字+花色符号
- 花色对应颜色: h/d = 红, c/s = 黑

**PlayingCard** — 包装组件
- Props: `card: string | null` ("Ah" 格式), `faceUp: boolean`
- null/faceUp=false → 显示灰色牌背
- 有值+faceUp=true → 显示 CardFace

**测试:** vitest + @vue/test-utils
- null card renders back
- faceUp=false renders back
- faceUp=true renders rank+suit
- suits render correct colors

---

### Task 2: PokerTable

**Files:**
- Create: `web/src/components/poker/PokerTable.vue`
- Create: `web/src/components/poker/__tests__/PokerTable.test.ts`

纯布局组件，不关心数据来源。

- Props:
  - `players: PlayerSeatData[]`
  - `communityCards: string[]`
  - `pot: number`
  - `dealerIndex: number`
  - `currentPlayerIndex: number`
  - `myPlayerId: string`

- Slots/default: 子组件透传

**PlayerSeatData:**
```
{ playerId, nickname, chips, betInRound, folded, allIn, seatIndex, holeCards, isDealer }
```

布局: 椭圆形桌面，玩家分布在上下两侧（手机竖屏适配）:
```
       [P2]  [P3]  [P4]
    [P1]  [CCCCC]  [P5]
         [P0] [YOU]
           POT: xxx
```

**测试:** seats rendered, community card slots, pot displayed

---

### Task 3: PlayerSeat

**Files:**
- Create: `web/src/components/poker/PlayerSeat.vue`
- Create: `web/src/components/poker/__tests__/PlayerSeat.test.ts`

- Props: 全部 seatData
- 显示: 昵称、筹码、当前下注、折叠遮罩、all-in 标记、dealer 标记
- 高亮: currentPlayer 边框发光
- 自己: 显示底牌
- 其他人: 显示 ? ? 或牌背

---

### Task 4: ActionPanel

**Files:**
- Create: `web/src/components/poker/ActionPanel.vue`
- Create: `web/src/components/poker/__tests__/ActionPanel.test.ts`

- Props:
  - `isMyTurn: boolean`
  - `legalActions: string[]` (FOLD/CHECK/CALL/BET/RAISE)
  - `currentBet: number`
  - `myBetInRound: number`
  - `bigBlind: number`
  - `timeLeftSec: number`
  - `myChips: number`

- Emits: `action(type: string, amount?: number)`

- 按钮布局:
  ```
  [FOLD] [CHECK/CALL] [RAISE/BET]
         [Slider + Amount Input]
  ```

- 非自己回合: 显示倒计时 + 灰色面板

**测试:**
- isMyTurn=false: buttons disabled, timer shown
- isMyTurn=true, canCheck: CHECK shown, CALL hidden
- isMyTurn=true, mustCall: CALL shown, CHECK hidden
- bet/raise shows amount slider

---

### Task 5: HandResult

**Files:**
- Create: `web/src/components/poker/HandResult.vue`
- Create: `web/src/components/poker/__tests__/HandResult.test.ts`

- Props: `winners: { playerId, nickname, handName, amount }[]`
- 显示赢家+牌型+赢得金额
- 底部按钮: "下一局" (仅房主)

---

### Task 6: 集成到 RoomView + Store 更新

- `room.ts`: 完善 `isMyTurn` 计算属性
- `RoomView.vue`: 
  - WAITING 状态 → 现有等待界面
  - PLAYING 状态 → PokerTable + ActionPanel
  - FINISHED/SHOWDOWN → HandResult
- WebSocket 订阅: `/topic/room/{roomId}/game` 用于游戏状态推送
- STOMP 发送: `/app/game/{roomId}/action` 用于发送动作

---

## TDD 流程

1. RED: 写 vitest 测试 → `npm test -- --run` 确认失败
2. GREEN: 写 Vue 组件实现 → 测试通过
3. COMMIT
