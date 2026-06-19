# 多线程与并发审查报告

> 审查日期: 2026-06-20
> 审查范围: `poker-server` 全部服务层、控制器层、模型层
> 审查方法: 静态代码分析 + 交叉验证两个独立审查结果

---

## 一、总体结论

### 1.1 多房间隔离

**✓ 基本安全。** 不同房间之间的所有操作互不干扰，可以支持大量用户同时创建独立房间。

| 维度 | 是否隔离 | 机制 |
|------|---------|------|
| 游戏状态 (`GameState`) | ✓ 完全隔离 | `ConcurrentHashMap<String, GameState>` + per-room `ReentrantLock` |
| Room 对象 | ✓ 完全隔离 | 每个 Room 是独立对象，由 `RoomRegistry` 的 `ConcurrentHashMap` 管理 |
| Player 列表 | ✓ 完全隔离 | 每个 Room 有自己的 `CopyOnWriteArrayList<Player>` |
| 广播消息 | ✓ 完全隔离 | STOMP `/topic/room/{roomId}` 按 roomId 路由 |
| 超时调度 | ⚠️ 共享线程 | 单线程 `ScheduledExecutorService`，高并发下可能互相延迟 |
| 断连处理 | ⚠️ 共享线程 | Spring 事件线程池共享，但操作按 roomId 隔离 |
| RoomDissolutionScheduler | ⚠️ 共享线程 | 单线程 `@Scheduled`，遍历所有房间 |

**结论**：不同房间之间的游戏逻辑完全隔离。瓶颈仅在超时调度和溶解调度的共享线程——非关键路径，延迟几秒不影响体验。

### 1.2 单房间内并发

**✗ 存在多个竞态条件。** `CopyOnWriteArrayList` 解决了列表结构的并发安全，但 **`Player` 对象的字段修改（chips、status、folded、connected 等）没有同步保护**。多个代码路径在 per-room lock 之外修改这些字段。

---

## 二、已有的并发保护（做得好的部分）

| 机制 | 文件:行号 | 保护范围 |
|------|----------|---------|
| `ConcurrentHashMap<String, GameState> sessions` | `GameSessionService:14` | 房间间隔离 |
| `ConcurrentHashMap<String, ReentrantLock> roomLocks` | `GameSessionService:15` | per-room 互斥 |
| `startGame` 锁内执行 | `GameSessionService:22-46` | 游戏开始原子性 |
| `applyAction` 锁内执行 | `GameSessionService:49-69` | 游戏状态变更原子性 |
| `endGame` 锁内执行 | `GameSessionService:76-88` | 游戏结束原子性 |
| `CopyOnWriteArrayList<Player>` | `Room:27` | 玩家列表迭代安全 |
| `ConcurrentHashMap` playerRooms | `GameDisconnectHandler:20` | 断连映射线程安全 |
| `ConcurrentHashMap` sessionToPlayer | `GameDisconnectHandler:21` | session 映射线程安全 |
| `@EventListener SessionDisconnectEvent` | `GameDisconnectHandler:48` | Spring 事件驱动断连检测 |
| `GameState` 为 Java `record` + `List.copyOf()` | `GameState:5,28,99` | 不可变，排除内部竞态 |
| `SimpMessagingTemplate` (Spring 内置) | `BroadcastService:9` | Spring 文档标注 thread-safe，排除广播层竞态 |

---

## 三、竞态条件详细分析

### 3.1 CRITICAL-1: `syncRoomChips` 完全在 per-room lock 外

**位置**: `GameMessageController:288-308`（`syncRoomChips` 方法体）、`RoomController:176-195`（`syncChips` 重复逻辑）

**调用路径**:

```
processAction (GameMessageController:67-92)
  → gameSession.applyAction(...)      // 获取 per-room lock，修改 GameState
  → 释放 lock
  → if handComplete:
      syncRoomChips(roomId, ...)      // ← 无锁！先同步 Player 字段
      gameSession.endGame(...)        // ← 再次获取 lock，移除 session

注意：实际顺序是 syncRoomChips → endGame，不是 endGame → syncRoomChips。
syncRoomChips 在锁外执行，endGame 再次获取锁（ReentrantLock 可重入）。
```

**竞态场景**:

```
时间线 →

Thread A (hand 结束, lock 已释放):
  syncRoomChips:
    pA.setChips(1000)                     ← 写入
    pA.setFolded(false)                   ← 写入
    pA.setAllIn(false)                    ← 写入
  endGame() → 获取锁，移除 session

Thread B (玩家 borrow, 任何状态都能调用):
  POST /api/rooms/{roomId}/borrow
    player.chips += 500                   ← 并发写入 pA.chips

Thread C (玩家断连):
  pA.setStatus(DISCONNECTED)              ← 并发写入 pA.status

最终 pA.chips 可能是 1000 或 1500，取决于 JMM 写入顺序。
pA.status 可能是 DISCONNECTED 或 syncRoomChips 未触及的先前值。
```

**影响等级**: CRITICAL — 筹码数据可能丢失/错误，直接影响核心业务正确性。

**注意**: `borrowChips` **没有检查房间状态**（见 `RoomController:113-130`），在任何状态下都可以调用。不存在"borrow 只有 WAITING 可用"的缓解条件，此前描述有误。

---

### 3.2 CRITICAL-2: `GameDisconnectHandler.onSessionDisconnect` 全面无锁

**位置**: `GameDisconnectHandler:48-123`

```java
@EventListener
public void onSessionDisconnect(SessionDisconnectEvent event) {
    // 71-77: 无锁修改 Player 字段
    p.setStatus(PlayerStatus.DISCONNECTED);
    p.setConnected(false);

    // 80-89: 获取 per-room lock，但 Player 状态已在锁外修改
    if (isPlaying) {
        var state = gameSession.getState(roomId);        // 锁外读
        if (state.currentPlayer().equals(playerId)) {
            gameSession.applyAction(..., FOLD, 0);       // 获取锁
        }
    }

    // 99-122: 60s 后定时器 → 无锁 removePlayer
    timer = executor.schedule(() -> {
        r.removePlayer(fPlayerId);                        // 无锁
    }, 60, SECONDS);
}
```

**竞态场景**:

```
Thread A (玩家动作 → hand 结束):
  endGame()
  syncRoomChips:
    pA.setChips(1000)
    pA.setFolded(false)
    pA.setAllIn(false)
    → broadcastBustChoice (如果 bust)

Thread B (玩家断连):
  pA.setStatus(DISCONNECTED)
  pA.setConnected(false)

60秒后 Thread C (定时器):
  r.removePlayer("pA")
  → 此时 pA 已从 syncRoomChips 收到新筹码值
  → 但 DISCONNECTED 状态和 bust_choice 可能冲突
```

**影响等级**: CRITICAL — Player 状态不一致。断连玩家可能被误认为 ACTIVE，或者 bust_choice 和 disconnect 处理交错。

---

### 3.3 CRITICAL-3: `Player` 对象字段无原子性保证

**位置**: 多处

`CopyOnWriteArrayList` 保证**列表结构**的并发安全（add/remove/iterate 不抛 ConcurrentModificationException），但**不保护 Player 对象内部字段**。

**所有无锁修改 Player 字段的路径**:

| 代码路径 | 文件:行号 | 修改的字段 |
|----------|----------|-----------|
| `syncRoomChips` | `GameMessageController:288-308` | chips, betInRound, folded, allIn, holeCards |
| `syncChips` (RoomController dup) | `RoomController:176-195` | chips, betInRound, folded, allIn, holeCards |
| `onSessionDisconnect` | `GameDisconnectHandler:70-77` | status, connected |
| `onReconnect` | `GameDisconnectHandler:125-144` | status, connected |
| `handleLeave` | `GameMessageController:160-161` | 列表结构 (removePlayer) |
| `handleQueueAccept` | `GameMessageController:105-136` | status, chips, seatIndex |
| `borrowChips` (REST) | `RoomController:126` | chips, borrowCount |
| `transferOwnership` | `RoomService:91-111` | owner (Player 和 Room 都无锁) |
| 60s disconnect 定时器 | `GameDisconnectHandler:101-116` | 列表结构 (removePlayer) |

**注意**: `RoomController` 存在 `autoBots` 的同步逻辑，也存在 `syncChips`/`checkGameOver`/`broadcastGameOver` 的完整重复（`RoomController:132-253`），与 `GameMessageController` 的对应方法有**完全相同的竞态条件**。

**具体竞态示例**:

```
Thread A: syncRoomChips 写入 pA.chips = 1000, pA.folded = false
Thread B: borrowChips 写入 pA.chips += 500, pA.borrowCount++
Thread C: onSessionDisconnect 写入 pA.status = DISCONNECTED

Java Memory Model 不保证这些写入的顺序或可见性。
最终 pA.chips 可能是 1000、1500、或其他值（取决于编译器重排序）。
```

**影响等级**: CRITICAL — 这是所有 CRITICAL 问题的根源。Player 对象是可变共享状态，多线程修改无同步保护。

---

### 3.4 H-1: `autoPlayBots` / `autoBots` 在锁外读状态导致竞态

**位置**: `GameMessageController:228-274`（`autoPlayBots`）、`RoomController:132-174`（`autoBots`）— **两处有相同问题**

**调用路径**:

```
processAction (Thread A):
  applyAction(...)  → 获取锁 → 成功 → 释放锁
  autoPlayBots():
    state = gameSession.getState()    ← 锁外读，当前 player 是 bot-1
    applyAction(bot-1, CALL)          ← 获取锁

processAction (Thread B, 并发):
  applyAction(...)  → 获取锁期间读到当前 player 也是 bot-1
  autoPlayBots():
    state = gameSession.getState()    ← 锁外读，当前 player 可能已变

Thread A 成功执行 bot-1 的 CALL，状态推进到 bot-2
Thread B 尝试执行 bot-1 的 CALL → "Not your turn" 异常
→ 异常被 catch 并 continue，bot 可能跳过本次行动
```

**影响等级**: HIGH — 不会损坏数据（per-room lock 保护了状态变更），但：
- 异常被 catch 后 continue，可能导致 bot 跳过行动
- 在高并发下（多 human 同时操作后触发 autoPlayBots），可能影响游戏流程
- 产生大量异常日志噪音，影响可观测性

`RoomController.autoBots` 有**相同问题**，两个方法应一并修复。

---

### 3.5 H-2: `addPlayer` 容量检查不是原子的（TOCTOU）

**位置**: `Room:35-42`

```java
public boolean addPlayer(Player player) {
    if (players.size() >= config.getMaxSeats()) return false;   // Time-of-Check
    if (players.stream().anyMatch(...)) return false;
    players.add(player);                                        // Time-of-Use
    return true;
}
```

**竞态场景**: 房间只剩 1 个空位，两个玩家同时 join：

```
Thread A: size() = 7, maxSeats = 8 → 通过检查
Thread B: size() = 7, maxSeats = 8 → 也通过检查（CopyOnWriteArrayList 快照）
Thread A: players.add(A) → 成功，size = 8
Thread B: players.add(B) → 成功，size = 9 → 超出 maxSeats
```

**影响等级**: HIGH — 玩家数量超出房间容量，可能导致座位分配错误。

**注意**: `CopyOnWriteArrayList.size()` 在 add 完成前返回旧值，加剧了此问题。

**额外风险**: `addPlayer` 的 `synchronized` 只保护 Room 级的 add，但 `RoomService.joinRoom`（调用方）中的 `room.getPlayers().size()` 用于设置 `seatIndex`，如果两线程同时 join 最后一席，seatIndex 分配可能冲突。修复 `addPlayer` 时需一并检查调用方。

---

### 3.6 H-3: `handleQueueAccept` 无锁座位分配

**位置**: `GameMessageController:105-136`

```java
// 两个 QUEUED 玩家同时 accept
// Thread A 和 Thread B 同时执行：

boolean[] occupied = new boolean[maxSeats];
for (var rp : room.getPlayers()) {
    if (rp.getStatus() == ACTIVE && rp.getSeatIndex() >= 0) {
        occupied[rp.getSeatIndex()] = true;  // 两线程都读到相同的 occupied 快照
    }
}
for (int i = 0; i < maxSeats; i++) {
    if (!occupied[i]) {
        p.setSeatIndex(i);  // 可能两线程都分到同一个 i
        break;
    }
}
```

**影响等级**: HIGH — 两个玩家可能被分配到同一个座位。

---

### 3.7 H-4: `transferOwnership` 无锁修改 owner 字段

**位置**: `RoomService:91-111`

```java
public Player transferOwnership(Room room, String leavingPlayerId) {
    Player newOwner = room.getPlayers().stream()...findFirst().orElse(null);  // 锁外读
    if (newOwner != null) {
        room.getPlayers().stream().filter(Player::isOwner)
            .findFirst().ifPresent(o -> o.setOwner(false));  // 无锁写 Player.owner
        newOwner.setOwner(true);                              // 无锁写 Player.owner
        room.setOwner(newOwner);                              // 无锁写 Room.owner
    }
}
```

**竞态场景**: 两玩家同时 leave（都曾是 owner→已转移），两个线程同时调用 `transferOwnership`：
- Thread A 找 newOwner = Player X，设置 X.owner = true，room.owner = X
- Thread B 找 newOwner = Player Y，设置 Y.owner = true，room.owner = Y
- 最终 `room.owner` = Y，但 `X.owner` 仍为 true

**影响等级**: HIGH — 房间有多个 `owner=true` 的 Player，owner 状态不一致。

---

### 3.8 MED-1: `GameTimeoutScheduler` 单线程 executor

**位置**: `GameTimeoutScheduler:13`

```java
private final ScheduledExecutorService executor =
    Executors.newSingleThreadScheduledExecutor();
```

所有房间的超时回调共用一个线程。如果房间 A 的回调在等 per-room lock，房间 B 的超时会被延迟。

**影响等级**: MEDIUM — 超时可能延迟几秒，不影响正确性但影响用户体验。

**现有缓解**: 事实上每个房间的超时回调只是调用 `applyAction(FOLD)`，这个操作在获取锁后很快完成，所以实际延迟很小。

---

### 3.9 MED-2: `roomLocks` 永不清理

**位置**: `GameSessionService:15`

```java
private final ConcurrentHashMap<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();
```

`computeIfAbsent` 创建锁，但 `endGame` 后不移除。长期运行 → 内存泄漏。

**影响等级**: MEDIUM — 每个 `ReentrantLock` 约 200 字节，1 万个房间约 2MB，实际影响有限。

---

### 3.10 MED-3: `sessionToPlayer` 映射仅 `startGame` 时注册

**位置**: `GameDisconnectHandler:40-42`

`registerSession(sessionId, playerId)` 在 `startGame` → `disconnectHandler.registerPlayer` 时调用，但 `registerPlayer` 的注册时机是 `startGame`（遍历 `state.players()`），不在 `joinRoom` 时。

**影响**: WAITING 状态下的玩家断连无法通过 `sessionToPlayer` 检测到。`onSessionDisconnect` 的 fallback 是读 `event.getUser().getName()`（Principal），这个在 WebSocket handshake 时通过 `PlayerIdHandshakeInterceptor` 设置。但需要注意：
- SockJS 模式下 Principal 传递可能不稳定（依赖于 STOMP CONNECT 帧的 header）
- 如果 WebSocket 连接在 handshake 完成前断开，interceptor 不会设置 Principal

**影响等级**: LOW — 有 fallback 路径，但 SockJS 模式下不完全可靠。

---

### 3.11 MED-4: 关键字段缺少 `volatile`

**位置**: 多处

| 字段 | 类 | 读写线程 |
|------|-----|---------|
| `Room.owner` | `Room:20` | STOMP 线程 (handleLeave) / REST 线程 (createRoom) / dissolve 线程 |
| `Room.lastActivity` | `Room:18` | STOMP 线程 / Scheduler 线程 |
| `Player.connected` | `Player:17` | STOMP 线程 / disconnect 事件线程 / reconnect |
| `Player.status` | `Player:22` | STOMP 线程 / disconnect 事件线程 / syncRoomChips |

**影响等级**: LOW-MEDIUM — 在无锁场景下，缺少 `volatile` 可能导致其他线程读到过期值。但 x86 架构上 32/64 位写入通常是原子的，Spring 线程调度增加了可见性保证。

**注意**: `volatile` 与锁的关系：
- 如果所有读写都在 per-room lock 内 → `volatile` 是多余的（锁已保证可见性）
- 如果存在锁外读写 → `volatile` 是必要的
- 当前状态是大部分修改在锁外 → 至少需要 `volatile`
- 修复后（全部进锁）→ 部分 `volatile` 可移除

---

### 3.12 已验证的安全点

**`GameState` 不可变性**：`GameState.java` 是 Java `record`（`GameState:5`），构造时使用 `List.copyOf()`（`GameState:28`），`withPlayers` 也用 `List.copyOf()`（`GameState:99`）。`GameState.getState()` 返回的引用在多线程下安全。

**`BroadcastService` 线程安全性**：委托给 Spring `SimpMessagingTemplate`，其 JavaDoc 标注 `@ThreadSafe`（Spring 框架保证）。`BroadcastService` 自身无状态，仅转发调用。

---

### 3.13 测试覆盖说明

当前 160 个单元测试**均为单线程测试**，不覆盖并发场景。并发问题无法通过普通单元测试发现，需要使用 `CountDownLatch`、`CyclicBarrier` 或压力测试工具验证。回归测试只能确保**现有单线程逻辑不受修复影响**，不能替代并发正确性验证。

---

## 四、独立房间隔离分析（详细版）

### 4.1 完全隔离的维度

```
Room A ───────────────────────────────── Room B ─────────────────────────────────
│                                         │
│ RoomRegistry: ConcurrentHashMap         │ RoomRegistry: ConcurrentHashMap
│   rooms["AAA"] = RoomA                  │   rooms["BBB"] = RoomB
│                                         │
│ GameSessionService:                     │ GameSessionService:
│   sessions["AAA"] = GameState_A         │   sessions["BBB"] = GameState_B
│   roomLocks["AAA"] = ReentrantLock_A    │   roomLocks["BBB"] = ReentrantLock_B
│                                         │
│ Broadcast:                              │ Broadcast:
│   /topic/room/AAA/...                   │   /topic/room/BBB/...
│   /topic/player/pA1/game                │   /topic/player/pB1/game
│                                         │
│ GameDisconnectHandler:                  │ GameDisconnectHandler:
│   playerRooms["pA1"] = "AAA"            │   playerRooms["pB1"] = "BBB"
```

**关键保证**: 线程 T1 在 Room A 上持有 `roomLocks["AAA"]`，线程 T2 可以同时持有 `roomLocks["BBB"]`，完全无阻塞。

### 4.2 部分隔离的维度

```
┌─────────────────────────────────────────────────────┐
│ GameTimeoutScheduler                                │
│   singleThreadExecutor                              │
│                                                     │
│   队列: [Room_A_timeout, Room_B_timeout, ...]       │
│                                                     │
│   → 顺序执行，A 阻塞会延迟 B                           │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ SessionDisconnectEvent (Spring ApplicationEvent)     │
│   事件线程池 (默认 SimpleAsyncTaskExecutor)            │
│                                                     │
│   → 房间 A 的断连处理可能和房间 B 并发                  │
│   → 但各自操作不同 Player 对象，安全                    │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ RoomDissolutionScheduler                            │
│   @Scheduled(fixedRate=300000)                      │
│   单线程遍历所有房间                                   │
│                                                     │
│   → 顺序检查，不影响正确性                             │
└─────────────────────────────────────────────────────┘
```

---

## 五、修复方案

### 5.1 核心思路

**统一 per-room lock 入口**：在 `GameSessionService` 添加一个 `executeWithLock(roomId, Runnable)` 方法，所有需要保护的操作通过它执行。

**死锁分析**：`ReentrantLock` 是可重入锁。如果 `executeWithLock` 内的 Runnable 调用 `applyAction`（后者也获取同一个锁），不会死锁——同一个线程可以多次获取同一个 `ReentrantLock`。这是设计选择使用 `ReentrantLock` 而非 `synchronized` 的原因。

### 5.2 动作 1: 扩大 per-room lock 覆盖范围

将以下操作移入 per-room lock：

| 操作 | 当前状态 | 修复后 |
|------|---------|--------|
| `syncRoomChips` | 无锁 | 在 `endGame` 内（锁释放前）执行 |
| `handleLeave` 的 Player 修改 | 无锁 | `executeWithLock` |
| `handleQueueAccept` 座位分配 | 无锁 | `executeWithLock` |
| `handleDissolve` 的 Player 修改 | 无锁 | `executeWithLock` |
| `borrowChips` 的 Player 修改 | 无锁 | `executeWithLock` |
| `transferOwnership` 的 owner 修改 | 无锁 | `executeWithLock` |
| `autoPlayBots` / `autoBots` 锁外读 | 无锁 | 改为先尝试获取锁，检查 currentPlayer 是否仍为 bot |

**覆盖**: CRITICAL-1, CRITICAL-3, H-3, H-4

### 5.3 动作 2: 断开连接处理接入 per-room lock

`onSessionDisconnect` 中的 Player 状态修改和 60s 定时器内的 `removePlayer` 通过 per-room lock 保护。

**覆盖**: CRITICAL-2

### 5.4 动作 3: `addPlayer` 加 `synchronized` + 调用方修复

```java
public synchronized boolean addPlayer(Player player) { ... }
```

`Room.addPlayer` 的 `synchronized` 只保证 add 操作本身原子的，但 `RoomService.joinRoom` 中 `new Player(..., room.getPlayers().size(), ...)` 用 player count 作 seatIndex，这个在 `addPlayer` 的外部。如果两个线程同时 join 最后一席：
- Thread A: size()=7, addPlayer(A) → 成功，A.seatIndex=7
- Thread B: size()=7（A 还没加完？`synchronized` 保证了不会），addPlayer(B) → 失败（满座）

`addPlayer synchronized` 可以解决这个问题——因为 B 在 A 的 add 完成后才能进入，size() 已经是 8。但 seatIndex 的赋值（`new Player(..., room.getPlayers().size(), ...)`）仍在锁外。**修复时需确保 seatIndex 分配也在同步范围内。**

**覆盖**: H-2

### 5.5 动作 4: 超时调度改用线程池

```java
Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors())
```

**覆盖**: MED-1 (原 H-3)

### 5.6 动作 5: 清理 + volatile

| 改动 | 位置 | 前提条件 |
|------|------|---------|
| `endGame` 后 `roomLocks.remove(roomId)` | `GameSessionService` | 需确认 endGame 后不再有操作需要该 roomLock |
| `Room.owner` 加 `volatile` | `Room` | 锁外读写时必要；全部进锁后可移除 |
| `Room.lastActivity` 加 `volatile` | `Room` | 同上 |
| `Player.connected` 加 `volatile` | `Player` | 同上 |
| `Player.status` 加 `volatile` | `Player` | 同上 |

**`roomLocks` 清理的前提条件**：当前 `endGame` 后仍有 `handleLeave`、`onSessionDisconnect`、`borrow` 等操作需要锁。修复动作 1+2 将所有这些操作纳入锁内后，`endGame` 之后的锁应该存在的唯一场景是：
- 断连宽限期的定时器（60s 后触发）
- 房间未解散时的后续 leave/borrow

建议不立即清理，而是用弱引用或定期清理。或者：只在 `removeRoom`（房间完全解散）时清理。

**覆盖**: MED-2 (原 MED-1), MED-4 (原 MED-3)

### 5.7 `autoPlayBots` 修复方案

两个可选方案：

**方案 A**: 在 `processAction` 中，human action 处理完后不释放锁，直接继续 autoPlayBots，直到遇到下一个 human player 才释放锁。锁持有时间长，但消除竞态。

**方案 B**: `autoPlayBots` 在锁外执行，但先获取锁检查 `currentPlayer` 是否仍为 bot。如果已变（被另一个线程处理了），跳过。

**推荐 B**：不增加锁持有时长。核心改动是 `getState()` → `try { getState() 检查 currentPlayer } catch (IllegalStateException) { 跳过 }`。

### 5.8 `RoomService.joinRoom` seatIndex 分配修复

当前：
```java
new Player(req.getPlayerId(), req.getNickname(),
    room.getPlayers().size(),          // ← 锁外读，不准确
    room.getConfig().getInitialChips());
```

修复为在 `addPlayer` 内部分配 seatIndex：
```java
// Room.addPlayer (synchronized)
public synchronized boolean addPlayer(Player player) {
    if (players.size() >= config.getMaxSeats()) return false;
    if (players.stream().anyMatch(p -> p.getPlayerId().equals(player.getPlayerId()))) return false;
    player.setSeatIndex(players.size());  // 在锁内分配
    players.add(player);
    return true;
}
```

---

## 六、修复影响评估

| 修复 | 风险 | 测试影响 |
|------|------|---------|
| `executeWithLock` | 低 — 只是将已有操作包入已有锁 | 需要验证 borrow/leave/disconnect 在锁内不产生死锁 |
| `addPlayer synchronized` | 极低 — 单房间短临界区 | 需要验证两线程同时 join 超座不成功 |
| 超时线程池 | 极低 — 线程数增加但合理 | 需要验证超时回调正确性 |
| `volatile` 字段 | 极低 — 编译期行为变更 | 无需测试变更 |
| `roomLocks` 清理 | 低 — 仅在 endGame 后 | 需要验证 endGame 后确实不再需要锁 |

**所有修复不改变 API 契约，不改变前端行为。**

---

## 七、审查交叉验证记录

| 审查轮次 | 发现问题 | 新增/确认 |
|----------|---------|---------|
| 第一轮（AI 审查） | P1 addPlayer TOCTOU, P2 syncRoomChips 锁外, P3 disconnect 定时器竞态, P4 volatile 缺失 | 初始发现 |
| 第二轮（用户审查） | CRITICAL-1 autoPlayBots 竞态, CRITICAL-2 disconnect 无锁, CRITICAL-3 leave TOCTOU, H-1 Player 字段无原子性, H-2 queueAccept 无锁, H-3 单线程超时, MED-1 roomLocks 泄漏, MED-2 session 映射丢失 | 新增 5 个问题，深化 4 个问题 |
| 第三轮（用户代码验证） | 行号错误 ×3、调用顺序描述有误、"现有缓解"不成立(borrow无状态检查)、遗漏 RoomController.autoBots 相同竞态、遗漏 transferOwnership 无锁、GameState/BroadcastService 未验证、修复方案 4 个缺陷、CRITICAL-4 降级理由偏弱、MED-2 fallback 可靠性存疑、缺少测试覆盖声明 | 修正 4 处、补充 6 处、新增 2 个安全检查项 |

**三轮审查去重后共 13 个问题**: 3 CRITICAL, 4 HIGH, 4 MEDIUM, 2 LOW。

---

## 八、问题汇总

| ID | 等级 | 问题 |
|----|------|------|
| CRITICAL-1 | CRITICAL | `syncRoomChips` 完全在锁外 |
| CRITICAL-2 | CRITICAL | `onSessionDisconnect` 全面无锁 |
| CRITICAL-3 | CRITICAL | `Player` 字段无原子性（无锁修改路径表包含 9 条路径） |
| H-1 | HIGH | `autoPlayBots` / `autoBots` 锁外读（两处相同问题） |
| H-2 | HIGH | `addPlayer` 容量 TOCTOU + `joinRoom` seatIndex 分配不安全 |
| H-3 | HIGH | `handleQueueAccept` 座位分配无锁 |
| H-4 | HIGH | `transferOwnership` 无锁修改 owner |
| MED-1 | MEDIUM | `GameTimeoutScheduler` 单线程 executor |
| MED-2 | MEDIUM | `roomLocks` 永不清理 |
| MED-3 | MEDIUM | `sessionToPlayer` 仅 startGame 注册 + fallback 不完全可靠 |
| MED-4 | MEDIUM | 4 个字段缺少 `volatile` + volatile 与锁关系需澄清 |
| L-1 | LOW | `RoomController` 存在完整的逻辑重复（7 个方法与 GameMessageController 重复） |
| L-2 | LOW | `handleLeave` 的 `hasActiveSession` → `applyAction` TOCTOU（功能正确但时序不洁） |

---

## 九、开发建议

建议一次提交修复所有问题（而非分批），因为修复相互关联：

1. `executeWithLock` 是其他修复的基础
2. 全部进锁后重新评估哪些 `volatile` 仍需保留
3. `RoomService.joinRoom` seatIndex 分配与 `addPlayer synchronized` 联动
4. 超时线程池和 roomLocks 清理是独立改动

预计修改文件数: 8-10
预计涉及行数: ~120 行增删
预计回归测试: 已有 160 单线程测试 + 需补充 3-4 个并发专项测试（CountDownLatch）
