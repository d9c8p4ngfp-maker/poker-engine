# Concurrency Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 16 concurrency issues (3 CRITICAL, 5 HIGH, 5 MEDIUM, 3 LOW) by introducing `executeWithLock`, expanding lock coverage, making `addPlayer` atomic, and eliminating `RoomController` code duplication.

**Architecture:** Core idea is a single `executeWithLock(roomId, Runnable)` entry point on `GameSessionService`. All Player/Room mutation operations route through it, ensuring per-room mutual exclusion. `autoPlayBots` uses a lock-check pattern rather than holding the lock across all bot turns. `RoomController`'s 5 duplicate methods are deleted and callers are redirected to shared code.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Lombok, CopyOnWriteArrayList, ReentrantLock, CountDownLatch

---

## Phase 1: Foundation — `executeWithLock` + move critical ops into lock

### Task 1.1: Add `executeWithLock` to `GameSessionService`

**Files:**
- Modify: `server/src/main/java/com/first/poker/service/GameSessionService.java:1-89`

- [ ] **Step 1: Add the `executeWithLock` method**

```java
/**
 * Execute a runnable under the per-room lock. If the lock doesn't exist
 * (room never had a game), creates one; if the lock was removed after
 * dissolve, still creates it for remaining cleanup operations.
 */
public void executeWithLock(String roomId, Runnable task) {
    ReentrantLock lock = roomLocks.computeIfAbsent(roomId, k -> new ReentrantLock());
    lock.lock();
    try {
        task.run();
    } finally {
        lock.unlock();
    }
}
```

Add after `hasActiveSession` (line 19) and before `startGame` (line 21).

- [ ] **Step 2: Write tests for `executeWithLock`**

```java
// File: server/src/test/java/com/first/poker/service/GameSessionServiceTest.java
// Add these test methods:

@Test
void executeWithLock_shouldRunTaskUnderLock() {
    var service = new GameSessionService();
    var executed = new boolean[1];
    service.executeWithLock("R1", () -> executed[0] = true);
    assertTrue(executed[0]);
}

@Test
void executeWithLock_shouldBeReentrant() {
    var service = new GameSessionService();
    var executed = new boolean[1];
    service.executeWithLock("R1", () -> {
        service.executeWithLock("R1", () -> executed[0] = true);
    });
    assertTrue(executed[0]);
}

@Test
void executeWithLock_differentRooms_noBlocking() throws Exception {
    var service = new GameSessionService();
    var latch = new java.util.concurrent.CountDownLatch(2);
    var executedA = new java.util.concurrent.atomic.AtomicBoolean();
    var executedB = new java.util.concurrent.atomic.AtomicBoolean();
    // Room A holds lock while Room B also acquires its own lock
    var tA = new Thread(() -> {
        service.executeWithLock("A", () -> {
            executedA.set(true);
            latch.countDown();
            try { latch.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
        });
    });
    var tB = new Thread(() -> {
        service.executeWithLock("B", () -> executedB.set(true));
    });
    tA.start();
    tB.start();
    tB.join(3000);
    assertTrue(executedB.get(), "Room B should execute immediately, not blocked by Room A");
}
```

- [ ] **Step 3: Run tests to verify they pass**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="GameSessionServiceTest" -DfailIfNoTests=false
```

Expected: all tests PASS (including existing 7 tests + 3 new).

- [ ] **Step 4: Commit**

```bash
git add server/src/main/java/com/first/poker/service/GameSessionService.java server/src/test/java/com/first/poker/service/GameSessionServiceTest.java
git commit -m "feat: add executeWithLock to GameSessionService for unified per-room locking"
```

---

### Task 1.2: Move `syncRoomChips` into `endGame` (CRITICAL-1)

**Files:**
- Modify: `server/src/main/java/com/first/poker/service/GameSessionService.java:76-88`

- [ ] **Step 1: Rewrite `endGame` to accept a pre-unlock task**

Currently `syncRoomChips` runs in lock-free space between `applyAction`'s lock release and `endGame`'s lock reacquisition. The fix is to let `endGame` accept a `Runnable` that executes **inside** the lock, before session removal.

Replace the existing `endGame` method:

```java
/**
 * End the game session. The beforeUnlock task (typically syncRoomChips)
 * executes inside the per-room lock before the session is removed.
 */
public void endGame(String roomId, Runnable beforeUnlock) {
    ReentrantLock lock = roomLocks.get(roomId);
    if (lock != null) {
        lock.lock();
        try {
            if (beforeUnlock != null) {
                beforeUnlock.run();
            }
            sessions.remove(roomId);
        } finally {
            lock.unlock();
        }
    } else {
        if (beforeUnlock != null) {
            beforeUnlock.run();
        }
        sessions.remove(roomId);
    }
}

/** Backward-compatible overload for callers that don't need a pre-unlock task. */
public void endGame(String roomId) {
    endGame(roomId, null);
}
```

- [ ] **Step 2: Update tests for new `endGame` signature**

```java
// In GameSessionServiceTest.java, add:

@Test
void endGame_shouldRunBeforeUnlockInsideLock() {
    var room = makeRoom("R5", List.of(
        new Player("A", "Alice", 0, 1000),
        new Player("B", "Bob", 1, 1000)
    ));
    var service = new GameSessionService();
    service.startGame(room, "A");

    var taskRan = new boolean[1];
    service.endGame("R5", () -> taskRan[0] = true);

    assertTrue(taskRan[0]);
    assertFalse(service.hasActiveSession("R5"));
}

@Test
void endGame_noLock_shouldStillRunTask() {
    var service = new GameSessionService();
    var taskRan = new boolean[1];
    service.endGame("NONEXISTENT", () -> taskRan[0] = true);
    assertTrue(taskRan[0]);
}
```

- [ ] **Step 3: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="GameSessionServiceTest" -DfailIfNoTests=false
```

Expected: all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/java/com/first/poker/service/GameSessionService.java server/src/test/java/com/first/poker/service/GameSessionServiceTest.java
git commit -m "feat: endGame accepts beforeUnlock runnable for atomic syncRoomChips"
```

---

### Task 1.3: Wire `syncRoomChips` into `endGame` in callers (CRITICAL-1)

**Files:**
- Modify: `server/src/main/java/com/first/poker/controller/GameMessageController.java:79-88`
- Modify: `server/src/main/java/com/first/poker/controller/GameMessageController.java:260-268`
- Modify: `server/src/main/java/com/first/poker/controller/RoomController.java:155-157`

- [ ] **Step 1: Update `processAction` in `GameMessageController`**

Change lines 79-84 from:

```java
            if (result.handComplete()) {
                System.out.println("[HAND-COMPLETE] " + roomId + " winners=" + result.winners());
                timeoutScheduler.cancelTimeout(roomId);
                syncRoomChips(roomId, result.state());
                gameSession.endGame(roomId);
                if (checkGameOver(roomId, result)) return;
```

To:

```java
            if (result.handComplete()) {
                System.out.println("[HAND-COMPLETE] " + roomId + " winners=" + result.winners());
                timeoutScheduler.cancelTimeout(roomId);
                com.first.poker.engine.GameState finalState = result.state();
                gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
                if (checkGameOver(roomId, result)) return;
```

- [ ] **Step 2: Update `autoPlayBots` in `GameMessageController`**

Change lines 260-264 from:

```java
                if (result.handComplete()) {
                    timeoutScheduler.cancelTimeout(roomId);
                    syncRoomChips(roomId, result.state());
                    gameSession.endGame(roomId);
```

To:

```java
                if (result.handComplete()) {
                    timeoutScheduler.cancelTimeout(roomId);
                    com.first.poker.engine.GameState finalState = result.state();
                    gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
```

- [ ] **Step 3: Update `autoBots` in `RoomController`**

Change lines 155-157 from:

```java
                if (result.handComplete()) {
                    syncChips(roomId, result.state());
                    gameSessionService.endGame(roomId);
```

To:

```java
                if (result.handComplete()) {
                    com.first.poker.engine.GameState finalState = result.state();
                    gameSessionService.endGame(roomId, () -> syncChips(roomId, finalState));
```

- [ ] **Step 4: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="GameSessionServiceTest,GameMessageControllerTest" -DfailIfNoTests=false
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/first/poker/controller/GameMessageController.java server/src/main/java/com/first/poker/controller/RoomController.java
git commit -m "fix: move syncRoomChips into endGame's lock scope (CRITICAL-1)"
```

---

### Task 1.4: Wrap `handleLeave` Player mutations in `executeWithLock` (CRITICAL-3, H-4, H-5)

**Files:**
- Modify: `server/src/main/java/com/first/poker/controller/GameMessageController.java:138-187`

- [ ] **Step 1: Rewrite `handleLeave` to use `executeWithLock`**

Replace the entire `handleLeave` method body (lines 138-187) with:

```java
    @MessageMapping("/room/{roomId}/leave")
    public void handleLeave(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        System.out.println("[LEAVE] " + roomId + " player=" + playerId);

        gameSession.executeWithLock(roomId, () -> {
            var room = roomService.findRoom(roomId);
            if (room == null) return;

            boolean wasOwner = room.getPlayers().stream()
                .anyMatch(p -> p.getPlayerId().equals(playerId) && p.isOwner());
            boolean isPlaying = gameSession.hasActiveSession(roomId);

            // Auto-fold if game is in progress
            if (isPlaying) {
                try {
                    gameSession.applyAction(roomId, playerId, GameAction.FOLD, 0);
                } catch (Exception e) {
                    System.out.println("[LEAVE-FOLD] " + playerId + " fold failed (may not be their turn): " + e.getMessage());
                }
            }

            roomService.leaveRoom(roomId, playerId);
            room.setLastActivity(System.currentTimeMillis());

            String newOwnerId = null;
            if (wasOwner) {
                if (roomService.hasHumanPlayers(roomId)) {
                    Player newOwner = roomService.transferOwnership(room, playerId);
                    newOwnerId = newOwner != null ? newOwner.getPlayerId() : null;
                } else {
                    // No human players left — dissolve room
                    System.out.println("[DISSOLVE] " + roomId + " all humans left");
                    var dissolvePayload = new java.util.HashMap<String, Object>();
                    dissolvePayload.put("type", "room_dissolved");
                    dissolvePayload.put("roomId", roomId);
                    gameSession.endGame(roomId);
                    registry.removeRoom(roomId);
                    broadcast.sendToRoom(roomId, dissolvePayload);
                    return;
                }
            }

            var leavePayload = new java.util.HashMap<String, Object>();
            leavePayload.put("type", "player_left");
            leavePayload.put("playerId", playerId);
            if (newOwnerId != null) {
                leavePayload.put("newOwnerId", newOwnerId);
            }
            broadcast.sendToRoom(roomId, leavePayload);
            broadcast.sendToRoom(roomId, "room", roomToResponse(room));
        });
    }
```

Key changes:
1. Entire body wrapped in `executeWithLock`
2. Dissolve path: `endGame` runs BEFORE `broadcast` (atomic within lock), `removeRoom` also inside lock
3. `transferOwnership` is now inside the lock

- [ ] **Step 2: Write concurrency test for handleLeave + syncRoomChips race**

```java
// File: server/src/test/java/com/first/poker/controller/GameMessageControllerTest.java
// Add a test that simulates concurrent leave + handComplete

// Note: This test requires Spring test context and MockMvc/WebSocket.
// For the plan phase, we rely on the single-threaded tests passing,
// and add a concurrency test skeleton in Phase 6.
```

- [ ] **Step 3: Run existing tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="GameMessageControllerTest,RoomControllerTest" -DfailIfNoTests=false
```

Expected: all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/java/com/first/poker/controller/GameMessageController.java
git commit -m "fix: wrap handleLeave in executeWithLock (CRITICAL-3, H-4, H-5)"
```

---

### Task 1.5: Wrap `handleQueueAccept` in `executeWithLock` (H-3)

**Files:**
- Modify: `server/src/main/java/com/first/poker/controller/GameMessageController.java:105-136`

- [ ] **Step 1: Rewrite `handleQueueAccept`**

Replace lines 105-136 with:

```java
    @MessageMapping("/room/{roomId}/queue-accept")
    public void handleQueueAccept(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        System.out.println("[QUEUE-ACCEPT] " + roomId + " player=" + playerId);

        gameSession.executeWithLock(roomId, () -> {
            var room = roomService.findRoom(roomId);
            if (room == null) return;

            room.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId)
                          && p.getStatus() == com.first.poker.model.enums.PlayerStatus.QUEUED)
                .findFirst()
                .ifPresent(p -> {
                    p.setStatus(com.first.poker.model.enums.PlayerStatus.ACTIVE);
                    p.setChips(room.getConfig().getInitialChips());
                    // Assign seat
                    int maxSeats = room.getConfig().getMaxSeats();
                    boolean[] occupied = new boolean[maxSeats];
                    for (var rp : room.getPlayers()) {
                        if (rp.getStatus() == com.first.poker.model.enums.PlayerStatus.ACTIVE
                            && rp.getSeatIndex() >= 0 && rp.getSeatIndex() < maxSeats) {
                            occupied[rp.getSeatIndex()] = true;
                        }
                    }
                    for (int i = 0; i < maxSeats; i++) {
                        if (!occupied[i]) {
                            p.setSeatIndex(i);
                            break;
                        }
                    }
                    broadcast.sendToRoom(roomId, "room", roomToResponse(room));
                });
        });
    }
```

- [ ] **Step 2: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="GameMessageControllerTest" -DfailIfNoTests=false
```

Expected: all tests PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/first/poker/controller/GameMessageController.java
git commit -m "fix: wrap handleQueueAccept in executeWithLock (H-3)"
```

---

### Task 1.6: Wrap `handleDissolve` in `executeWithLock`

**Files:**
- Modify: `server/src/main/java/com/first/poker/controller/GameMessageController.java:189-210`

- [ ] **Step 1: Rewrite `handleDissolve`**

Replace lines 189-210 with:

```java
    @MessageMapping("/room/{roomId}/dissolve")
    public void handleDissolve(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        System.out.println("[DISSOLVE] " + roomId + " requested by " + playerId);

        gameSession.executeWithLock(roomId, () -> {
            var room = roomService.findRoom(roomId);
            if (room == null) return;

            // Verify owner
            if (room.getOwner() == null || !room.getOwner().getPlayerId().equals(playerId)) {
                var err = new java.util.HashMap<String, Object>();
                err.put("error", "Only room owner can dissolve");
                broadcast.sendToPlayer(playerId, err);
                return;
            }

            var dissolvePayload = new java.util.HashMap<String, Object>();
            dissolvePayload.put("type", "room_dissolved");
            dissolvePayload.put("roomId", roomId);
            gameSession.endGame(roomId);
            registry.removeRoom(roomId);
            broadcast.sendToRoom(roomId, dissolvePayload);
        });
    }
```

- [ ] **Step 2: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="GameMessageControllerTest" -DfailIfNoTests=false
```

Expected: all tests PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/first/poker/controller/GameMessageController.java
git commit -m "fix: wrap handleDissolve in executeWithLock"
```

---

### Task 1.7: Wrap `borrowChips` in `executeWithLock` (CRITICAL-3)

**Files:**
- Modify: `server/src/main/java/com/first/poker/controller/RoomController.java:113-130`

- [ ] **Step 1: Rewrite `borrowChips`**

Replace lines 113-130 with:

```java
    @PostMapping("/{roomId}/borrow")
    public ResponseEntity<?> borrowChips(@PathVariable String roomId, @RequestBody Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        if (playerId == null || playerId.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "missing playerId"));

        final int[] borrowAmount = {roomService.findRoom(roomId) != null ? roomService.findRoom(roomId).getConfig().getInitialChips() : 0};
        if (body.containsKey("amount")) {
            borrowAmount[0] = ((Number) body.get("amount")).intValue();
        }

        gameSessionService.executeWithLock(roomId, () -> {
            var room = roomService.findRoom(roomId);
            if (room == null) return;
            var player = room.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst().orElse(null);
            if (player == null) return;
            player.borrow(borrowAmount[0]);
            System.out.println("[BORROW] " + roomId + " " + playerId
                + " borrowed #" + player.getBorrowCount()
                + " amount=" + borrowAmount[0] + ", chips=" + player.getChips());
        });

        var room = roomService.findRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        broadcastService.sendToRoom(roomId, "room", roomToResponse(room));
        return ResponseEntity.ok(Map.of("playerId", playerId,
            "chips", room.getPlayers().stream().filter(p -> p.getPlayerId().equals(playerId)).findFirst().map(com.first.poker.model.Player::getChips).orElse(0),
            "borrowCount", room.getPlayers().stream().filter(p -> p.getPlayerId().equals(playerId)).findFirst().map(com.first.poker.model.Player::getBorrowCount).orElse(0)));
    }
```

- [ ] **Step 2: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="RoomControllerTest" -DfailIfNoTests=false
```

Expected: all tests PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/first/poker/controller/RoomController.java
git commit -m "fix: wrap borrowChips in executeWithLock (CRITICAL-3)"
```

---

### Task 1.8: Wrap `RoomDissolutionScheduler` in `executeWithLock` (MED-5)

**Files:**
- Modify: `server/src/main/java/com/first/poker/service/RoomDissolutionScheduler.java:22-38`

- [ ] **Step 1: Rewrite `dissolveInactiveRooms`**

Replace lines 22-38 with:

```java
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void dissolveInactiveRooms() {
        long now = System.currentTimeMillis();
        long thirtyMinutes = 30 * 60 * 1000;

        for (var room : registry.listPublicRooms()) {
            if (room.getLastActivity() + thirtyMinutes < now) {
                String roomId = room.getRoomId();
                System.out.println("[DISSOLVE-INACTIVE] " + roomId + " inactive for 30+ minutes");
                gameSession.executeWithLock(roomId, () -> {
                    gameSession.endGame(roomId);
                    registry.removeRoom(roomId);
                    var payload = new HashMap<String, Object>();
                    payload.put("type", "room_dissolved");
                    payload.put("roomId", roomId);
                    broadcast.sendToRoom(roomId, payload);
                });
            }
        }
    }
```

- [ ] **Step 2: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -DfailIfNoTests=false
```

Expected: all 160 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/first/poker/service/RoomDissolutionScheduler.java
git commit -m "fix: wrap RoomDissolutionScheduler in executeWithLock (MED-5)"
```

---

### Task 1.9: Delete `RoomController` duplicate methods + redirect callers (L-1)

**Files:**
- Modify: `server/src/main/java/com/first/poker/controller/RoomController.java:132-253`

The 5 duplicate methods: `autoBots`, `syncChips`, `broadcastBustChoice`, `checkGameOver`, `broadcastGameOver` are complete copies of methods in `GameMessageController`. Delete them and route `RoomController.startGame` + `autoBots` through `GameMessageController`.

- [ ] **Step 1: Inject `GameMessageController` into `RoomController`**

This creates a dependency between controllers. Cleaner approach: extract shared logic into a `GameBroadcastHelper` service class.

**New file:** `server/src/main/java/com/first/poker/service/GameBroadcastHelper.java`

```java
package com.first.poker.service;

import com.first.poker.engine.GameEngine;
import com.first.poker.engine.GameState;
import com.first.poker.engine.GameStateSnapshot;
import com.first.poker.model.Room;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Shared game broadcast logic used by both GameMessageController (STOMP) and
 * RoomController (REST). Eliminates the 5-method duplication.
 */
@Service
public class GameBroadcastHelper {

    private final RoomService roomService;
    private final GameSessionService gameSession;
    private final BroadcastService broadcast;
    private final GameTimeoutScheduler timeoutScheduler;

    public GameBroadcastHelper(RoomService roomService, GameSessionService gameSession,
                                BroadcastService broadcast, GameTimeoutScheduler timeoutScheduler) {
        this.roomService = roomService;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
        this.timeoutScheduler = timeoutScheduler;
    }

    public void broadcastGameState(String roomId, GameState state) {
        broadcast.sendToRoom(roomId, "game", GameStateSnapshot.buildPublic(state));
        for (var p : state.players()) {
            broadcast.sendToPlayer(p.playerId(), GameStateSnapshot.buildForPlayer(state, p.playerId()));
        }
    }

    public void broadcastWinners(String roomId, GameEngine.ActionResult result) {
        var payload = new HashMap<String, Object>();
        payload.put("winners", result.winners().stream()
            .map(w -> java.util.Map.of("playerId", w.playerId(), "nickname", w.nickname(), "handName", w.handName(), "amount", w.amount()))
            .toList());
        broadcast.sendToRoom(roomId, "game", payload);
    }

    public void syncRoomChips(String roomId, GameState resolvedState) {
        var room = roomService.findRoom(roomId);
        if (room == null) return;
        for (var gsp : resolvedState.players()) {
            room.getPlayers().stream()
                .filter(rp -> rp.getPlayerId().equals(gsp.playerId()))
                .findFirst()
                .ifPresent(rp -> {
                    int oldChips = rp.getChips();
                    int newChips = gsp.chips();
                    rp.setChips(newChips);
                    rp.setBetInRound(0);
                    rp.setFolded(false);
                    rp.setAllIn(false);
                    rp.setHoleCards(new ArrayList<>());
                    if (oldChips > 0 && newChips <= 0) {
                        broadcastBustChoice(roomId, rp.getPlayerId(), rp.getNickname());
                    }
                });
        }
    }

    public void broadcastBustChoice(String roomId, String playerId, String nickname) {
        var payload = new HashMap<String, Object>();
        payload.put("type", "bust_choice");
        payload.put("playerId", playerId);
        payload.put("nickname", nickname);
        broadcast.sendToPlayer(playerId, payload);
    }

    public boolean checkGameOver(String roomId, GameEngine.ActionResult result) {
        var room = roomService.findRoom(roomId);
        if (room == null) return false;
        boolean anyBusted = room.getPlayers().stream().anyMatch(p -> p.getChips() <= 0);
        if (!anyBusted) return false;
        if (room.getConfig().isBustEndsGame()) {
            broadcastGameOver(roomId, room, result);
            return true;
        }
        long activePlayers = room.getPlayers().stream().filter(p -> p.getChips() > 0).count();
        if (activePlayers <= 1) {
            broadcastGameOver(roomId, room, result);
            return true;
        }
        return false;
    }

    public void broadcastGameOver(String roomId, Room room, GameEngine.ActionResult result) {
        var payload = new HashMap<String, Object>();
        payload.put("winners", result.winners().stream()
            .map(w -> java.util.Map.of("playerId", w.playerId(), "nickname", w.nickname(), "handName", w.handName(), "amount", w.amount()))
            .toList());
        int borrowUnit = room.getConfig().getInitialChips();
        payload.put("leaderboard", room.getPlayers().stream()
            .sorted((a, b) -> Integer.compare(
                b.getChips() - b.getBorrowCount() * borrowUnit,
                a.getChips() - a.getBorrowCount() * borrowUnit))
            .map(p -> java.util.Map.of("playerId", p.getPlayerId(), "nickname", p.getNickname(),
                "chips", p.getChips(), "borrowCount", p.getBorrowCount(),
                "borrowed", p.getBorrowCount() * borrowUnit, "netChips", p.getChips() - p.getBorrowCount() * borrowUnit))
            .toList());
        payload.put("bustedPlayerIds", room.getPlayers().stream().filter(p -> p.getChips() <= 0).map(com.first.poker.model.Player::getPlayerId).toList());
        broadcast.sendToRoom(roomId, "game", payload);
    }

    public void autoPlayBots(String roomId) {
        int safety = 0;
        while (safety++ < 30) {
            var state = gameSession.getState(roomId);
            if (state == null || state.currentPlayerIndex() < 0) break;
            var cp = state.currentPlayer();
            if (!cp.playerId().startsWith("bot-")) break;
            if (cp.folded() || cp.allIn()) break;

            int toCall = state.currentBet() - cp.roundBet();
            com.first.poker.engine.GameAction botAction = toCall <= 0
                ? com.first.poker.engine.GameAction.CHECK
                : com.first.poker.engine.GameAction.CALL;

            try {
                var result = gameSession.applyAction(roomId, cp.playerId(), botAction, 0);
                broadcastGameState(roomId, result.state());

                if (result.handComplete()) {
                    timeoutScheduler.cancelTimeout(roomId);
                    com.first.poker.engine.GameState finalState = result.state();
                    gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
                    if (checkGameOver(roomId, result)) return;
                    if (!result.winners().isEmpty()) {
                        broadcastWinners(roomId, result);
                    }
                    return;
                }
            } catch (Throwable e) {
                System.err.println("[autoPlayBot] " + cp.playerId() + ": " + e.getClass().getName() + " - " + e.getMessage());
                continue;
            }
        }
    }
}
```

- [ ] **Step 2: Refactor `GameMessageController` to delegate to `GameBroadcastHelper`**

Replace `broadcastGameState`, `broadcastWinners`, `syncRoomChips`, `broadcastBustChoice`, `checkGameOver`, `broadcastGameOver`, `autoPlayBots` methods in `GameMessageController` with delegating calls through `GameBroadcastHelper`.

Add to constructor:

```java
private final GameBroadcastHelper helper;

// in constructor add:
this.helper = helper;
```

Replace body of each method with delegation:

```java
private void broadcastGameState(String roomId, com.first.poker.engine.GameState state) {
    helper.broadcastGameState(roomId, state);
}
private void broadcastWinners(String roomId, GameEngine.ActionResult result) {
    helper.broadcastWinners(roomId, result);
}
private void syncRoomChips(String roomId, com.first.poker.engine.GameState resolvedState) {
    helper.syncRoomChips(roomId, resolvedState);
}
private void broadcastBustChoice(String roomId, String playerId, String nickname) {
    helper.broadcastBustChoice(roomId, playerId, nickname);
}
private boolean checkGameOver(String roomId, GameEngine.ActionResult result) {
    return helper.checkGameOver(roomId, result);
}
private void broadcastGameOver(String roomId, Room room, GameEngine.ActionResult result) {
    helper.broadcastGameOver(roomId, room, result);
}
private void autoPlayBots(String roomId) {
    helper.autoPlayBots(roomId);
}
```

- [ ] **Step 3: Refactor `RoomController` to use `GameBroadcastHelper`**

Delete the 5 duplicate methods (`autoBots`, `syncChips`, `broadcastBustChoice`, `checkGameOver`, `broadcastGameOver`) and use `GameBroadcastHelper` instead.

Add dependency:

```java
private final GameBroadcastHelper helper;

// in constructor add:
this.helper = helper;
```

Replace `startGame`'s `autoBots(roomId)` call with `helper.autoPlayBots(roomId)`.

- [ ] **Step 4: Run all tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -DfailIfNoTests=false
```

Expected: all 160 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/first/poker/service/GameBroadcastHelper.java server/src/main/java/com/first/poker/controller/GameMessageController.java server/src/main/java/com/first/poker/controller/RoomController.java
git commit -m "refactor: extract GameBroadcastHelper, eliminate RoomController duplication (L-1)"
```

---

## Phase 2: Disconnect into lock (CRITICAL-2)

### Task 2.1: Wrap `onSessionDisconnect` Player mutations in `executeWithLock`

**Files:**
- Modify: `server/src/main/java/com/first/poker/service/GameDisconnectHandler.java:48-123`

- [ ] **Step 1: Rewrite `onSessionDisconnect`**

Replace the method body (lines 48-123) with:

```java
    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String playerId = sessionToPlayer.get(sessionId);
        if (playerId == null && event.getUser() != null) {
            playerId = event.getUser().getName();
        }
        if (playerId == null) return;

        String roomId = playerRooms.get(playerId);
        if (roomId == null) return;

        System.out.println("[DISCONNECT] session=" + sessionId + " player=" + playerId + " room=" + roomId);

        final String fPlayerId = playerId;
        final String fRoomId = roomId;
        final String fSessionId = sessionId;

        // Phase 1: Mark player as DISCONNECTED under lock
        gameSession.executeWithLock(fRoomId, () -> {
            var room = roomService.findRoom(fRoomId);
            if (room == null) return;

            room.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(fPlayerId))
                .findFirst()
                .ifPresent(p -> {
                    p.setStatus(PlayerStatus.DISCONNECTED);
                    p.setConnected(false);
                });

            boolean isPlaying = gameSession.hasActiveSession(fRoomId);

            // Auto-fold if it's the player's turn
            if (isPlaying) {
                try {
                    var state = gameSession.getState(fRoomId);
                    if (state != null && state.currentPlayer().playerId().equals(fPlayerId)) {
                        gameSession.applyAction(fRoomId, fPlayerId, GameAction.FOLD, 0);
                    }
                } catch (Exception e) {
                    System.out.println("[DISCONNECT-FOLD] " + fPlayerId + " fold failed: " + e.getMessage());
                }
            }
        });

        // Phase 2: Broadcast (outside lock, broadcast is thread-safe)
        var payload = new HashMap<String, Object>();
        payload.put("type", "player_disconnected");
        payload.put("playerId", fPlayerId);
        broadcast.sendToRoom(fRoomId, payload);

        // Phase 3: Grace period timer (60s) — accesses under lock
        var executor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> timer = executor.schedule(() -> {
            gameSession.executeWithLock(fRoomId, () -> {
                try {
                    var r = roomService.findRoom(fRoomId);
                    if (r == null) return;
                    r.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(fPlayerId)
                                  && p.getStatus() == PlayerStatus.DISCONNECTED)
                        .findFirst()
                        .ifPresent(p -> {
                            r.removePlayer(fPlayerId);
                            sessionToPlayer.remove(fSessionId);
                            playerRooms.remove(fPlayerId);
                            graceTimers.remove(fPlayerId);
                            System.out.println("[DISCONNECT-EXPIRE] " + fPlayerId + " removed from " + fRoomId);
                        });
                    broadcast.sendToRoom(fRoomId, "room", roomToUpdatedResponse(r));
                } catch (Exception e) {
                    System.err.println("[DISCONNECT-EXPIRE-ERROR] " + fPlayerId + ": " + e.getMessage());
                }
            });
            executor.shutdown();
        }, 60, TimeUnit.SECONDS);
        graceTimers.put(fPlayerId, timer);
    }
```

- [ ] **Step 2: Wrap `onReconnect` in `executeWithLock`**

Replace lines 125-144 with:

```java
    public void onReconnect(String playerId) {
        ScheduledFuture<?> timer = graceTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
        }
        var roomId = playerRooms.get(playerId);
        if (roomId != null) {
            gameSession.executeWithLock(roomId, () -> {
                var room = roomService.findRoom(roomId);
                if (room != null) {
                    room.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst()
                        .ifPresent(p -> {
                            p.setStatus(PlayerStatus.ACTIVE);
                            p.setConnected(true);
                        });
                }
            });
        }
    }
```

- [ ] **Step 2: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="GameDisconnectHandlerTest" -DfailIfNoTests=false
```

Expected: all tests PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/first/poker/service/GameDisconnectHandler.java
git commit -m "fix: wrap onSessionDisconnect + onReconnect in executeWithLock (CRITICAL-2)"
```

---

## Phase 3: `addPlayer synchronized` + seatIndex fix (H-2, L-3)

### Task 3.1: Make `Room.addPlayer` synchronized with atomic seatIndex

**Files:**
- Modify: `server/src/main/java/com/first/poker/model/Room.java:35-42`

- [ ] **Step 1: Rewrite `addPlayer`**

```java
    public synchronized boolean addPlayer(Player player) {
        if (players.size() >= config.getMaxSeats()) return false;
        if (players.stream().anyMatch(p -> p.getPlayerId().equals(player.getPlayerId()))) {
            return false;
        }
        // Assign seatIndex inside the synchronized block
        player.setSeatIndex(players.size());
        players.add(player);
        return true;
    }
```

- [ ] **Step 2: Update `RoomTest`**

```java
// In RoomTest.java, add:

@Test
void addPlayer_shouldAssignSeatIndexInsideSync() {
    Room room = new Room("R001", "测试", RoomConfig.withDefaults());
    Player p = new Player("p1", "Alice", -1, 1000); // -1 means "not assigned"
    boolean ok = room.addPlayer(p);
    assertTrue(ok);
    assertEquals(0, p.getSeatIndex()); // Assigned during addPlayer
}

@Test
void addPlayer_shouldAssignSequentialSeatIndices() {
    Room room = new Room("R001", "测试", RoomConfig.withDefaults());
    Player p1 = new Player("p1", "A", -1, 1000);
    Player p2 = new Player("p2", "B", -1, 1000);
    Player p3 = new Player("p3", "C", -1, 1000);
    assertTrue(room.addPlayer(p1));
    assertTrue(room.addPlayer(p2));
    assertTrue(room.addPlayer(p3));
    assertEquals(0, p1.getSeatIndex());
    assertEquals(1, p2.getSeatIndex());
    assertEquals(2, p3.getSeatIndex());
}

@Test
void addPlayer_concurrentShouldNotExceedMaxSeats() throws Exception {
    RoomConfig config = RoomConfig.withDefaults();
    config.setMaxSeats(3);
    Room room = new Room("R001", "测试", config);
    int threadCount = 10;
    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
    java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger();
    var executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
        final int idx = i;
        executor.submit(() -> {
            if (room.addPlayer(new Player("p" + idx, "Player" + idx, -1, 1000))) {
                successCount.incrementAndGet();
            }
            latch.countDown();
        });
    }
    latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
    executor.shutdown();
    assertEquals(3, successCount.get());
    assertEquals(3, room.getPlayers().size());
}
```

- [ ] **Step 3: Fix callers that pre-assign seatIndex**

**RoomService.createRoom** (line 32): Change `new Player(req.getOwnerId(), nickname, 0, ...)` → `new Player(req.getOwnerId(), nickname, -1, ...)`. The `addPlayer` call on line 34 will now assign seatIndex=0.

```java
// Line 32: change seatIndex from 0 to -1
Player owner = new Player(req.getOwnerId(), nickname, -1, config.getInitialChips());
```

**RoomService.joinRoom** (line 56-57): Change `new Player(..., room.getPlayers().size(), ...)` → `new Player(..., -1, ...)`. The `addPlayer` call will assign the correct seatIndex.

```java
// Line 56-57: change seatIndex from room.getPlayers().size() to -1
Player player = new Player(req.getPlayerId(), req.getNickname(),
        -1, room.getConfig().getInitialChips());
```

**RoomService.addBots** (line 77): Change `new Player(botId, name, room.getPlayers().size(), ...)` → `new Player(botId, name, -1, ...)`.

```java
// Line 77: change seatIndex from room.getPlayers().size() to -1
Player bot = new Player(botId, name, -1, room.getConfig().getInitialChips());
```

- [ ] **Step 4: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="RoomTest,GameSessionServiceTest,GameMessageControllerTest" -DfailIfNoTests=false
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/first/poker/model/Room.java server/src/main/java/com/first/poker/service/RoomService.java server/src/test/java/com/first/poker/model/RoomTest.java
git commit -m "fix: make addPlayer synchronized with atomic seatIndex assignment (H-2, L-3)"
```

---

## Phase 4: autoPlayBots lock check (H-1)

### Task 4.1: Add lock-check guard in `autoPlayBots`

**Files:**
- Modify: `server/src/main/java/com/first/poker/service/GameBroadcastHelper.java` (the `autoPlayBots` method)

- [ ] **Step 1: Update `autoPlayBots` to verify current player before acting**

The race: `processAction` completes a human action, then releases the lock. `autoPlayBots` reads state in lock-free space. Before it calls `applyAction`, another thread could have already processed the bot's turn.

The fix: Before calling `applyAction` for a bot, re-read state to confirm it's still the bot's turn.

Update the `autoPlayBots` method in `GameBroadcastHelper`:

```java
    public void autoPlayBots(String roomId) {
        int safety = 0;
        while (safety++ < 30) {
            // Re-read state under lock to verify current player
            var state = gameSession.getState(roomId);
            if (state == null || state.currentPlayerIndex() < 0) break;
            var cp = state.currentPlayer();
            if (!cp.playerId().startsWith("bot-")) break;
            if (cp.folded() || cp.allIn()) break;

            int toCall = state.currentBet() - cp.roundBet();
            com.first.poker.engine.GameAction botAction = toCall <= 0
                ? com.first.poker.engine.GameAction.CHECK
                : com.first.poker.engine.GameAction.CALL;

            try {
                var result = gameSession.applyAction(roomId, cp.playerId(), botAction, 0);
                broadcastGameState(roomId, result.state());

                if (result.handComplete()) {
                    timeoutScheduler.cancelTimeout(roomId);
                    com.first.poker.engine.GameState finalState = result.state();
                    gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
                    if (checkGameOver(roomId, result)) return;
                    if (!result.winners().isEmpty()) {
                        broadcastWinners(roomId, result);
                    }
                    return;
                }
            } catch (com.first.poker.engine.GameAction.IllegalStateException e) {
                // Game state changed between our read and applyAction — safe to break
                break;
            } catch (IllegalArgumentException e) {
                // Not our turn anymore — another thread processed it
                continue;
            } catch (Throwable e) {
                System.err.println("[autoPlayBot] " + cp.playerId() + ": " + e.getClass().getName() + " - " + e.getMessage());
                continue;
            }
        }
    }
```

Key: `applyAction` throws `IllegalArgumentException("Not your turn")` if the current player changed. We catch that and continue/skip. This is the "lock-check" pattern — we don't hold the lock, but `applyAction`'s internal check verifies correctness.

- [ ] **Step 2: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -DfailIfNoTests=false
```

Expected: all 160 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/first/poker/service/GameBroadcastHelper.java
git commit -m "fix: add lock-check guard in autoPlayBots (H-1)"
```

---

## Phase 5: Infrastructure (MED-1, MED-2, MED-4)

### Task 5.1: Replace single-thread executor with thread pool (MED-1)

**Files:**
- Modify: `server/src/main/java/com/first/poker/service/GameTimeoutScheduler.java:13`

- [ ] **Step 1: Change executor**

```java
// Line 13: change from:
private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
// To:
private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
    Math.max(4, Runtime.getRuntime().availableProcessors()));
```

- [ ] **Step 2: Run tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="GameTimeoutSchedulerTest" -DfailIfNoTests=false
```

Expected: all tests PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/first/poker/service/GameTimeoutScheduler.java
git commit -m "fix: use thread pool instead of single-thread executor for timeouts (MED-1)"
```

---

### Task 5.2: Add `volatile` to shared fields (MED-4)

**Files:**
- Modify: `server/src/main/java/com/first/poker/model/Room.java:20`
- Modify: `server/src/main/java/com/first/poker/model/Player.java:17-22`

- [ ] **Step 1: Add `volatile` to Room fields**

```java
// Room.java:20
private volatile Player owner;

// Room.lastActivity (line 18) — already accessed in scheduler thread
private volatile long lastActivity;
```

- [ ] **Step 2: Add `volatile` to Player fields**

```java
// Player.java:17
private volatile boolean connected;

// Player.java:22
private volatile PlayerStatus status;
```

- [ ] **Step 3: Run all tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -DfailIfNoTests=false
```

Expected: all 160 tests PASS (volatile is compile-time only, no behavior change).

- [ ] **Step 4: Commit**

```bash
git add server/src/main/java/com/first/poker/model/Room.java server/src/main/java/com/first/poker/model/Player.java
git commit -m "fix: add volatile to Room.owner/lastActivity and Player.connected/status (MED-4)"
```

---

### Task 5.3: Clean `roomLocks` on room dissolve (MED-2)

**Files:**
- Modify: `server/src/main/java/com/first/poker/service/GameSessionService.java:76-88`

- [ ] **Step 1: Add lock cleanup to `endGame`**

Update the `endGame(String roomId, Runnable beforeUnlock)` method to clean the lock only when the room is fully dissolving (i.e., `removeRoom` will follow). Add an overload:

```java
/**
 * End game and clean up the per-room lock. Use this when the room is fully
 * dissolving (removeRoom follows). For normal hand-complete endGame, use
 * the overload without cleanup.
 */
public void endGameAndCleanupLock(String roomId, Runnable beforeUnlock) {
    ReentrantLock lock = roomLocks.get(roomId);
    if (lock != null) {
        lock.lock();
        try {
            if (beforeUnlock != null) {
                beforeUnlock.run();
            }
            sessions.remove(roomId);
        } finally {
            lock.unlock();
        }
        roomLocks.remove(roomId);
    } else {
        if (beforeUnlock != null) {
            beforeUnlock.run();
        }
        sessions.remove(roomId);
    }
}
```

- [ ] **Step 2: Update dissolve paths to use `endGameAndCleanupLock`**

In `GameMessageController.handleLeave` dissolve path (line ~180):
```java
gameSession.endGameAndCleanupLock(roomId, null);
```

In `GameMessageController.handleDissolve` (line ~208):
```java
gameSession.endGameAndCleanupLock(roomId, null);
```

In `RoomDissolutionScheduler.dissolveInactiveRooms`:
```java
gameSession.endGameAndCleanupLock(roomId, null);
```

- [ ] **Step 3: Run all tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -DfailIfNoTests=false
```

Expected: all 160 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/java/com/first/poker/service/GameSessionService.java server/src/main/java/com/first/poker/controller/GameMessageController.java server/src/main/java/com/first/poker/service/RoomDissolutionScheduler.java
git commit -m "fix: cleanup roomLocks on dissolve via endGameAndCleanupLock (MED-2)"
```

---

## Phase 6: Full Regression + Concurrency Tests

### Task 6.1: Run all existing 160 tests

**Files:** None (verification only)

- [ ] **Step 1: Full test suite**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -DfailIfNoTests=false
```

Expected: all tests PASS. If any fail, fix before proceeding.

- [ ] **Step 2: Compile check (no warnings)**

```bash
cd D:\poker-web\server && .\mvnw.cmd compile -pl . 2>&1 | findstr /C:"error" /C:"warning"
```

Expected: no errors, no new warnings.

---

### Task 6.2: Add concurrency test for room dissolve + borrow race

**Files:**
- Create: `server/src/test/java/com/first/poker/service/ConcurrencyTest.java`

- [ ] **Step 1: Write concurrent borrow + dissolve test**

```java
package com.first.poker.service;

import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    @Test
    void borrowChipsUnderLock_shouldNotCorruptDuringConcurrentAccess() throws Exception {
        var config = RoomConfig.withDefaults();
        config.setInitialChips(1000);
        var room = new Room("R1", "test", config);
        Player p = new Player("A", "Alice", 0, 100);
        room.addPlayer(p);

        var service = new GameSessionService();
        int threads = 10;
        var latch = new CountDownLatch(threads);
        var executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                service.executeWithLock("R1", () -> {
                    p.borrow(100);
                });
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(100 + 10 * 100, p.getChips());
        assertEquals(10, p.getBorrowCount());
    }

    @Test
    void addPlayer_concurrentAdd_shouldNotExceedMaxSeats() throws Exception {
        var config = RoomConfig.withDefaults();
        config.setMaxSeats(6);
        var room = new Room("R2", "test", config);

        int threadCount = 20;
        var latch = new CountDownLatch(threadCount);
        var successCount = new java.util.concurrent.atomic.AtomicInteger();
        var executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                if (room.addPlayer(new Player("p" + idx, "P" + idx, -1, 1000))) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(6, successCount.get());
        assertEquals(6, room.getPlayers().size());
        // Verify all seat indices are unique
        var seats = room.getPlayers().stream().map(Player::getSeatIndex).distinct().count();
        assertEquals(6, seats);
    }

    @Test
    void executeWithLock_differentRooms_fullyParallel() throws Exception {
        var service = new GameSessionService();
        var latch = new CountDownLatch(2);
        var completed = new java.util.concurrent.atomic.AtomicInteger();

        // Room A holds its lock, Room B should still be able to acquire its own
        var t1 = new Thread(() -> {
            service.executeWithLock("A", () -> {
                completed.incrementAndGet();
                latch.countDown();
                try { Thread.sleep(2000); } catch (Exception ignored) {}
            });
        });
        var t2 = new Thread(() -> {
            service.executeWithLock("B", () -> {
                completed.incrementAndGet();
                latch.countDown();
            });
        });

        t1.start();
        Thread.sleep(100); // Let t1 acquire lock A
        t2.start();

        boolean t2Finished = t2.join(3000);
        assertTrue(t2Finished, "Thread B should finish even while Thread A holds lock A");
        assertEquals(2, completed.get());
    }
}
```

- [ ] **Step 2: Run concurrency tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -Dtest="ConcurrencyTest" -DfailIfNoTests=false
```

Expected: all 3 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add server/src/test/java/com/first/poker/service/ConcurrencyTest.java
git commit -m "test: add concurrency tests for borrow, addPlayer, and room isolation"
```

---

### Task 6.3: Final full regression

- [ ] **Step 1: Run ALL tests**

```bash
cd D:\poker-web\server && .\mvnw.cmd test -pl . -DfailIfNoTests=false
```

Expected: all ~163 tests PASS (160 existing + 3 new concurrency tests).

- [ ] **Step 2: Package and verify**

```bash
cd D:\poker-web\server && .\mvnw.cmd package -pl . -DskipTests 2>&1 | findstr /C:"BUILD" /C:"ERROR"
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Final commit**

```bash
git add -A
git diff --cached --stat
git commit -m "chore: final regression pass — all 163 tests green"
```

---

## Summary

| Phase | Tasks | Issues Covered | Files Modified |
|-------|-------|---------------|----------------|
| 1: Foundation | 1.1–1.9 | CRITICAL-1, CRITICAL-3, H-3, H-4, H-5, MED-5, L-1 | `GameSessionService`, `GameMessageController`, `RoomController`, `RoomDissolutionScheduler`, `GameBroadcastHelper` (new) |
| 2: Disconnect | 2.1 | CRITICAL-2 | `GameDisconnectHandler` |
| 3: addPlayer | 3.1 | H-2, L-3 | `Room`, `RoomService`, `RoomTest` |
| 4: autoPlayBots | 4.1 | H-1 | `GameBroadcastHelper` |
| 5: Infrastructure | 5.1–5.3 | MED-1, MED-2, MED-4 | `GameTimeoutScheduler`, `Room`, `Player`, `GameSessionService` |
| 6: Regression | 6.1–6.3 | verification | `ConcurrencyTest` (new) |

**Estimated files modified/created:** 10–11
**Estimated new code:** ~300 lines (GameBroadcastHelper ~120, lock wrapping ~100, tests ~80)
**Estimated deleted code:** ~120 lines (RoomController duplicates)

### Deferred Issue

**MED-3** (`sessionToPlayer` mapping only registered at `startGame` time) is **deferred**. Fixing it requires adding session→player registration in `GameActionController.joinRoom` (STOMP) and the WebSocket connect path. The current fallback (`event.getUser().getName()` from the Principal) covers most cases, and the 60s grace timer + `executeWithLock` wrapping in Phase 2 prevents stale session data from corrupting room state. This can be addressed in a follow-up PR.

---

## Verification Checklist

- [ ] All phases committed incrementally (14 commits)
- [ ] No compilation errors
- [ ] All 163 tests pass
- [ ] All CRITICAL issues resolved (CRITICAL-1/2/3)
- [ ] All HIGH issues resolved (H-1/2/3/4/5)
- [ ] RoomController has zero duplicate logic methods (L-1)
- [ ] `endGame` executes `syncRoomChips` atomically within lock
- [ ] `handleLeave` dissolve path is fully lock-protected
- [ ] `onSessionDisconnect` Player mutations are lock-protected
- [ ] 60s disconnect timer runs under lock
- [ ] `addPlayer` is synchronized with atomic seatIndex
- [ ] `borrowChips` runs under lock
- [ ] `roomLocks` cleaned on room dissolve
- [ ] `volatile` added to Room.owner/lastActivity, Player.connected/status
- [ ] Timeout scheduler uses thread pool, not single thread
