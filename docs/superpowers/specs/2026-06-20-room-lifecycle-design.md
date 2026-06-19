# Room Lifecycle — Design Specification

**Date:** 2026-06-20  
**Status:** Draft — pending user review  
**Context:** The current room system has no concept of player leave, no disconnection handling that works, and rooms live forever in memory until server restart. This design makes rooms server-persistent (within the JVM process, not across restarts), well-behaved under player leave/rejoin, and enables spectator/mid-game join flows.

---

## 1. Existing Code Fundamentals (Do Not Break)

Before proposing changes, here's what the codebase actually does today. Every design decision must respect these constraints or explicitly replace them.

### 1.1 Room Storage & Identity
```java
// RoomRegistry.java — the only place rooms live
ConcurrentHashMap<String, Room> rooms; // keyed by 6-char alphanumeric roomId
```
- No database. No persistence. Server restart = all rooms gone.
- RoomId is generated once at creation, stable for the room's lifetime.

### 1.2 Room Model (Current)
```java
// Room.java
String roomId;
String name;
RoomStatus status; // WAITING | PLAYING | FINISHED
RoomConfig config;  // maxSeats, initialChips, blinds, bustEndsGame, etc.
List<Player> players; // ArrayList — NOT thread-safe
int dealerIndex;     // rotates each hand
long createdAt;
int handCount;
```

### 1.3 Player Model (Current)
```java
// Player.java
String playerId;     // human: localStorage UUID; bot: "bot-{roomId}-{N}"
String nickname;
int seatIndex;
int chips;
boolean connected;   // set at creation time, never updated
int borrowCount;     // recent addition
```
**No `owner` field exists.** Ownership is derived: `players.get(0)` is the room owner.

### 1.4 Room Lifecycle (Current) and the "Status" Problem
```
User POST /api/rooms         → createRoom → Room.status = WAITING (CONSTRUCTOR ONLY — never modified again)
                                 ↓
Other user POST /join         → joinRoom → adds Player to ArrayList (no status check)
                                 ↓
Owner POST /start or STOMP    → startGame → puts GameState into GameSessionService.sessions map
                                 ↓ Game plays...                  Room.status is STILL "WAITING"
Hand ends                     → checkGameOver → broadcasts game_over or winners
                                 ↓                                Room.status is STILL "WAITING"
```

**Critical fact:** `Room.status` is set to `WAITING` in the constructor and **never modified by any code path**. The game state is tracked independently by `GameSessionService.sessions` (a `ConcurrentHashMap<String, GameState>`). The frontend receives two different "status" values from two different WebSocket channels:

| Channel | Source of "status" value | What it says |
|---------|-------------------------|--------------|
| `/topic/room/{roomId}` | `roomToResponse()` → `Room.getStatus().name()` | Always `"WAITING"` |
| `/topic/room/{roomId}/game` | `GameStateSnapshot.buildPublic()` → `phase==SHOWDOWN ? "FINISHED" : "PLAYING"` | `"PLAYING"` or `"FINISHED"` |

Frontend `roomStore.status` is first set from the room channel (WAITING), then overwritten by the game channel (PLAYING/FINISHED). This works today because messages arrive in order and overwrite.

**Design decision — do NOT merge these two status sources into one.** Maintaining `Room.status` in sync with `GameSessionService.sessions` would require updating it in every code path (startGame, processAction, endGame, checkGameOver) across two controllers, creating a synchronization burden with no real benefit. Instead, this design uses `GameSessionService.sessions.containsKey(roomId)` as the single source of truth for "is a hand in progress".

See §3.4 for the join guard implementation.

**Key gaps in current code:**
- There is no "leave" flow. Player who closes tab just disconnects silently.
- `GameDisconnectHandler.handleDisconnect()` callback exists but is never wired to `SessionDisconnectEvent` — no `@EventListener` annotation anywhere.
- `Room.handCount` is initialized to 0 and never incremented.
- `Player.connected` is set to `true` in constructor and never updated.
- `Player.lastAction` exists as a field but is never written.

### 1.5 WebSocket Channels (Current)
```
/topic/room/{roomId}          → room state updates (players, status, blinds)
/topic/room/{roomId}/game     → game snapshot, winners, game_over
/topic/player/{playerId}/game → private: hole cards, bust_choice, errors
```

### 1.6 Current Join Rules (Factual)
`RoomService.joinRoom()` has **no status check**. It's callable even during PLAYING. The only guard is `room.addPlayer()` which checks maxSeats and duplicate playerId.

### 1.7 Current "Owner" Detection (Factual)
```java
// GameMessageController.startGame line 19:
if (room.getPlayers().isEmpty() || !room.getPlayers().get(0).getPlayerId().equals(requesterId))
// RoomView.vue isOwner:
roomStore.players[0].playerId === userStore.playerId
```
Owner = index 0. When owner's player object is removed, the next player at index 0 silently becomes owner.

### 1.8 Known Concurrency Gaps (Not fixed in this design — documented for awareness)
- `Room.players` is `ArrayList` → concurrent add/remove is unsafe
- `GameSessionService` read-check-write on `ConcurrentHashMap` is a TOCTOU race
- `borrowChips` mutates `Player.chips` during active game → overwritten by `syncRoomChips`
- These are addressed in a separate "per-room locking" design, NOT in this lifecycle design.

---

## 2. Extended Status Model

**Important:** Per §1.4, `Room.status` is only set in the constructor and never modified. This design does NOT maintain `Room.status` as a runtime field. Instead, room state is **derived** from two sources:

| Logical State | How Derived | Players can join? | Game can start? |
|---------------|-------------|-------------------|-----------------|
| WAITING | `GameSessionService.sessions` has no entry for this room | Yes (add as ACTIVE) | Yes (if ≥2 with chips) |
| PLAYING | `sessions.containsKey(roomId)` is `true` | Yes (add as QUEUED) | No |
| FINISHED | Game over broadcast sent, `sessions` entry removed | No (reject join) | No (tournament concluded) |
| DISSOLVED | Room removed from `RoomRegistry.rooms` | No (room not found) | No |

**`FINISHED` vs `WAITING` after game over:** When `bustEndsGame=false` and a hand ends, the sessions map is cleared (`endGame`) and the room returns to logical WAITING. FINISHED is only set when `broadcastGameOver` fires (all busted or `bustEndsGame` first bust). The actual `Room.status` field is **never written** — FINISHED is tracked by the absence of a session AND the presence of a `gameOver=true` flag in the frontend.

**`DISSOLVED`:** Expressed by physically removing the Room from `RoomRegistry.rooms`. There is no `Room.status = DISSOLVED` — the room is simply gone. Frontend detects this when `GET /api/rooms/{roomId}` returns 404.

---

## 3. New Concepts

### 3.1 Explicit Owner (`Player.owner`)
Add `private boolean owner` to Player. The first player is owner. On owner leave, ownership transfers automatically.

### 3.2 Player State (`Player.status`)
Add `enum PlayerStatus { ACTIVE, SPECTATING, QUEUED, LEFT, DISCONNECTED }` to Player:  
- **ACTIVE:** Has seat, has chips, plays normally in current/next hand  
- **SPECTATING:** Has chips=0, chose to watch. Stays in `room.players` with seatIndex=-1. No seat at hand start.  
- **QUEUED:** Joined during PLAYING. Stays in `room.players` with seatIndex=-1. Waiting for next hand to get a seat.  
- **DISCONNECTED:** WebSocket disconnected but player hasn't explicitly left. Seat preserved. Auto-fold on timeout. On reconnect → ACTIVE.  
- **LEFT:** Explicitly left the room. Removed from `room.players`. Can rejoin fresh.

**All states share the same `room.players` list.** No separate queue list. `roomToResponse()` includes `status` in the player JSON so the frontend can render differently based on status.

### 3.3 Server-Persistent Room Lifecycle
Rooms survive only within JVM process lifetime (same as current). No database.

Dissolution conditions (any one triggers):
1. **Owner left and no ACTIVE human remains** — last human = bot, room dissolves
2. **Owner left and ownership transferred to a new player** — room continues with new owner
3. **Manual dissolution** — owner clicks "解散房间" (new button)
4. **Inactivity timeout** — no game activity for 30 minutes → auto-dissolve

### 3.4 `joinRoom` Guard Enhancement

Add is-playing detection to `RoomService.joinRoom()`. Because `Room.status` is never updated (see §1.4), the guard uses `GameSessionService.sessions.containsKey(roomId)` — the authoritative source for "is a hand in progress":

```java
public Room joinRoom(String roomId, JoinRoomRequest req) {
    Room room = registry.findById(roomId);
    if (room == null) return null;

    boolean isPlaying = gameSessionService.hasActiveSession(roomId);

    if (isPlaying) {
        // Add as QUEUED — spectator view, no seat, join prompt after hand
        Player player = new Player(req.getPlayerId(), req.getNickname(),
                -1, room.getConfig().getInitialChips());
        player.setStatus(PlayerStatus.QUEUED);
        room.addPlayer(player);
        return room;
    }

    // WAITING: add as ACTIVE (current behavior)
    Player player = new Player(req.getPlayerId(), req.getNickname(),
            room.getPlayers().size(), room.getConfig().getInitialChips());
    player.setStatus(PlayerStatus.ACTIVE);
    if (!room.addPlayer(player)) return null;
    return room;
}
```

| Room state (by sessions map) | Join behavior |
|------------------------------|---------------|
| No active session (WAITING) | Add as ACTIVE (current behavior) |
| Active session (PLAYING) | Add as QUEUED (spectator view, no seat) |
| Room dissolved | `findById` returns null → 404 |

**Why not use `Room.status` for this check?** Because `Room.status` is only set in the constructor and never transitions to PLAYING/FINISHED. Maintaining it in sync with `GameSessionService.sessions` would require updates in two controllers across 5+ code paths, creating a synchronization burden with zero benefit over the simpler `sessions.containsKey()` check.

---

## 4. Detailed Interaction Flows

### 4.1 Player Leave (Explicit) vs Disconnect (Implicit)

**Two distinct scenarios with different behaviors:**

#### A. Explicit Leave (user clicks "退出" or STOMP leave message)

```
Frontend:
  1. Send STOMP message: /app/room/{roomId}/leave  { playerId }
  2. disconnect()
  3. roomStore.reset()
  4. router.push('/')

Backend (GameMessageController @MessageMapping):
  1. Remove player from room.players
  2. Free the seatIndex (mark as available for future joins)
  3. If player was owner → transfer ownership to next human ACTIVE player (preferred) or QUEUED player (fallback)
       - New owner can startGame only when room is logically WAITING.
       - If new owner is QUEUED (no seat yet), the "开始游戏" button is disabled until they're seated.
  4. If owner left and NO human player remains (only bots) →
       broadcast "room_dissolved" to /topic/room/{roomId}
       registry.removeRoom(roomId)
       return
  5. If game was PLAYING → auto-fold the leaving player.
       - ⚠️ Requires per-room lock (see §9 bug C2/C3). Without locking, this operation can race with concurrent actions.
       - Alternative (if locking not yet implemented): mark player as LEFT but defer auto-fold to the existing timeout mechanism.
  6. Broadcast `{type: "player_left", playerId}` to /topic/room/{roomId}
  7. Broadcast updated room state to /topic/room/{roomId}
```

#### B. Implicit Disconnect (WebSocket dropped, tab closed, network loss)

```
Backend (@EventListener SessionDisconnectEvent):
  1. Look up player by session ID → get playerId + roomId
  2. Set Player.status = DISCONNECTED, Player.connected = false
  3. If it was this player's turn → auto-fold (requires per-room lock)
  4. Broadcast `{type: "player_disconnected", playerId}` to /topic/room/{roomId}
  5. Start a 60s grace timer:
     - If player reconnects before 60s → status back to ACTIVE (if still WAITING/their seat)
     - If 60s expires → remove from room.players, free seat (same as explicit leave)
```

**Owner transfer on explicit leave only** — if owner disconnects, ownership is NOT transferred until the grace period expires or the 60s timer fires.

### 4.2 Player Join During Different Logical States

```
New player calls POST /api/rooms/{roomId}/join

Backend (RoomController / RoomService):
  case WAITING (no session in GameSessionService.sessions):
    1. Set PlayerStatus.ACTIVE
    2. Assign next available seatIndex
    3. Give initialChips chips
    4. Broadcast room update
    5. Player sees waiting room view

  case PLAYING (session exists in GameSessionService.sessions):
    1. Set PlayerStatus.QUEUED
    2. Added to room.players with seatIndex=-1
    3. Subscribe to game channels (public only — no seat, no hole cards)
    4. Player sees waiting overlay: "等待对局结束..." + pot/phase info
    5. On hand complete (syncRoomChips runs):
       - If room returns to WAITING:
           a. Prompt QUEUED players: "是否加入下一局?" (10s auto-accept)
           b. On accept → assign seat, give chips, set ACTIVE
           c. Broadcast updated room

  case FINISHED (game over broadcast sent, no session):
    Reject — room exists but tournament concluded.

  case DISSOLVED (room not in registry):
    404 — room doesn't exist.
```

### 4.3 Mid-Game Join Seating (After PLAYING → WAITING Transition)

```
Hand completes → checkGameOver returns false → endGame clears session → room is logically WAITING

Backend:
  1. After syncRoomChips:
  2. Iterate room.players (QUEUED players already in the list with status=QUEUED, seatIndex=-1):
     a. If seats available (active players < maxSeats) → send "queue_prompt" to /topic/player/{id}/game
     b. Auto-accept after 10s if no response
  3. On accept:
     a. Set PlayerStatus.ACTIVE
     b. Assign seat (fill empty slots first, then append)
     c. Set chips = initialChips
     d. Broadcast updated room state to /topic/room/{roomId}

Frontend (RoomView.vue):
  Subscribe handler:
    if data.type === 'queue_prompt':
       show queue prompt modal: "对局结束，是否加入下一局？" [加入] [继续观战]
       timeout 10s → auto-join

Seat assignment: seats 0..maxSeats-1. Maintain a boolean[maxSeats] occupied[] from room.players
where player.status == ACTIVE. Fill first available slot. If all full, player remains QUEUED
and will be prompted again after the next hand.
```

### 4.4 Spectating a Live Game (QUEUED during PLAYING)

```
Frontend:
  - QUEUED players see a waiting overlay on top of the room view:
    "等待对局结束..." + 当前 pot 和 phase 简略信息
  - They do NOT see the PokerTable (no seat, no hole cards)
  - They subscribe to /topic/room/{roomId}/game (public channel) for minimal game progress
  - They do NOT have a private channel subscription (no hole cards)
  - Can click "🏆 排行" to see leaderboard

Backend:
  - GameStateSnapshot.buildPublic is broadcast to /topic/room/{roomId}/game
  - QUEUED players receive this and can extract phase/progress info
  - No private per-player snapshot is sent (playerId is not in GameState.players list)
```

### 4.5 Room Dissolution by Inactivity

```
New scheduled task (ScheduledExecutorService):
  Every 5 minutes:
    1. Scan all rooms in registry
    2. For each room:
       if lastActivity + 30min < now() → dissolve room
    3. lastActivity is initialized to createdAt, then updated on:
       game start, game action, player join, player leave, player disconnect, borrow

On dissolution:
  1. broadcast "room_dissolved" to /topic/room/{roomId}
  2. registry.removeRoom(roomId)
  3. gameSession.endGame(roomId) if game was active
```

---

## 5. New Backend Components

### 5.1 New Room Fields
```java
// Room.java additions:
private long lastActivity;     // System.currentTimeMillis() on any interaction; initialized to createdAt
private Player owner;          // explicit owner reference (not index-based)
// handCount already exists — increment it in startGame if needed for tracking
// No separate queue list — QUEUED/SPECTATING/DISCONNECTED players are in room.players with PlayerStatus
```

### 5.2 New Player Fields
```java
// Player.java additions:
private boolean owner;           // true for room's owner (denormalized — same value as Room.owner.playerId == this.playerId)
private PlayerStatus status;     // ACTIVE | SPECTATING | QUEUED | LEFT | DISCONNECTED
```

**Double `owner` field explanation:** `Room.owner` holds a reference (O(1) lookup). `Player.owner` is a denormalized boolean (O(1) check in UI/loops). Both are set together on create/transfer; `Room.owner` is authoritative, `Player.owner` is a cache.

### 5.3 New Endpoints (STOMP only)

| Destination | Direction | Purpose |
|-------------|-----------|---------|
| `/app/room/{roomId}/leave` | C→S | Player explicitly leaves room |
| `/app/room/{roomId}/dissolve` | C→S | Owner dissolves room |

**Why STOMP only?** The `startGame` precedent shows the danger of dual REST+STOMP entry points (REST doesn't register disconnect handlers). All room lifecycle operations go through STOMP to guarantee consistent handling. `borrow` remains REST because it doesn't need real-time notification — the response+room broadcast is sufficient.

### 5.4 New Broadcast Message Types (S→C)

`/topic/room/{roomId}` now carries `type` field:

| type | When | Payload |
|------|------|---------|
| `room_dissolved` | Room removed from registry | `{type, roomId}` |
| `player_left` | Player explicitly left | `{type, playerId, newOwnerId?}` |
| `player_joined` | New player joined room | `{type, playerId, nickname, seatIndex}` |
| `player_disconnected` | WebSocket dropped but still in grace period | `{type, playerId}` |

### 5.5 New RoomService Methods
```java
void leaveRoom(String roomId, String playerId);
void dissolveRoom(String roomId, String requesterId);
void seatQueuedPlayers(String roomId);
```

---

## 6. Frontend Changes

### 6.1 RoomView.vue — Header
```
Current:  [← 退出]     [房间名 / ID]     [🏆 排行]
Extended: [← 退出]     [房间名 / ID]     [🏆 排行]
If owner: "← 退出" becomes two buttons: [← 离开] [解散]
```

### 6.2 RoomView.vue — Leave Handler
```js
async function handleLeave() {
  await send(`/app/room/${roomId}/leave`, { playerId: userStore.playerId })
  disconnect()
  roomStore.reset()
  router.push('/')
}
```
Current `handleLeave` just navigates without notifying backend. Replace with STOMP message.

### 6.3 RoomView.vue — Room Channel Message Handling (Updated)

```js
subscribe(`/topic/room/${roomId}`, (msg) => {
  const data = JSON.parse(msg.body)

  if (data.type === 'room_dissolved') {
    alert('房间已解散')
    disconnect()
    roomStore.reset()
    router.push('/')
    return
  }

  if (data.type === 'player_left') {
    roomStore.players = roomStore.players.filter(p => p.playerId !== data.playerId)
    if (data.newOwnerId && data.newOwnerId === userStore.playerId) {
      alert('你已成为新房主')
      refreshRoom()
    }
    return
  }

  if (data.type === 'player_joined') {
    refreshRoom() // full refresh to get updated player list
    return
  }

  if (data.type === 'player_disconnected') {
    // Visual indicator: show "n玩家已断开" toast or dim player in list
    const p = roomStore.players.find(p => p.playerId === data.playerId)
    if (p) p.connected = false
    return
  }

  // ... existing room state update logic
})
```

### 6.4 RoomView.vue — Queue Overlay (New)
```
<div v-if="myPlayer?.status === 'QUEUED'" class="queue-overlay">
  等待对局结束...
  <div class="game-progress">Pot: {{ potSize }} | Phase: {{ gamePhase }}</div>
</div>
<!-- QUEUED players do NOT see PokerTable — no seat, no hole cards -->
```

### 6.5 RoomView.vue — Queue Prompt Modal (New)
```
Hand complete → backend sends "queue_prompt" to QUEUED players:
  Modal: "是否加入下一局？" [加入] [观战]
  Timeout 10s → auto-join
```

### 6.6 New Store Fields
```ts
// room.ts additions:
const queueLength = ref(0)      // number of queued players
const playerStatus = ref('ACTIVE') // my status
```

---

## 7. Things NOT Changed in This Design

| Item | Reason |
|------|--------|
| `ConcurrentHashMap` for rooms | Enough for read-mostly. Write ordering not critical for room lifecycle. |
| `ArrayList` for players | Not fixing here. Separate locking design. |
| `players[0]` as owner detection | REPLACED by explicit `owner` field — is a change |
| borrow during game | Still prohibited in game view — borrow only in waiting room |
| FINISHED → WAITING transition | Already works via `handleBackToRoom()` |
| Server restart behavior | No persistence = rooms lost on restart. Explicitly accepted constraint. |

---

## 8. Error States

| Scenario | Behavior |
|----------|----------|
| Non-owner clicks "解散" | Button not shown. Backend rejects if somehow sent. |
| Player explicitly leaves then rejoins within 60s (same playerId) | Key-lookup fails — player was removed from `room.players`. Fresh join required (new seat). |
| Player disconnects then reconnects within 60s grace (same playerId) | `DISCONNECTED → ACTIVE` if WAITING; `DISCONNECTED → QUEUED` if PLAYING. Seat preserved. |
| All humans left, only bots remain | Room dissolves (bots don't count as humans) |
| Implicit disconnect during PLAYING (not leave) | Auto-fold on timeout + keep in room 60s grace. If reconnect before 60s → continue from QUEUED or ACTIVE. |
| Room dissolved while spectating | `room_dissolved` message → toast "房间已解散" → redirect to / |

---

## 9. Known Bugs from Code Review (2026-06-20)

These were discovered during a thorough review of all recently modified files. They are NOT part of this lifecycle design — they are pre-existing issues.

### Bug Fix Sequencing for This Lifecycle

Bugs that MUST be fixed before or alongside the lifecycle implementation (blockers):

| Bug | Problem | Why Blocker |
|-----|---------|-------------|
| C2 (TOCTOU) | `applyAction` read-check-write not atomic | Leave auto-fold and mid-game join both depend on atomic state operations |
| C3 (autoPlayBots race) | Bot auto-play races with human actions via C2 | Leave auto-fold and bot auto-play compete for same lock |
| M2 (unsynchronized ArrayList) | `room.players` is `ArrayList` read across threads | Every lifecycle operation reads/writes `room.players` — any race here corrupts player lists |

Bugs STRONGLY RECOMMENDED before lifecycle (can work around, but risky):

| Bug | Problem | Why Recommended |
|-----|---------|-----------------|
| C4 (stale allIn) | Showdown winners keep `allIn=true` | QUEUED→ACTIVE seating depends on correct player state; stale flags could block hand start |
| H1 (BB phantom) | `currentBet` over-calculated when BB is short-stacked | Causes betting-phase anomalies that lifecycle's auto-fold/disconnect flows could encounter |

Bugs that can be fixed AFTER lifecycle (independent concerns):

C1, H2, H3, H4, H5, M1, M3–M14 — These affect gameplay logic, frontend UI, or performance in ways that don't interact with the lifecycle's join/leave/disconnect flows.

### CRITICAL (4 issues)

| # | File:Line | Description |
|---|-----------|-------------|
| C1 | `GameEngine.java:32-34` | **Cap-before-validate breaks RAISE.** The chip cap applied before `ActionValidator.validate()` can reduce a RAISE below minimum, but the validator silently passes it as an all-in raise. `BettingRoundManager` then sets `currentBet` to the capped value which may be **lower** than before, breaking betting invariants and causing hands to freeze. Fix: validate original amount first, cap only for chip deduction. |
| C2 | `GameSessionService.java:38-52` | **`applyAction` read-check-write is not atomic.** Two threads can both read the same GameState, both validate, both compute new states, and the later `put()` silently overwrites the earlier one. |
| C3 | `GameMessageController.java:68-86` + `RoomController.java:131-153` | **`autoPlayBots` races with human `processAction`.** C2's race condition manifests concretely: bot auto-play and human WebSocket messages can interleave on the same room's state. |
| C4 | `GameEngine.java:133-143` | **Showdown winners retain stale `allIn=true`.** When an all-in player wins chips at showdown, their `chips` increase but `allIn` stays `true`. The next hand treats them as inactive. |

### HIGH (5 issues)

| # | File:Line | Description |
|---|-----------|-------------|
| H1 | `PhaseTransition.java:38-49` | **Initial `currentBet` uses full BB even when BB posts partial all-in.** If BB has < BB amount chips, `currentBet` is set to full BB (e.g. 20) but BB only contributed e.g. 15. Other players must call 20, creating a 5-chip phantom. |
| H2 | `ActionValidator.java:23-25` | **`legalActions` excludes all-in BET under minRaise.** Frontend hides BET button, but backend allows it. |
| H3 | `RoomView.vue:309-313` | **Auto-fold watch has no debounce guard.** If FOLD fails, the watch fires again, sending duplicate FOLD messages. No flag prevents re-entry. |
| H4 | `RoomView.vue:339-340` | **Client leaderboard hardcodes borrow unit as 1000.** Server uses `room.getConfig().getInitialChips()`. If a room uses different initial chips, client and server disagree on netChips. |
| H5 | `GameEngine.java:88-159` | **Showdown debug logging is unbuffered.** 15+ `System.err.println` + `flush()` calls fire on EVERY showdown. Should use conditional SLF4J. |

### MEDIUM (14 issues — summary, full table in review transcript)

| # | Summary |
|---|---------|
| M1 | `BettingRoundManager.isRoundComplete` returns true when no active players — functionally correct but undocumented |
| M2 | `Room.players` is unsynchronized `ArrayList` accessed from WebSocket, HTTP, and disconnect threads |
| M3 | `Player.borrow()` increments `borrowCount` by 1 regardless of custom borrow amount |
| M4 | `RoomController.startGame` blocks HTTP response on `autoBots` loop (up to 30 turns) |
| M5 | Game subscription handler can clear `winners` prematurely between winner broadcast and next-hand button press |
| M6 | `ActionValidator.validate()` and `legalActions()` have divergent logic — should share single truth source |
| M7 | `PhaseTransition.firstActiveAfter` fallback can return inactive player |
| M8 | `BettingRoundManager.advanceToNextActive` returns unchanged state when stuck, caller must safeguard |
| M9 | `checkGameOver` called after `endGame` — session already removed, relies on UI to restart |
| M10 | Frontend forces `allIn=false` for chips≤0, conflating "busted between hands" with "all-in during hand" |
| M11 | Sole survivor pot calculation streams players list 3 times |
| M12 | Auto-slider resets betAmount on any canBet/canRaise change, can show value exceeding chips |
| M13 | `autoPlayBots`, `checkGameOver`, `broadcastGameOver`, `broadcastBustChoice` are copy-pasted between `GameMessageController` and `RoomController` |
| M14 | `user.ts:21` — module-level `localStorage.setItem` side effect in store definition |

### LOW (8 issues — summary)

| # | Summary |
|---|---------|
| L1 | `Player.placeBet()` appears to be dead code — engine uses `GamePlayerState.withChipsDeducted()` |
| L2 | `timeLeftSec \|\| 30` fallback never activates — server hardcodes 30 |
| L3 | `bettingRound` ref typed as `string` instead of union type |
| L4 | `(snapshot as any).minRaise` — field missing from `RoomSnapshot` interface |
| L5 | `GamePhase.HAND_OVER` exists in enum but is never returned by any method |
| L6 | `PhaseTransition.advancePhase` default case returns unchanged state — should throw defensively |
| L7 | `BroadcastService.sendToRoom` single-arg overload sends to wrong destination for game data |
| L8 | `RoomConfig.setSmallBlind()` auto-sets bigBlind to 2x, no reciprocal validation on direct bigBlind set |

---

## Self-Review Checklist (after writing)

- [x] No placeholder/TODO in body
- [x] Status transitions consistent with current code (`joinRoom` confirmed no status check; `handleLeave` confirmed only navigates)
- [x] Frontend changes map to actual Vue components (RoomView.vue header, leave handler, subscriptions)
- [x] STOMP topics match existing `/app/room/` and `/topic/room/` prefix convention
- [x] Concurrency not addressed = explicitly deferred in Section 7
- [x] Borrow during game vs waiting room: spec is consistent with current UI (borrow button in waiting room only, game borrow removed in prior commit)
