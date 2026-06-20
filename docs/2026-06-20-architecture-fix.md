# 扑克引擎全面架构分析 & 修复方案 v2

> 2026-06-20 | 两轮深度扫描：第一轮覆盖核心游戏流程（7 条链路），第二轮覆盖 REST API / 借贷 / 排队 / WebSocket / 前端状态 / DTO。  
> 共计发现 **13 个严重、21 个中等、11 个轻微 = 45 个问题**。

---

## 总览表

### 🔴 CRITICAL (13)

| ID | 类别 | 问题 | 影响 |
|----|------|------|------|
| C1 | 游戏逻辑 | Dealer 按钮从不轮转 | 盲注位永不轮换 |
| C2 | 资源管理 | 断线处理线程泄漏 | 每断线一次漏一个线程 |
| C3 | 并发 | `processAction` 锁内操作与锁外广播间的 TOCTOU 竞态 | 状态不一致，排错超时 |
| C4 | 并发 | `handleTimeout` 也有锁内操作与锁外广播的竞态 | 同上 |
| C5 | 并发 | `scheduleNextTimeout` 在锁外读取 GameState | 可能为错误的玩家排超时 |
| C6 | 并发 | 断线 handler Phase 2 使用过期 state 做 chip sync | chip 同步到错误的值 |
| C7 | REST API | `addBots` 无锁，游戏进行中添加机器人绕过引擎 | 房间状态与 GameState 不一致 |
| C8 | REST API | `startGame` 在 `findRoom` 和获取锁之间有 TOCTOU 窗口 | 脏读房间状态 |
| C9 | 并发 | `joinRoom` 无锁，与并发开始游戏竞态 | 玩家加入时机错误 |
| C10 | 并发 | `endGame` 在锁被 `endGameAndCleanupLock` 清理后回退到无锁分支 | chip sync 丢失 |
| C11 | 前端状态 | `player_left` 在前端本地直接 filter，无服务端确认 | 状态不一致 |
| C12 | DTO | `GameActionRequest` 无校验注解，null playerId 导致 NPE | 500 错误 |
| C13 | 安全 | 房间密码字段存在但从未校验 | 死代码，假安全 |

### 🟡 MEDIUM (21)

| ID | 类别 | 问题 |
|----|------|------|
| M1 | 前端 | 0 筹码 watch 自动 FOLD 与服务端 `autoPlayBots` 重复提交 |
| M2 | 断线 | `handleLeave` 没取消断线宽限定时器 |
| M3 | 重连 | 重连找不到 room 时静默失败 |
| M4 | 前端 | WebSocket 重连 auto-rejoin 与 `onMounted` refreshRoom 竞态 |
| M5 | 游戏逻辑 | `syncRoomChips` 后 dealerIndex 不递增（配合 C1） |
| M6 | REST API | `getRoom` 无锁读取可变的 Player 字段，返回混合新旧数据 |
| M7 | REST API | `borrowChips` 在锁外重新读取房间去广播，可能拿到过期数据 |
| M8 | REST API | `borrowChips` 不验证 playerId 是否真的在房间里 |
| M9 | 重连 | 重连路径不恢复 QUEUED 玩家的 seatIndex |
| M10 | 房间生命周期 | `leaveRoom` 公开方法无锁，可绕过安全机制 |
| M11 | 游戏逻辑 | 游戏进行中添加的机器人以 ACTIVE 状态存在但不参与当前局 |
| M12 | 借贷 | 游戏中借的筹码在一手牌结束时被 `syncRoomChips` 覆盖 |
| M13 | 排队 | QUEUED 玩家座位分配做了两次（`addPlayer` + `queueAccept`） |
| M14 | 排队 | `queueAccept` 无条件重置筹码为初始值，可能被利用刷筹码 |
| M15 | 排队 | `queue_prompt` 消息前端无 handler，静默忽略 |
| M16 | WebSocket | 固定 2 秒重连间隔，无退避，无限重试 |
| M17 | WebSocket | 重连期间的消息静默丢弃，无队列/重试 |
| M18 | 前端状态 | `updateFromSnapshot` 把本地过期的 `connected` 覆盖服务端值 |
| M19 | 前端状态 | 快照无版本号，乱序到达无法检测 |
| M20 | 并发 | `endGame` 从 `GameDisconnectHandler` 在锁外调用，与解散竞态 |
| M21 | DTO | `CreateRoomRequest` 对 `minPlayers`/`initialChips`/`actionTimeoutSec` 无校验 |

### 🟢 MINOR (11)

| ID | 类别 | 问题 |
|----|------|------|
| N1 | 代码质量 | `GameMessageController` 有未使用的 import |
| N2 | 代码质量 | 前端 `PlayerView` 接口在 `RoomView.vue` 和 `room.ts` 各定义一次 |
| N3 | 代码质量 | `GameMessageController` 的 private 包装方法多余 |
| N4 | 并发 | 理论上的 room ID 碰撞（`ConcurrentHashMap.put` 覆盖） |
| N5 | 代码质量 | `borrow` 端点用 raw `Map` 而不是类型化 DTO |
| N6 | 房间生命周期 | `transferOwnership` 返回 null 到房间解散之间有一个无主窗口 |
| N7 | 排队 | `seatQueuedPlayers` 的 10 秒自动接受逻辑未实现 |
| N8 | 借贷 | `Player.borrowCount` 是普通 `int`，锁外读取存在数据竞争 |
| N9 | WebSocket | 无 `onWebSocketClose`/`onWebSocketError` handler |
| N10 | WebSocket | `connect()` Promise 在初始失败后不再 resolve |
| N11 | 并发 | `ReentrantLock` 默认不公平，理论上有线程饥饿 |

---

## 第一层：核心游戏流程（7 条链路分析）

### 流程 1: 人类玩家操作 (CHECK/CALL/BET/RAISE/FOLD)

```
前端: send(/app/game/{roomId}/action)
  → GameMessageController.processAction
    → gameSession.applyAction (lock)        ✓
    (unlock)
    → broadcastGameState                    ✗ 锁外
    → if handComplete:
        cancelTimeout + endGame + winners   ✗ 锁外
    → autoPlayBots                          ✗ 锁外
    → scheduleNextTimeout(getState())       ✗ 锁外，TOCTOU
```

### 流程 2: 机器人自动操作链

```
autoPlayBots(roomId):
  loop:
    → getState(roomId)                      ✗ 锁外读取
    → if bot: applyAction (加锁/解锁)
    → broadcastGameState                    ✗ 锁外
    → if human: break
  → scheduleNextTimeout(getState())         ✗ 锁外
```

### 流程 3: 超时

```
GameTimeoutScheduler:
  → handleTimeout(roomId, playerId)
    → executeWithLock:
        applyAction(FOLD)                   ✓
        broadcastGameState                  ✓
        autoPlayBots                        ✓
        scheduleNextTimeout                 ✓
```

### 流程 4: 断线

```
SessionDisconnectEvent:
  → Phase 1 (lock):
        mark DISCONNECTED, auto-FOLD
        capture ActionResult → AtomicRef
    (unlock)
  → Phase 2 (锁外):
        broadcastGameState(fr.state())      ✗ 过期 state
        if handComplete: cancelTimeout + endGame + winners ✗ 锁外
        else: autoPlayBots + scheduleNextTimeout ✗ 锁外
  → Phase 3: 宽限定时器
        new SingleThreadScheduledExecutor   ✗ 线程泄漏
```

### 流程 5: 一手牌结束

```
result.handComplete():
  → cancelTimeout                           ✗ 锁外
  → endGame(lock) → syncRoomChips
  → checkGameOver                           ✗ 锁外
  → broadcastWinners                        ✗ 锁外
```

### 流程 6: 开始游戏

```
startGame(roomId):
  → roomService.findRoom (无锁)
  → gameSession.startGame (加锁)           ✓
  → broadcastGameState                      ✗ 锁外
  → autoPlayBots                            ✗ 锁外
  → scheduleNextTimeout                     ✗ 锁外
```

### 流程 7: 重连

```
前端 WebSocket 重连:
  watch(connected):
    → fetch POST /api/rooms/{roomId}/join   ✗ 无锁
    → onReconnect (加锁)                    ✓
    → refreshRoom (GET, 无锁)
```

---

## 🔴 CRITICAL 详情

### C1 — Dealer 按钮从不轮转

`Room.dealerIndex` 初始化为 0 后再也没有被修改过。每局游戏都传入同一个值，盲注位永不轮换。

```java
// Room.java:28
this.dealerIndex = 0;

// GameSessionService.java:56
GameEngine.startHand(players, room.getDealerIndex(), room.getConfig());
```

**修复** — 在 `syncRoomChips` 末尾递增：

```java
int activeCount = (int) room.getPlayers().stream()
    .filter(p -> p.getStatus() != PlayerStatus.LEFT && p.getChips() > 0).count();
if (activeCount > 0) room.setDealerIndex((room.getDealerIndex() + 1) % activeCount);
```

---

### C2 — 断线处理线程泄漏

每次 WebSocket 断开都 `new SingleThreadScheduledExecutor()`。重连时只取消 timer，不 shutdown executor——线程永久泄漏。

```java
// GameDisconnectHandler.java:140
var executor = Executors.newSingleThreadScheduledExecutor();
```

**修复** — 共享线程池：

```java
private final ScheduledExecutorService graceExecutor =
    Executors.newScheduledThreadPool(4);
// 去掉 executor.shutdown()
```

---

### C3/C4/C5 — TOCTOU 竞态

核心问题：`processAction` 在锁内完成状态变更，然后**锁释放后才**做广播、机器人、排超时。并发断线/超时可能在窗口期内改写状态。

**竞态场景**：

```
T1: player A 的 CHECK 成功，锁释放
T2: player B 断线 → auto-FOLD → handComplete → endGame → sessions.remove
T3: processAction 继续 → getState() 返回 null → scheduleNextTimeout 不排 → 下局无超时
```

**修复** — 创建 `handleActionResult` 方法，在同一个锁调用中原子化：

```java
public void handleActionResult(String roomId, GameEngine.ActionResult result) {
    boolean handComplete;
    try {
        broadcastGameState(roomId, result.state());
        handComplete = result.handComplete();
    } catch (Exception e) {
        System.err.println("[handleActionResult] broadcast failed: " + e.getMessage());
        // broadcast 失败不能阻止游戏推进 — 继续走后续逻辑
        handComplete = result.handComplete();
    }

    if (handComplete) {
        timeoutScheduler.cancelTimeout(roomId);
        GameState finalState = result.state();
        gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
        if (!checkGameOver(roomId, result) && !result.winners().isEmpty()) {
            try { broadcastWinners(roomId, result); }
            catch (Exception e) { System.err.println("[handleActionResult] winners broadcast failed"); }
        }
        return;
    }

    // Hand not complete: advance bots, then schedule timeout
    autoPlayBots(roomId);

    // autoPlayBots may have called endGame (handComplete inside the loop).
    // After endGame, getState() returns null — that's normal, skip timeout.
    GameState state = gameSession.getState(roomId);
    if (state == null) return;
    if (state.currentPlayerIndex() < 0) return;

    var cp = state.currentPlayer();
    if (cp != null && cp.chips() > 0 && !cp.playerId().startsWith("bot-")
            && !cp.folded() && !cp.allIn())
        timeoutScheduler.scheduleTimeout(roomId, cp.playerId(), 30);
}
```

**`processAction` 进锁**：

```java
@MessageMapping("/game/{roomId}/action")
public void processAction(...) {
    gameSession.executeWithLock(roomId, () -> {
        try {
            var result = gameSession.applyAction(roomId, playerId, action, amount);
            helper.handleActionResult(roomId, result);
        } catch (Throwable e) {
            broadcast.sendToPlayer(playerId, Map.of("error", e.getMessage()));
            // Try to advance game even after error. Finally-block guarantees
            // scheduleNextTimeout runs regardless of whether autoPlayBots throws.
            try {
                helper.autoPlayBots(roomId);
            } catch (Exception inner) {
                System.err.println("[processAction] autoPlayBots threw: " + inner.getMessage());
            } finally {
                helper.scheduleNextTimeout(roomId, gameSession.getState(roomId));
            }
        }
    });
}
```

**`handleTimeout` 委托 `handleActionResult`**（已在锁内）：

```java
public void handleTimeout(String roomId, String playerId) {
    gameSession.executeWithLock(roomId, () -> {
        try {
            var result = gameSession.applyAction(roomId, playerId, GameAction.FOLD, 0);
            handleActionResult(roomId, result);
        } catch (Exception e) {
            System.err.println("[TIMEOUT-ERROR] " + playerId + ": " + e.getMessage());
            // catch 块没有 ActionResult 可用 — 直接推进游戏
            try {
                autoPlayBots(roomId);
            } catch (Exception inner) {
                System.err.println("[TIMEOUT-ERROR-bots] " + inner.getMessage());
            } finally {
                scheduleNextTimeout(roomId, gameSession.getState(roomId));
            }
        }
    });
}
```

---

### C6 — 断线 handler 过期 state

Phase 1 在锁内捕获 `ActionResult`，Phase 2 在锁外使用——此时 state 可能已被并发改写。

**修复** — 合并 Phase 1+2 到单次 `executeWithLock`：

```java
gameSession.executeWithLock(fRoomId, () -> {
    // 标记、auto-FOLD、广播、handleActionResult 全在锁内
    ...
});
// 只有宽限定时器放在锁外
graceExecutor.schedule(() -> { ... }, 300, TimeUnit.SECONDS);
```

---

### C7 — `addBots` 无锁，绕过游戏引擎

```java
// RoomController.java:80-92
public ResponseEntity<?> addBots(@PathVariable String roomId, @RequestParam int count) {
    // 无锁！无锁！无锁！
    var bots = roomService.addBots(roomId, count);
    ...
}
```

游戏进行中添加的机器人进入 `room.getPlayers()` 但不进入当前 `GameState`。一手牌结束时 `syncRoomChips` 根据 GameState 写 chip——机器人不在里面，chip sync 跳过。下一局开始时机器人才被纳入。在此期间房间列表显示不一致。

**修复** — 加锁，如果游戏进行中设置 `QUEUED` 状态而不是 `ACTIVE`。

---

### C8 — `startGame` 的 TOCTOU 窗口

```java
// RoomController.java:94-109
var room = roomService.findRoom(roomId);  // 无锁
...
var state = gameSession.startGame(room, ...); // 内部才取锁
```

`findRoom` 和 `startGame` 获取锁之间有窗口——房间可能被并发解散。

**修复** — 整个 controller 方法进 `executeWithLock`。

---

### C9 — `joinRoom` 无锁

```java
// RoomController.java & RoomService.joinRoom
public Room joinRoom(String roomId, String playerId, String nickname) {
    // 无锁
    var room = registry.findById(roomId);
    ...
    room.addPlayer(player);
}
```

与并发 `startGame` 竞态：判断 `hasActiveSession` 为 false 之后、`addPlayer` 之前，游戏可能已经开始了。

**修复** — 取锁。

---

### C10 — `endGame` 无锁回退分支

```java
// GameSessionService.java:95-113
ReentrantLock lock = roomLocks.get(roomId);
if (lock != null) { lock.lock(); ... }  // 正常路径
else {
    beforeUnlock.run();   // ⚠️ 无锁执行 syncRoomChips
    sessions.remove(roomId);
}
```

`endGameAndCleanupLock` 同时清理锁。如果 `endGame` 在它之后调用，走到无锁分支，chip sync 与并发操作交错。

**修复** — 把 `beforeUnlock` 的执行合并进一个统一的、有锁保护的 `handleActionResult`。

另外 `endGameAndCleanupLock` 本身有 remove-after-unlock 竞态：`roomLocks.remove()` 在 `lock.unlock()` 之后执行，存在窗口。改为**解锁前移除**，或直接不移除锁对象（ReentrantLock 对象本身是轻量的，留着不影响功能）：

```java
// GameSessionService.endGameAndCleanupLock
lock.lock();
try {
    if (beforeUnlock != null) beforeUnlock.run();
    sessions.remove(roomId);
} finally {
    roomLocks.remove(roomId);  // 移到这里 — 在 unlock 之前移除
    lock.unlock();
}
```

---

### C11 — 前端 `player_left` 本地 filter

```typescript
// RoomView.vue:63
if (data.type === 'player_left') {
    roomStore.players = roomStore.players.filter(p => p.playerId !== data.playerId)
    if (data.newOwnerId && data.newOwnerId === userStore.playerId) {
        alert('你已成为新房主')
        refreshRoom()
    }
    return
}
```

完全在本地删除玩家，没有服务端确认。如果消息顺序有问题（比如 SockJS 降级导致乱序），可能删错人。更严重的是：如果当前用户自己被踢，fitler 把自己删了但不跳转页面——用户还在房间里但本地列表里没有自己。

**修复** — 改为调用 `refreshRoom()` 从服务端刷新完整列表。

---

### C12 — `GameActionRequest` 无校验

```java
public class GameActionRequest {
    private String playerId;   // 可为 null → NPE
    private String action;     // 可为 null → NPE
    private int amount;
}
```

无 `@NotNull`、`@NotBlank`。null playerId 在 `GameMessageController` 里走 `equals` 比较直接 NPE → 500。

**修复** — 添加 `@NotBlank` + `@NotNull` + Controller 上 `@Validated`。

---

### C13 — 房间密码从未校验

```java
// JoinRoomRequest.java
private String password;  // 存在，但从未被读取或使用
```

房间可以设密码，但 `joinRoom` 从不对比密码——设了也没用。要么删字段，要么实现校验。

**修复** — 在 `RoomService.joinRoom` 中加上 `if (room.getPassword() != null && !room.getPassword().equals(req.getPassword())) throw ...`。

---

## 🟡 MEDIUM 详情（核心 8 个）

### M1 — 前端 0 筹码 watch 与服务端重复 FOLD

删掉这个 watch，0 筹码自动 FOLD 由服务端 `autoPlayBots` 接管。

### M2 — `handleLeave` 不取消宽限定时器

`handleLeave` 删除玩家后，如果该玩家之前有排宽限期定时器，到期后操作已经不存在的玩家。

**修复** — 在 `disconnectHandler` 上加 `cancelGraceTimer(playerId)` 方法。

### M3 — 重连失败时静默

前端 `watch(connected)` 的 `joinRoom` 失败只 `console.error`，用户不知道。

**修复** — 加 `alert('重连失败，房间可能已解散')`。

### M4 — 前端双重 join 竞态

`watch(connected)` 和 `onMounted` 的 `refreshRoom` 可能同时触发 join。

**修复** — `joiningLock` 标记。

### M6 — `getRoom` 返回混合新旧数据

`Player.chips` 是普通 `int`，无锁读取可能看到旧值（非 volatile）。

### M7 — `borrowChips` 锁外二次读房间去广播

借筹码时在锁内修改了 chips，但锁外重新 `findRoom` 去构造广播——如果此时 `syncRoomChips` 并发执行，广播的是旧数据。

**修复** — 在锁内构造响应。
### M12 — 游戏中借筹码会被覆盖

如果一手牌进行中玩家借了筹码，`syncRoomChips` 在一手牌结束时会用 GameState 的 chips 覆盖——借款丢失。

**修复** — `syncRoomChips` 应该用 `max(old, new)` 或累计。

### M16 — WebSocket 无限重试无退避

固定 2 秒间隔重连，永不停止。

**修复** — 加 `maxReconnectAttempts`。

---

## 实施计划

> 注：`Room.addPlayer` 当前用 `synchronized(this)`，方案引入的锁是 `ReentrantLock`。两者操作不同对象（`addPlayer` 是 `synchronized` 在 Room 实例上，ReentrantLock 在 `roomLocks` map 里），共用 `CopyOnWriteArrayList` 做底层安全。最终目标：所有 Room 修改走 `executeWithLock`，届时移除 `addPlayer` 的 `synchronized`。过渡期两把锁并存无正确性问题（`CopyOnWriteArrayList` 自身线程安全），但有可见性隐患——在 Phase 2 中统一处理。
>
> 另：`GameTimeoutScheduler` 通过 `GameConfiguration` 以 `@Bean` 注册到 Spring 容器，无需 `@Service` 注解。

### Phase 1 — 线程泄漏 + 小修 (P0)

| # | 问题 | 文件 | 必要测试 |
|---|------|------|----------|
| P1-1 | C2 共享线程池 | `GameDisconnectHandler.java` | 3 次断线 → 重连 → 断言线程数不增长 |
| P1-2 | M2 取消宽限定时器 | `GameDisconnectHandler.java` | 断线 → handleLeave → 断言 timer 被取消 |
| P1-3 | C12 DTO 校验 | `GameActionRequest.java` + `RoomController.java` | null playerId → 400 而非 500 |
| P1-4 | C13 密码校验 / 删字段 | `RoomService.java` | 正确密码进 / 错误密码拒 |

### Phase 2 — 锁一致性重构 (P0)

> C1（dealer 轮转）和 M12（借款不被覆盖）都改 `syncRoomChips`，合并到此 Phase 一起做。

| # | 问题 | 文件 | 必要测试 |
|---|------|------|----------|
| P2-1 | C3/C4/C5 锁内原子化 `handleActionResult`（含 try/finally） | `GameBroadcastHelper.java` | 并发 action + 断线 → 无 NPE、无超时丢失 |
| P2-2 | C3 `processAction` 进锁 + catch 块 try/finally | `GameMessageController.java` | `applyAction` 抛异常 → `scheduleNextTimeout` 仍执行 |
| P2-3 | C4 `handleTimeout` 修正（catch 不调 handleActionResult） | `GameBroadcastHelper.java` | 超时时 `applyAction` 抛异常 → 游戏不卡死 |
| P2-4 | C8 `startGame` 两个入口都进锁 | `GameMessageController.java` + `RoomController.java` | 并发 startGame → 只有第一个成功 |
| P2-5 | C6/C10 断线 handler 合并 Phase 1+2 | `GameDisconnectHandler.java` | 断线触发 handComplete → chip sync 值正确 |
| P2-6 | C7/C9 `addBots` + `joinRoom` 进锁 | `RoomService.java` + `RoomController.java` | 游戏中 addBot → 机器人 QUEUED 而非 ACTIVE |
| P2-7 | C1 dealer 轮转 | `GameBroadcastHelper.java` | 3 玩家 5 局 → dealerIndex 取模递增 |
| P2-8 | M12 `syncRoomChips` 不覆盖借款 | `GameBroadcastHelper.java` | 游戏中借筹码 → 牌局结束 → 筹码不被覆盖 |
| P2-9 | C10 `endGameAndCleanupLock` 解锁前移除 | `GameSessionService.java` | dissolve → 旧锁对象不再被 use-after-free |
| P2-10 | 移除 `Room.addPlayer` 的 `synchronized` | `Room.java` | 确认所有调用方已持 ReentrantLock |

### Phase 3 — 前端 + 中场问题 (P1)

| # | 问题 | 文件 | 必要测试 |
|---|------|------|----------|
| P3-1 | M1 删除 0 筹码 watch（保留最小值兜底：`chips<=0 && isMyTurn` 时 **仅发一次** FOLD） | `RoomView.vue` | 手动测试：0 筹码时不重复发 FOLD |
| P3-2 | C11 `player_left` 改为 `refreshRoom` | `RoomView.vue` | 手动测试：玩家离开后 UI 通过 GET 刷新 |
| P3-3 | M3 重连失败提示 | `RoomView.vue` | 解散房间后重连 → 弹出提示 |
| P3-4 | M4 防双重 join | `RoomView.vue` | 手动：快速重连 → 不报 409 |
| P3-5 | M18 `updateFromSnapshot` 不覆盖服务端 `connected` | `room.ts` | 手动：断开状态下收到快照 → connected 仍为 false |
| P3-6 | M15 `queue_prompt` 前端 handler | `RoomView.vue` | 手动：QUEUED 玩家看到"接受/拒绝"弹窗 |

### Phase 4 — WebSocket + 代码质量 (P2)

| # | 问题 | 文件 | 必要测试 |
|---|------|------|----------|
| P4-1 | M16 WebSocket 退避重连 | `useWebSocket.ts` | 手动：断网 1 分钟 → 重连间隔增长 |
| P4-2 | M17 消息队列 | `useWebSocket.ts` | 手动：断网期间点 FOLD → 重连后自动发送 |
| P4-3 | M13/M14 queue 座位/筹码 | `GameMessageController.java` | 连续 2 次 queueAccept → 筹码不翻倍 |
| P4-4 | N1/N2/N3/N5 代码清理 | 各处 | — |

---

# Part 2: 外部审查 Prompt

> 将以下 prompt 发给另一个 LLM（如 Claude、GPT-4、Gemini 等），让它对上述方案进行独立审查。

---

## 审查 Prompt

```
你是一位资深后端架构师，专门审查分布式游戏服务器的代码改造方案。你的任务是审查以下架构分析和修复方案，找出方案本身的漏洞、遗漏和风险。

## 背景

这是一个基于 Spring Boot + WebSocket(STOMP) 的德州扑克服务器。核心设计：
- 每个房间有一把 ReentrantLock（GameSessionService.roomLocks），保护 GameState 和 Room 的修改
- 游戏状态是 immutable record（GameState），每次操作创建新对象放入 ConcurrentHashMap
- 超时由 GameTimeoutScheduler 管理（ScheduledExecutorService，30秒触发 FOLD）
- 前端 Vite + Pinia，WebSocket 通过 SockJS + STOMP 通信

## 待审查的方案

以下是完整的架构分析和修复方案。请仔细阅读全文后再回答。

---
[此处粘贴上方的 Part 1 全文]
---

## 审查要求

请从以下 6 个维度逐条审查，每个维度给出"是否有问题"和"具体问题描述"：

### 维度 1：锁模型正确性
- 方案声称"所有修改 GameState/Room 的操作必须持锁"。逐行核查 5 个关键 Java 文件（GameMessageController、GameBroadcastHelper、GameSessionService、GameDisconnectHandler、RoomService），是否所有修改路径都满足这个约束？
- handleActionResult 在锁内调用 autoPlayBots，后者又 lock.lock()——依赖 ReentrantLock 重入。是否有任何调用链会导致死锁而非重入？
  - 提示：onSessionDisconnect 是 Spring 事件监听器（@EventListener），它的调用线程是什么？持有锁期间如果 Spring 派发断开事件会怎样？
- autoPlayBots 内有 while(true) 循环在锁内执行，最坏情况下（5 个机器人连续操作）锁持有时间大约多少毫秒？是否可接受？

### 维度 2：原子性边界
- handleActionResult 内部的 broadcastGameState → autoPlayBots → scheduleNextTimeout 这条链，如果中间任何一步抛异常，scheduleNextTimeout 是否还会执行？方案有没有在所有异常路径上做补偿？
- processAction 的 catch 块里调了 autoPlayBots + scheduleNextTimeout。如果 autoPlayBots 在 catch 里又抛异常，scheduleNextTimeout 还会执行吗？

### 维度 3：方案完整性
- 修改 handleActionResult 的签名后，所有当前的和未来的调用处是否都正确传递参数？有没有遗漏？
- startGame 进锁后，RoomController.startGame REST 端点是否也需要对应调整？（提示：它目前不走 STOMP，直接调 gameSession.startGame）
- 是否有任何代码路径调用了被替换的旧方法（比如之前 GameConfiguration 里直接调 gameSession.applyAction 的旧代码）？

### 维度 4：前端改动风险
- player_left 改为 refreshRoom() 后，refreshRoom 是异步 HTTP 调用。在 await 期间如果又有 WebSocket 消息到达并修改了 roomStore，会不会造成短暂 UI 不一致？
- 删除 0 筹码 watch 后，如果服务端 autoPlayBots 因为某种原因没触发（比如 bot 循环上限 30 次耗尽），玩家会永远卡住吗？

### 维度 5：测试覆盖
- 列出所有必须有自动化测试覆盖才能安全合入的改动，并说明测试场景
- 当前测试套件（171 个 test）是否覆盖了这些场景？如果不覆盖，要补哪些？

### 维度 6：实施顺序
- 4 个 Phase 的实施顺序是否合理？有没有依赖关系错误？
- 有没有某个 Phase 的改动如果不被后续 Phase 完成，会造成中间状态的回归？

## 最后

请给出：
1. 对方案的总体评价（可行 / 需修改 / 需重做）
2. 按严重度排列的所有发现（🔴CRITICAL / 🟡MEDIUM / 🟢MINOR）
3. 如果发现方案本身有问题，给出修正建议
```

---

# Part 3: 给 LLM 审查员的注意事项

审查本方案时，以下方向需要特别关注：

### 1. 锁模型边界判断是否正确

方案声称"所有修改 GameState/Room 的操作必须持锁"。核查 `GameSessionService.java`、`RoomService.java`、`GameBroadcastHelper.java`、`GameDisconnectHandler.java` 里，是否有**方案不知道的锁获取路径**，或者方案标记为"无锁"的地方实际上有条隐性路径拿到了锁。

### 2. `handleActionResult` 重入安全性

方案的核心是新建 `handleActionResult`，在锁内串联调用"广播 + autoPlayBots + scheduleNextTimeout"。`autoPlayBots` 内部会再次 `lock.lock()`——依赖 `ReentrantLock` 的重入。验证：是否有任何路径会导致**不是重入而是死锁**（比如持有锁期间触发了 WebSocket 断线事件 → `onSessionDisconnect` 又尝试获取同一把锁）。

### 3. 锁持有时间

`autoPlayBots` 在锁内执行（while 循环 + applyAction + broadcast），最坏情况下（5 个机器人连续操作）锁持有时间大约多少毫秒，是否可接受。

### 4. 方案是否遗漏了修改点

方案列了 Phase 1 5 项 + Phase 2 7 项 + Phase 3 6 项 + Phase 4 4 项。检查：这些修改的**调用方**是否也需要对应调整（比如 `handleActionResult` 签名变了，所有调用处是否都更新了；`startGame` 进锁了，`RoomController` 里那些 private 包装方法是否还有用）。

### 5. 前端的改动是否引入新竞态

C11 把 `player_left` 改为 `refreshRoom()`，但 `refreshRoom` 是异步的，期间如果又有新消息进来会不会造成短暂的不一致。

### 6. 测试是否覆盖了关键路径

方案没有测试部分。判断：哪些修改**必须有测试覆盖**才能安全合入（比如 `syncRoomChips` 后续借筹码不覆盖、锁内 handleActionResult 的并发场景）。

---

# Part 4: 独立审查反馈 & 修正记录

> 以下为外部独立审查的发现以及对应的方案修正。

## 审查发现的 CRITICAL (4)

| ID | 问题 | 修正 |
|----|------|------|
| R1 | `autoPlayBots` 在 `endGame` 后 `getState()` 返回 null 的路径未处理 | `handleActionResult` 增加 `if (state == null) return`；`autoPlayBots` 本身已有 `if (state == null) break`（当前实现 136 行） |
| R2 | `handleActionResult` 缺少异常安全保护 | 所有代码块加 try/catch/finally，确保 `autoPlayBots + scheduleNextTimeout` 在任何异常路径下都执行 |
| R3 | `handleTimeout` catch 块无法委托给 `handleActionResult`（无 result 对象） | 改为 catch 块直接调 `autoPlayBots + scheduleNextTimeout`，带 try/finally |
| R4 | 45 个问题的修复方案完全没有测试计划 | 每个 Phase 表新增"必要测试"列 |

## 审查发现的 MEDIUM (7)

| ID | 问题 | 修正 |
|----|------|------|
| R1 | `Room.addPlayer` 的 `synchronized` 与 `ReentrantLock` 并存 | 方案注明过渡期两锁并存无正确性问题（底层 `CopyOnWriteArrayList` 安全），Phase 2-10 统一移除 `synchronized` |
| R2 | `processAction` catch 块内 `autoPlayBots` 异常会丢失 `scheduleNextTimeout` | catch 块改为 try { autoPlayBots } finally { scheduleNextTimeout } |
| R3 | `endGameAndCleanupLock` 的 remove-after-unlock 竞态未修复 | 新增代码修正：`roomLocks.remove()` 移到 `lock.unlock()` **之前** |
| R4 | `GameTimeoutScheduler` 可能缺少 Spring bean 注解 | 方案注明：通过 `@Bean` 在 `GameConfiguration` 注册，无需额外注解 |
| R5 | `refreshRoom()` 异步期间的 UI 闪烁 | 可接受的权衡（最终一致），方案注明 |
| R6 | 测试覆盖完全缺失 | 已在各 Phase 补测试要求 |
| R7 | Phase 1 C1 和 Phase 2 M12 都改 `syncRoomChips`，有隐性依赖 | C1 + M12 合并到 Phase 2 一起做 |

## 审查发现的小问题

| ID | 修正 |
|----|------|
| 删除 0 筹码 watch 后缺少兜底 | M1 改为"保留最小值兜底：`chips<=0 && isMyTurn` 时仅发一次 FOLD（用 ref 防重复）" |
| Phase 3/4 前后端可并行 | Phase 3 前端改动不依赖 Phase 2 后端（除 M15），实施时前后端可同时开工 |

