# Phase 3: 前后端打通 Implementation Plan

> **目标:** WebSocket 游戏消息 → GameSessionService → GameEngine → 广播快照给所有玩家

---

## 架构

```
STOMP /app/game/{roomId}/action
  ↓
GameMessageController
  ↓
GameSessionService (持 per-room GameState, synchronized)
  ↓
GameEngine.processAction(state, action, amount)
  ↓
GameStateSnapshot → BroadcastService → /topic/room/{roomId}/game (公共)
                                     → /user/{id}/queue/game (私人，含底牌)
```

新增文件：
```
server/src/main/java/com/first/poker/service/
  GameSessionService.java           [NEW] per-room GameState 持有者
  GameStateSnapshot.java            [NEW] GameState → Map<String,Object>
  GameTimeoutScheduler.java         [NEW] 超时自动 Fold
server/src/main/java/com/first/poker/controller/
  GameMessageController.java        [NEW] STOMP 游戏消息端点
server/src/main/java/com/first/poker/listener/
  GameDisconnectHandler.java        [NEW] 断连自动 Fold
```

---

## Task 1: 修复 GameEngine Showdown 筹码发放 🔴

**Bug:** `GameEngine.processAction` 在 Showdown 时调用了 `HandResolver.distributePots()` 但结果未应用到 `GamePlayerState.chips`，也没有产出 winners 列表。

**Fix:**
1. `GameEngine` 返回类型从 `ActionResult` 升级，增加 `List<WinnerInfo> winners`
2. Showdown 时将 pots 金额加到对应赢家
3. 补充单元测试

**Files:**
- Modify: `GameEngine.java` — 返回类型 + Showdown 逻辑
- Modify: `GameEngineTest.java` — 补充筹码发放断言

---

## Task 2: GameStateSnapshot

将 `GameState` + 玩家上下文转为前端可消费的 `Map<String, Object>`。

```
static Map<String, Object> buildPublic(GameState state)
static Map<String, Object> buildForPlayer(GameState state, String playerId)
```

**Public Snapshot:** `players[].holeCards` = null, `myHoleCards` = 不含
**Private Snapshot:** `myHoleCards` = 该玩家的底牌

**测试:** 底牌不被泄露到公共快照

**Files:**
- Create: `GameStateSnapshot.java`
- Create: `GameStateSnapshotTest.java`

---

## Task 3: GameSessionService

持有 per-room GameState，提供 start/action 入口。

```
startGame(roomId, playerId): GameState
applyAction(roomId, playerId, action, amount): ActionResult
getSession(roomId): GameSession
```

**GameSession record:**
```
record GameSession(GameState state, int timeoutTaskId, long lastActionTs) {}
```

**并发:** `ConcurrentHashMap<String, GameSession>` + `synchronized (session)` 在 startGame/applyAction 入口

**测试:** 基础流程 → start + action → phase advance

**Files:**
- Create: `GameSessionService.java`
- Create: `GameSessionServiceTest.java`

---

## Task 4: GameMessageController

STOMP 端点：
```
@MessageMapping("/game/{roomId}/action")
@MessageMapping("/game/{roomId}/start")
```

流程：
1. 接收消息
2. 查 Room + 验权限
3. 调用 GameSessionService
4. 构建快照 → 广播

**依赖注入:** RoomService, GameSessionService, BroadcastService, GameStateSnapshot

**测试:** Spring STOMP 集成测试 → 发消息 → 验证广播内容

**Files:**
- Create: `GameMessageController.java`
- Modify: `BroadcastService.java` — 增加 sendToPlayer 方法
- Create: `GameMessageControllerTest.java`

---

## Task 5: 超时 + 断连

**GameTimeoutScheduler:**
```
scheduleTimeout(roomId, playerId, timeoutSec): void
cancelTimeout(roomId): void
```
用 `ScheduledExecutorService`，超时后回调 `GameSessionService.applyAction(roomId, playerId, FOLD, 0)`。

**GameDisconnectHandler:**
```
@EventListener(SessionDisconnectEvent.class)
```
从 session attributes 提取 playerId → 查所在 room → `GameSessionService.handleDisconnect(roomId, playerId)`。

**测试:** mock 超时/断连 → 验证 fold 被调用

**Files:**
- Create: `GameTimeoutScheduler.java`
- Create: `GameDisconnectHandler.java`
- Create: `GameTimeoutSchedulerTest.java`

---

## Task 6: 集成测试

完整一局：3 人 → Preflop → Flop → Turn → River → Showdown → 验证筹码变化。

用 `@SpringBootTest` + `@TestPropertySource` + STOMP 客户端连接。

---

## TDD 流程

每步：RED (写测试→编译失败) → GREEN (实现→测试通过) → COMMIT
