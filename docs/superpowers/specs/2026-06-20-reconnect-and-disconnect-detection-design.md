# 断线重连 & MED-3 修复设计

## 一、要解决的完整场景

| 场景 | 期望结果 |
|------|---------|
| 玩家 wifi 闪断（2秒内重连成功） | 自动恢复，不感知 |
| 玩家 wifi 断了60秒后恢复 | 主页输入房间号 → 重回原位 |
| 玩家误关标签页，重新打开浏览器 | 主页输入房间号 → 回到房间 |
| 断开期间轮到该玩家操作 | 后端超时自动 fold |
| WAITING 房间有人断连 | 能检测到，标记 DISCONNECTED，60s 后移除 |

## 二、根本原因：两条命脉都断了

```
浏览器关闭
  ↓
Spring SessionDisconnectEvent
  ↓
onSessionDisconnect()
  ↓
① playerId = sessionToPlayer.get(sessionId)  ← MED-3: 只在 startGame 时注册，joinRoom 时不注册
  ↓ null ? 
② playerId = event.getUser().getName()       ← fallback (handshake URL 里的 playerId)，SockJS 下不稳定
  ↓ 拿到了！
③ roomId = playerRooms.get(playerId)         ← 也只在 startGame 时注册！！
  ↓ null (WAITING 房间)
return; ← 直接退出，什么都不做
```

| 映射表 | 注册时机 | WAITING 房间 | PLAYING 房间 | 
|--------|---------|:---:|:---:|
| `sessionToPlayer` | `SessionRegistrationInterceptor` (每次 WS CONNECT) | fallback 不稳定 | ✅ |
| `playerRooms` | `startGame` → `disconnectHandler.registerPlayer` | ❌ 导致断连检测失败 | ✅ |

## 三、受影响的调用链（codegraph 验证）

### 3.1 `registerPlayer` 的调用者
- `GameMessageController.startGame:54` → `disconnectHandler.registerPlayer(roomId, p.playerId())` ✅ 唯一调用方
- 无其他调用

### 3.2 `registerSession` 的调用者
- `SessionRegistrationInterceptor.preSend` → `handler.registerSession(sessionId, user.getName())` ✅ 每次 WS 连接都注册
- session→player 映射可靠

### 3.3 `onReconnect` 的调用者
- **无任何调用方** ← 这是断线重连不工作的直接原因
- 方法已实现但从未被调用

### 3.4 `joinRoom` 的调用链
- `RoomController.joinRoom` (REST POST /api/rooms/{roomId}/join) → 调 `RoomService.joinRoom`
- `GameActionController.joinRoom` (STOMP /app/room/{roomId}/join) → 也调 `RoomService.joinRoom`
- 两条路径都需要注册 `playerRooms` 映射

### 3.5 `addPlayer` 调用者（验证不会受影响）
- `RoomService.createRoom` ✅
- `RoomService.joinRoom` → `room.addPlayer(player)` ✅
- `RoomService.addBots` → `room.addPlayer(bot)` ✅
- 我们的修改不改变 `addPlayer` 的逻辑

## 四、修复方案（5处修改）

### 修改 1: `RoomService.joinRoom` — 重连检测 + 注册 playerRooms + lastActivity

**文件**: `server/src/main/java/com/first/poker/service/RoomService.java`（第40-61行）

**注入新依赖**: 构造函数加 `@Lazy GameDisconnectHandler disconnectHandler`（`@Lazy` 打破循环依赖）

**新增逻辑**（插入在 `findById` 之后，`isPlaying` 判断之前）：

```java
public Room joinRoom(String roomId, JoinRoomRequest req) {
    Room room = registry.findById(roomId);
    if (room == null) return null;

    // === 新增：重连检测 ===
    // 如果玩家已在房间中（之前断连），这不是新加入，是重连
    var existing = room.getPlayers().stream()
        .filter(p -> p.getPlayerId().equals(req.getPlayerId()))
        .findFirst().orElse(null);
    if (existing != null) {
        // State restoration is delegated to disconnectHandler.onReconnect()
        // which performs it inside executeWithLock — avoiding concurrency
        // conflicts with syncRoomChips / onSessionDisconnect.
        disconnectHandler.onReconnect(req.getPlayerId());
        // Register player→room mapping so future disconnects are detected (MED-3 fix)
        disconnectHandler.registerPlayer(roomId, req.getPlayerId());
        System.out.println("[RECONNECT] " + req.getPlayerId() + " rejoined room " + roomId);
        return room;
    }
    // === 新增结束 ===

    boolean isPlaying = gameSessionService.hasActiveSession(roomId);

    if (isPlaying) {
        // ... QUEUED logic ...
        room.setLastActivity(System.currentTimeMillis());  // ← 新增：玩家加入视为活动
        return room;
    }

    // ... ACTIVE logic ...
    room.setLastActivity(System.currentTimeMillis());      // ← 新增
    return room;
}
```

### 修改 2: `RoomService.createRoom` — 注册 owner 的 playerRooms

**文件**: 同上

**位置**: 在 `room.addPlayer(owner)` 之后加一行：

```java
// ... owner 已添加 ...
room.setOwner(owner);
disconnectHandler.registerPlayer(room.getRoomId(), req.getOwnerId());  // ← 新增
```

### 修改 3: `GameActionController.joinRoom` — 不需要额外 registerPlayer

**文件**: `server/src/main/java/com/first/poker/controller/GameActionController.java`

**结论**: **不需要修改此文件**。`GameActionController.joinRoom` 调用了 `roomService.joinRoom()`，而后者内部已经调用 `disconnectHandler.registerPlayer()`（见修改 1）。此处再调用会**重复注册**。

```java
// 当前代码保持不变，无需注入 GameDisconnectHandler
@MessageMapping("/room/{roomId}/join")
public void joinRoom(@DestinationVariable String roomId, @Payload JoinRoomRequest req) {
    Room room = roomService.joinRoom(roomId, req);
    // 不需要 disconnectHandler.registerPlayer —— RoomService.joinRoom 已处理
    if (room != null) {
        broadcast.sendToRoom(roomId, Map.of(
            "type", "system",
            "text", req.getNickname() + " joined the room"
        ));
    }
}
```

### 修改 4: `GameDisconnectHandler.onSessionDisconnect` — 处理 room == null 时主动清理

**文件**: `server/src/main/java/com/first/poker/service/GameDisconnectHandler.java`

**当前第68行**:
```java
var room = roomService.findRoom(fRoomId);
if (room == null) return;  // room 已被 dissolve → 静默退出
```

**保持不变** — 这个逻辑是正确的。room 被 dissolve 后不应该再做任何操作。我们用新注册的 `playerRooms` 保证了 room 存在时一定能找到。

### 修改 5: `lastActivity` 防超时销毁 — 所有交互活动均更新

**问题**: `lastActivity` 仅在构造函数（初始化）和 `handleLeave` 中更新。其他所有操作 — 加入、开始游戏、下注/弃牌/跟注、断连、重连、borrow — 都**不更新** `lastActivity`。`RoomDissolutionScheduler` 用 `lastActivity + 30min < now` 判断房间是否该销毁，这意味着：

- **任何超过 30 分钟的房间都会被销毁**，哪怕牌局正在激烈进行
- 玩家断连 + 全员退出后要等 30 分钟，而不是等游戏自然结束后的合理时间

**修改点**:

| # | 文件 | 方法 | 插入位置 |
|---|------|------|---------|
| 5a | `RoomService.joinRoom` | 已完成（见修改 1，两个 return 前均加 `room.setLastActivity`） |
| 5b | `GameMessageController.processAction` | `gameSession.applyAction` 成功后 | `room.setLastActivity(System.currentTimeMillis())` |
| 5c | `RoomController.startGame` | `gameSessionService.startGame` 调用前 | `room.setLastActivity(System.currentTimeMillis())` |
| 5d | `RoomController.borrowChips` | `executeWithLock` 内、chips 借出成功后 | `room.setLastActivity(System.currentTimeMillis())` |
| 5e | `GameDisconnectHandler.onSessionDisconnect` | `executeWithLock` 内、标记 DISCONNECTED 后 | `room.setLastActivity(System.currentTimeMillis())` |
| 5f | `GameDisconnectHandler.onReconnect` | `executeWithLock` 内、恢复 ACTIVE 后 | `room.setLastActivity(System.currentTimeMillis())` |

**5b 示例（`GameMessageController.processAction`）**:
```java
@MessageMapping("/game/{roomId}/action")
public void processAction(@DestinationVariable String roomId, @Payload GameActionRequest req) {
    try {
        // ... existing validation and applyAction ...
        var result = gameSession.applyAction(roomId, req.getPlayerId(), action, req.getAmount());

        // ← 新增：任何游戏操作都视为活动，防止超时销毁
        // applyAction 成功后 room 一定存在（session 存在 ⇒ room 存在），
        // null 检查是防御性编程，成本可忽略不计。
        var room = roomService.findRoom(roomId);
        if (room != null) room.setLastActivity(System.currentTimeMillis());

        var state = result.state();
        // ... existing broadcast and hand-complete logic ...
    }
}
```

**5c 示例（`RoomController.startGame`）**:
```java
public ResponseEntity<?> startGame(@PathVariable String roomId) {
    var room = roomService.findRoom(roomId);
    if (room == null || room.getOwner() == null) return ResponseEntity.notFound().build();
    String ownerId = room.getOwner().getPlayerId();

    var state = gameSessionService.startGame(room, ownerId);
    room.setLastActivity(System.currentTimeMillis());  // ← 新增：成功后更新（避免 startGame 失败时误更新）
    // ... existing broadcast and auto-play ...
}
```

**5d 示例（`RoomController.borrowChips`）**:
```java
gameSessionService.executeWithLock(roomId, () -> {
    var room = roomService.findRoom(roomId);
    if (room == null) return;
    // ... existing borrow logic ...
    player.borrow();  // or player.setChips(newChips)
    room.setLastActivity(System.currentTimeMillis());  // ← 新增
});
```

**5e 示例（`GameDisconnectHandler.onSessionDisconnect`）**:
```java
gameSession.executeWithLock(fRoomId, () -> {
    var room = roomService.findRoom(fRoomId);
    if (room == null) return;
    // ... existing DISCONNECTED marking ...
    room.setLastActivity(System.currentTimeMillis());  // ← 新增
    // ... existing auto-fold ...
});
```

**5f 示例（`GameDisconnectHandler.onReconnect`）**:
```java
if (roomId != null) {
    gameSession.executeWithLock(roomId, () -> {
        var room = roomService.findRoom(roomId);
        if (room != null) {
            // ... existing status restoration ...
            room.setLastActivity(System.currentTimeMillis());  // ← 新增
        }
    });
}
```

**效果**: 只要房间内有任何交互（加入、开始、操作、断连、重连、借钱），30 分钟的倒计时就会被重置。只有真正无人问津的空房间才会被定时器清理。

## 五、影响面评估

| 修改 | 影响的类 | 影响的方法 | 是否破坏现有逻辑 |
|------|---------|-----------|:---:|
| RoomService 构造器加 `@Lazy GameDisconnectHandler` | `RoomService`, `GameDisconnectHandler`（循环依赖由 `@Lazy` 解决） | 无 — 只是加参数 | ❌ 不破坏 |
| `joinRoom` 加重连检测 + lastActivity（只调 `onReconnect`，不直接改状态） | `RoomService` | 仅 `joinRoom`，在 `addPlayer` 之前执行 | ❌ 不破坏 — 新玩家走原有逻辑 |
| `createRoom` 加 registerPlayer | `RoomService` | 仅 `createRoom`，owner 注册 | ❌ 不破坏 — 纯新增 |
| `GameActionController` | — | — | ❌ **无需修改** — `joinRoom` 内部已处理 |
| lastActivity 防超时销毁 | `RoomService.joinRoom`, `GameMessageController.processAction`, `RoomController.startGame`, `RoomController.borrowChips`, `GameDisconnectHandler.onSessionDisconnect`, `GameDisconnectHandler.onReconnect` | 6 个方法各加 1 行 `room.setLastActivity` | ❌ 不破坏 — 纯新增，且都在锁内 |

### 不需要修改的

| 文件 | 原因 |
|------|------|
| `GameMessageController.startGame` 中的 `registerPlayer` | 已经注册了，保留不动 |
| `SessionRegistrationInterceptor` | 已经注册 `sessionToPlayer`，保留不动 |
| `Room.addPlayer` / `RoomService.addBots` | 不涉及 — Bot 不需要断连检测 |
| `GameBroadcastHelper` | 不涉及 |
| 前端 `useWebSocket.ts` | STOMP 自动重连已存在，不需要改动 |
| 前端 `RoomView.vue` | 现有 `player_joined` / `player_disconnected` 消息处理已完备 |
| 前端 `HomeView.vue` | join 流程不变，后端会自动识别重连 |

## 六、端到端流程验证

### 场景 A: wifi 闪断（3秒恢复）

```
T+0s  玩家 wifi 断
T+0s  Spring SessionDisconnectEvent → onSessionDisconnect
      → sessionToPlayer 有记录 ✅ → playerId 找到
      → playerRooms 有记录 ✅（createRoom 时注册的）→ roomId 找到
      → 标记 DISCONNECTED + auto-fold + 启动 60s 定时器
T+2s  STOMP 自动重连成功 (reconnectDelay: 2000)
T+3s  玩家重新打开主页 → 输入房间号 → POST /api/rooms/{id}/join
      → RoomService.joinRoom → existing 找到 → onReconnect 取消 60s 倒计时
      → registerPlayer 重新注册
T+3s  玩家回到房间，状态 ACTIVE
```

### 场景 B: 关浏览器，60s 后回来

```
T+0s  关浏览器 → 同场景 A T+0~T+0s
T+60s 定时器触发 → playerRooms 移除 → sessionToPlayer 移除 → 玩家离开房间
T+65s 重新打开浏览器 → 主页输入房间号 → POST /api/rooms/{id}/join
      → RoomService.joinRoom → existing 找不到（已被移除）
      → 走正常 join 逻辑 → 作为新玩家加入 ✅
```

### 场景 C: 关浏览器，5秒内回来

```
T+0s  关浏览器 → 断连检测成功 ✅
T+5s  重新打开浏览器 → 主页输入房间号 → joinRoom → existing 存在
      → onReconnect 取消定时器 → 回到房间 ✅
```

### 场景 D: 断连期间房间被解散

```
T+0s  玩家断连 → 标记 DISCONNECTED + 60s 定时器
T+1s  房主点击"解散房间" → endGameAndCleanup + registry.removeRoom
T+30s 玩家重连 → joinRoom → registry.findById 返回 null → 404
      → 前端提示"房间不存在" ✅
```

此场景下玩家无法重连（房间已不存在），行为正确——前端会收到 404 并提示用户。

## 七、涉及文件

| # | 文件 | 改动行数 |
|---|------|---------|
| 1 | `server/src/main/java/com/first/poker/service/RoomService.java` | +3（构造器参数）+12（重连检测 + lastActivity） |
| 2 | `server/src/main/java/com/first/poker/service/GameDisconnectHandler.java` | +2（onSessionDisconnect + onReconnect 各加 1 行 lastActivity） |
| 3 | `server/src/main/java/com/first/poker/controller/GameMessageController.java` | +1（processAction 加 lastActivity） |
| 4 | `server/src/main/java/com/first/poker/controller/RoomController.java` | +2（startGame + borrowChips 各加 1 行 lastActivity） |

**总计 4 个文件，约 20 行代码。**

## 八、设计决策记录（review 修正说明）

| 修正 | 原因 |
|------|------|
| `RoomService.joinRoom` 不直接调用 `setConnected(true)` / `setStatus(ACTIVE)` | 这些操作在 `executeWithLock` 外部执行，与 `syncRoomChips` / `onSessionDisconnect` 存在并发冲突（CRITICAL-3 同类问题）。全部委托给 `disconnectHandler.onReconnect()`，由它在 `executeWithLock` 内处理 |
| `RoomService` 注入 `GameDisconnectHandler` 使用 `@Lazy` | `GameDisconnectHandler` 已注入 `RoomService`，直接注入会形成循环依赖。Spring 可通过 `@Lazy` 安全打破 |
| `GameActionController.joinRoom` 不调用 `registerPlayer` | `RoomService.joinRoom` 内部已调用 `registerPlayer`，再次调用是重复注册 |
| `RoomService.addBots` 不需要 `registerPlayer` | Bot 不会断连，不需要断连检测 |
| lastActivity 单独列为修改 5 | 原设计遗漏了 lastActivity 更新，导致超过 30 分钟的牌局会被定时器误销毁。修改 5 在 6 个关键交互点重置倒计时 |
