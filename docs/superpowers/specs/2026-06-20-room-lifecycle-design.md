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

### 1.4 Room Lifecycle (Current)
```
User POST /api/rooms         → createRoom → Room.status = WAITING
                                 ↓
Other user POST /join         → joinRoom → adds Player to ArrayList
                                 ↓
Owner POST /start or STOMP    → startGame → WAITING → PLAYING
                                 ↓ Game plays...
Hand ends                     → checkGameOver
  ↓ (not bustEndsGame)          ↓ (bustEndsGame or all busted)
  WAITING (new hand possible)   FINISHED (setGameOver broadcast)
```
**Key gap:** There is no "leave" flow. Player who closes tab just disconnects silently. `handleDisconnect` callback exists but is never wired to WebSocket disconnect events.

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

Current: `WAITING | PLAYING | FINISHED`  
Extended: `WAITING | PLAYING | FINISHED | DISSOLVED`

| Status | Meaning | Players can join? | Game can start? |
|--------|---------|-------------------|-----------------|
| WAITING | Room open, waiting for players | Yes | Yes (if ≥2 with chips) |
| PLAYING | Hand in progress | Yes (as spectator/queue) | No |
| FINISHED | Tournament completed (bustEndsGame) | No | No |
| DISSOLVED | Room destroyed | No | No |

**Note on FINISHED vs WAITING after game over:** When `bustEndsGame=false` and a hand ends, the room transitions to WAITING — NOT FINISHED. FINISHED is only set when `setGameOver` fires (all busted or bustEndsGame first bust).

---

## 3. New Concepts

### 3.1 Explicit Owner (`Player.owner`)
Add `private boolean owner` to Player. The first player is owner. On owner leave, ownership transfers automatically.

### 3.2 Player State (`Player.status`)
Add `enum PlayerStatus { ACTIVE, SPECTATING, QUEUED, LEFT }` to Player:  
- **ACTIVE:** Has seat, chips, plays normally  
- **SPECTATING:** Has chips=0 or chose to spectate, stays in room, no seat assignment at hand start  
- **QUEUED:** Joined during PLAYING, waiting for next hand to be seated  
- **LEFT:** Explicitly left the room (disconnected), seat is freed. Reconnecting switches back to ACTIVE if seat still available.

### 3.3 Server-Persistent Room Lifecycle
Rooms survive only within JVM process lifetime (same as current). No database.

Dissolution conditions (any one triggers):
1. **Owner left and no ACTIVE human remains** — last human = bot, room dissolves
2. **Owner left and ownership transferred to a new player** — room continues with new owner
3. **Manual dissolution** — owner clicks "解散房间" (new button)
4. **Inactivity timeout** — no game activity for 30 minutes → auto-dissolve

### 3.4 `joinRoom` Guard Enhancement
Add status check to `RoomService.joinRoom()`:
```
if PLAYING/DISSOLVED → add as QUEUED (spectator view, no seat)
if FINISHED           → reject ("match concluded")
if WAITING            → add as ACTIVE (current behavior)
```

---

## 4. Detailed Interaction Flows

### 4.1 Player Leave

```
User clicks "退出" (or closes tab → WebSocket disconnect)

Frontend:
  1. Send STOMP message: /app/room/{roomId}/leave  { playerId }
  2. Router.push('/')

Backend (GameMessageController @MessageMapping):
  1. Remove player from room.players
  2. Free the seatIndex (for future joins)
  3. If player was owner → transfer ownership to next human ACTIVE/QUEUED player
  4. If owner left and NO human ACTIVE/QUEUED remains →
       broadcast "room_dissolved" to /topic/room/{roomId}
       registry.removeRoom(roomId)
       return
  5. If game was PLAYING → auto-fold the leaving player in current hand
  6. Broadcast updated room state to /topic/room/{roomId}
```

### 4.2 Player Join During Different States

```
New player calls POST /api/rooms/{roomId}/join

Backend (RoomController):
  case WAITING:
    1. Set PlayerStatus.ACTIVE
    2. Assign next available seatIndex
    3. Give initialChips chips
    4. Broadcast room update
    5. Player sees waiting room view

  case PLAYING:
    1. Set PlayerStatus.QUEUED
    2. No seat, no chips yet
    3. Subscribe to game channels
    4. Player sees full table view + "排队中 — 对局结束后加入" overlay
    5. On hand complete (syncRoomChips runs):
       - If room returns to WAITING:
           a. Prompt QUEUED players: "是否加入下一局?" (5s auto-accept)
           b. On accept → assign seat, give chips, set ACTIVE
           c. Broadcast updated room

  case FINISHED:
    → 403 "Match concluded"

  case DISSOLVED:
    → 404 "Room not found"
```

### 4.3 Mid-Game Join Seating (After PLAYING → WAITING Transition)

```
Hand completes → checkGameOver returns false → room ready for next hand

Backend:
  1. After syncRoomChips:
  2. For each QUEUED player:
     a. If seats available (not full) → send "queue_prompt" to /topic/player/{id}/game
     b. Auto-accept after 10s if no response
  3. On accept:
     a. Set PlayerStatus.ACTIVE
     b. Assign seat (fill empty slots first, then append)
     c. Set chips = initialChips
     d. Broadcast updated room

Frontend (RoomView.vue):
  Subscribe handler:
    if data.type === 'queue_prompt':
       show queue prompt modal: "对局结束，是否加入下一局？" [加入] [继续观战]
       timeout 10s → auto-join
```

### 4.4 Spectating a Live Game (QUEUED during PLAYING)

```
Frontend:
  - QUEUED players see full PokerTable (same as ACTIVE)
  - ActionPanel replaced by: "排队中 — 本局结束后加入"
  - No hole cards shown (watches public channel only)
  - Can click "🏆 排行" to see leaderboard

Backend:
  - GameStateSnapshot.buildPublic already shows all players' chips/bets
  - QUEUED players subscribe to /topic/room/{roomId}/game (public)
  - Do NOT get private channel snapshots (no hole cards)
```

### 4.5 Room Dissolution by Inactivity

```
New scheduled task (ScheduledExecutorService):
  Every 5 minutes:
    1. Scan all rooms in registry
    2. For each room:
       a. If handCount == 0 AND createdAt + 30min < now()
          OR lastActivity (new field on Room) + 30min < now()
       → dissolve room
    3. "lastActivity" updated on: game start, game action, player join, borrow

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
private long lastActivity;     // System.currentTimeMillis() on any interaction
private int handCount;         // already exists
private List<Player> queue;    // QUEUED players (not in players list during game)
```

### 5.2 New Player Fields
```java
// Player.java additions:
private boolean owner;           // true for room's owner
private PlayerStatus status;     // ACTIVE | SPECTATING | QUEUED | LEFT
```

### 5.3 New REST Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/rooms/{roomId}/leave` | Player explicitly leaves room |
| POST | `/api/rooms/{roomId}/dissolve` | Owner dissolves room (new) |
| POST | `/api/rooms/{roomId}/borrow` | Already exists — no change |

### 5.4 New STOMP Destinations

| Destination | Direction | Purpose |
|-------------|-----------|---------|
| `/app/room/{roomId}/leave` | C→S | Player leaves room |
| `/app/room/{roomId}/dissolve` | C→S | Owner dissolves room |
| `/topic/room/{roomId}` | S→C | Now includes: `type: "room_dissolved"`, `player_left`, `player_joined` |

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

### 6.3 RoomView.vue — Queue Overlay (New)
```
<div v-if="myPlayer?.status === 'QUEUED'" class="queue-overlay">
  排队中 — 本局结束后加入
</div>
```

### 6.4 RoomView.vue — Queue Prompt Modal (New)
```
Hand complete → backend sends "queue_prompt" to QUEUED players:
  Modal: "是否加入下一局？" [加入] [观战]
  Timeout 10s → auto-join
```

### 6.5 New Store Fields
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
| Player leaves then reconnects within 60s (same playerId) | `PlayerStatus.LEFT → ACTIVE` (if seat exists and WAITING). If PLAYING → QUEUED. |
| All humans left, only bots remain | Room dissolves (bots don't count as humans) |
| Disconnect during PLAYING (not leave) | Auto-fold on timeout + keep in room. If reconnect before hand ends → continue. |
| Room dissolved while spectating | Browser shows "房间已解散" toast → redirect to / |

---

## Self-Review Checklist (after writing)

- [x] No placeholder/TODO in body
- [x] Status transitions consistent with current code (`joinRoom` confirmed no status check; `handleLeave` confirmed only navigates)
- [x] Frontend changes map to actual Vue components (RoomView.vue header, leave handler, subscriptions)
- [x] STOMP topics match existing `/app/room/` and `/topic/room/` prefix convention
- [x] Concurrency not addressed = explicitly deferred in Section 7
- [x] Borrow during game vs waiting room: spec is consistent with current UI (borrow button in waiting room only, game borrow removed in prior commit)
