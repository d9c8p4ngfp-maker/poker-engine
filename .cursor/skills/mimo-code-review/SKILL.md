---
name: mimo-code-review
description: Review staged/unstaged git diffs with MiMo AI using scenario-aware prompts. Use after completing a batch of changes in a development session, before committing or creating a PR, or when the user asks for a code review. Triggers on keywords like "review", "审查", "code review", "mimo".
disable-model-invocation: false
---

# MiMo Code Review — Scenario-Aware

## Workflow

**Always follow this two-step process:**

### Step 1: Determine Review Mode

Analyze which files changed and pick one or more review modes from the table below. If changes span multiple categories, run the **highest-risk mode** (sorted by severity).

| Mode | When to Use | Triggers (files changed) |
|------|-------------|--------------------------|
| **concurrency** | ReentrantLock, executeWithLock, thread pools, synchronized blocks, volatile fields, ScheduledExecutorService | `GameSessionService`, `GameBroadcastHelper`, `GameDisconnectHandler`, `GameTimeoutScheduler`, `RoomDissolutionScheduler` |
| **game-logic** | betting rounds, chip sync, dealer rotation, hand resolution, side pots, all-in | `GameEngine`, `HandResolver`, `BettingRoundManager`, `SidePotCalculator`, `ActionValidator`, `syncRoomChips` |
| **security** | password, input validation, auth checks, DTO annotations, room access control | `GameActionRequest`, `JoinRoomRequest`, `CreateRoomRequest`, `RoomService.joinRoom`, `@Valid`/`@NotBlank` |
| **frontend-state** | Vue reactivity, Pinia store merges, optimistic delete, WebSocket message handling, countdown | `RoomView.vue`, `room.ts`, `useWebSocket.ts`, `ActionPanel.vue`, `GameOver.vue` |
| **websocket** | reconnect backoff, message queue, SockJS fallback, disconnect/grace handling | `useWebSocket.ts`, WebSocket config, disconnect handlers |
| **resource** | thread leaks, ScheduledExecutorService lifecycle, @PreDestroy, executor shutdown, room cleanup | `GameDisconnectHandler`, `RoomDissolutionScheduler`, executor/shutdown usage |
| **general** | Mixed changes or no clear category match | Any |

### Step 2: Construct Mode-Specific Prompt

Use the mode's review prompt (below), NOT the generic one. Replace `{diff}` with the actual git diff.

---

## Mode Prompts

### concurrency

```
You are a senior concurrency reviewer for a Spring Boot + ReentrantLock game server.

This project uses a per-room ReentrantLock model: GameSessionService.roomLocks protects GameState and Room mutations.
All state changes must happen inside executeWithLock. The lock is released before broadcasts, bot advances, or timeout scheduling — creating TOCTOU race windows that have caused multiple P0 bugs.

Review this diff and check:
1. Is every read/write of GameState and Room inside executeWithLock?
2. Are broadcasts, autoPlayBots, scheduleNextTimeout called AFTER the lock is released (not inside it, to avoid deadlock with re-entrant event listeners)?
3. Is there a TOCTOU window between a lock-guarded read and a later unlock-dependent action (e.g., reading state inside lock, then scheduling timeout outside)?
4. If this is a disconnect/event handler, does it merge Phase 1 (lock) and Phase 2 (broadcast/timeout) correctly without using stale state?
5. Are there nested lock acquisitions that could deadlock (e.g., holding lock A while Spring dispatches disconnect event that tries to acquire lock A again)?
6. For ScheduledExecutorService usage: is it a shared pool (not per-event SingleThreadExecutor)? Is there a @PreDestroy shutdown?

Output Format:
- P0 Critical (race/deadlock/TOCTOU): [must fix]
- P1 Warning (lock scope, ordering): [should address]
- P2 Suggestions (naming, structure): [optional]
- Overall: [one sentence about concurrency safety]

Diff:
{diff}
```

### game-logic

```
You are a poker engine reviewer. This is a Texas Hold'em server.

Known historical bugs in this project:
- dealerIndex never incremented (C1) — blinds never rotate
- syncRoomChips overwrites borrow-amount (M12) — in-hand loans erased
- Bots added mid-game get ACTIVE status but aren't in GameState (M11)

Review this diff and check:
1. Does dealerIndex increment after each hand? Does it wrap correctly modulo active player count?
2. Does syncRoomChips preserve chips that were added mid-hand (borrows)? Use max or cumulative, not overwrite.
3. Are new players/bots added mid-game correctly placed in QUEUED (not ACTIVE)?
4. Chip math: side pots, all-in calculations, blind posting — any off-by-one or incorrect rounding?
5. Hand complete sequence: cancelTimeout → endGame → syncRoomChips → checkGameOver → broadcastWinners — are all steps in the right order?
6. Does betting round advancement handle edge cases (only 1 player left, everyone all-in)?

Output Format:
- P0 Critical (incorrect game rule): [must fix]
- P1 Warning (edge case missing): [should address]
- P2 Suggestions (code clarity): [optional]
- Overall: [one sentence about game logic correctness]

Diff:
{diff}
```

### security

```
You are a security reviewer for a multiplayer game server.

Known historical bugs:
- Password field existed but was never checked (C13)
- GameActionRequest had no @NotBlank → null playerId caused NPE/500 (C12)
- borrowChips didn't verify playerId was in room (M8)
- queueAccept unconditionally reset chips (M14)

Review this diff and check:
1. Are all DTO fields with @NotBlank/@NotNull validated? Is @Valid/@Validated on the controller?
2. If a password/auth field was added, is it actually checked on join? Is it excluded from API responses (e.g., @JsonIgnore)?
3. Does every endpoint verify the caller is authorized (owner check, player-in-room check)?
4. Are there any fields that could be exploited (e.g., resetting chips, bypassing seat limits)?
5. Input validation: can negative values, zero, or excessively large values cause logic errors?
6. Are secrets (API keys, passwords) ever logged or serialized to responses?

Output Format:
- P0 Critical (auth bypass / data leak): [must fix]
- P1 Warning (missing validation): [should address]
- P2 Suggestions (defense-in-depth): [optional]
- Overall: [one sentence about security posture]

Diff:
{diff}
```

### frontend-state

```
You are a Vue 3 + Pinia frontend reviewer for a real-time poker client over WebSocket (STOMP/SockJS).

Known historical bugs:
- player_left message handler did optimistic local filter() without server confirmation (C11)
- updateFromSnapshot overwrote server-connected with stale local value (M18)
- WebSocket reconnect triggered double-join race with onMounted refreshRoom (M4)
- 0-chip auto-FOLD watch duplicated server-side autoPlayBots behavior (M1)

Review this diff and check:
1. Does any WebSocket message handler mutate local state without re-fetching from the server? Are optimistic mutations safe?
2. Does updateFromSnapshot merge (not replace) player data? Does the server's connected field survive?
3. Are there multiple async paths (watch, onMounted, WebSocket callback) that could trigger the same REST call concurrently?
4. Does the countdown/auto-action logic only run when it's actually the current player's turn AND the room is PLAYING?
5. Is the game-over state managed correctly (overlay visible, table still rendering behind it)?
6. Are there any computed properties or watchers that could trigger infinite re-render loops?

Output Format:
- P0 Critical (data loss / inconsistent state): [must fix]
- P1 Warning (race condition / duplicate call): [should address]
- P2 Suggestions (UX / performance): [optional]
- Overall: [one sentence about frontend state safety]

Diff:
{diff}
```

### websocket

```
You are a WebSocket connectivity reviewer for a SockJS + STOMP client.

Known historical bugs:
- Fixed 2s reconnect interval, no backoff, no max attempts (M16) — infinite reconnect storms
- Messages sent during disconnect silently dropped, no queue/retry (M17)
- No onWebSocketClose/onWebSocketError handlers (N9/N10)

Review this diff and check:
1. Reconnect logic: exponential backoff? Max retry limit? Does it stop trying after N failures?
2. Message reliability: are outbound messages queued during disconnect and flushed on reconnect?
3. Error handling: do close/error events have handlers? Is the user notified (not just console.error)?
4. Does the reconnection re-fetch room state from the server (refreshRoom) to recover missed state?
5. Is there a reconnection guard (joiningLock) to prevent duplicate join calls?

Output Format:
- P0 Critical (connection storm / data loss): [must fix]
- P1 Warning (degraded UX during disconnect): [should address]
- P2 Suggestions (resilience): [optional]
- Overall: [one sentence about WebSocket robustness]

Diff:
{diff}
```

### resource

```
You are a resource management reviewer for a Spring Boot server.

Known historical bugs:
- Every disconnect created a new SingleThreadScheduledExecutor, never shutdown (C2) — permanent thread leak
- handleLeave didn't cancel pending grace timer (M2) — timer fires on nonexistent player

Review this diff and check:
1. Any `new ScheduledExecutorService()` or `Executors.new*` — is it shared or per-event? If per-event, is it shutdown?
2. Does every executor/thread pool have a @PreDestroy shutdown method?
3. Are ScheduledFuture callbacks cancelled when their target is removed (player leave, room dissolve)?
4. Are there any collections (ConcurrentHashMap) that grow without bound? Is there cleanup on room dissolve/player leave?
5. Room locks: does endGameAndCleanupLock remove the lock from roomLocks BEFORE unlock() to avoid use-after-free?

Output Format:
- P0 Critical (thread/resource leak): [must fix]
- P1 Warning (missing cleanup): [should address]
- P2 Suggestions (monitoring): [optional]
- Overall: [one sentence about resource safety]

Diff:
{diff}
```

---

## Script Reference

The review is executed by `.cursor/hooks/mimo-review.ps1` which:

1. Collects `git diff` + untracked text files
2. Sends to MiMo API (`mimo-v2.5-pro`) with the mode-specific prompt
3. Saves to `.cursor/reviews/review-{timestamp}.md`

To use a specific mode, construct the prompt as shown above and call:

```powershell
# The hook defaults to the generic prompt. For mode-specific review,
# construct the prompt in-code and call Invoke-RestMethod directly.
```

## Interpreting Results

All modes use the same triage:
- **P0 Critical** → Must fix before merge (race conditions, rule errors, auth bypass, data leaks, thread leaks)
- **P1 Warning** → Should address (missing tests, edge cases, code smell)
- **P2 Suggestions** → Optional (naming, logging, readability)
