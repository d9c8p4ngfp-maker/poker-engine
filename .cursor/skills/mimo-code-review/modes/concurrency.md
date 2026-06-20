# Mode: concurrency

## When to Use

- Modified any of: `GameSessionService`, `GameBroadcastHelper`, `GameDisconnectHandler`, `GameTimeoutScheduler`, `RoomDissolutionScheduler`, `RoomService`
- Changes involve: `ReentrantLock`, `executeWithLock`, `ScheduledExecutorService`, `ConcurrentHashMap`, `volatile`, `synchronized`, `@EventListener`, `SessionDisconnectEvent`
- User mentions: "锁", "并发", "竞态", "TOCTOU", "线程安全"

## Known Historical Bugs

- **C2**: Every disconnect created `newSingleThreadScheduledExecutor()`, never `shutdown()` — permanent thread leak
- **C3/C4/C5**: `processAction`/`handleTimeout` mutated state inside lock, then broadcast/bot-advance/timeout-schedule outside lock — TOCTOU window between lock release and external action
- **C6**: Disconnect handler Phase 1 (locked) captured state, Phase 2 (unlocked) used stale captured state
- **C7**: `addBots` had no lock — bots added mid-game bypassed engine
- **C8**: `startGame` had `findRoom` (no lock) → `lock.lock()` — TOCTOU between lookup and acquisition
- **C9**: `joinRoom` had no lock — raced with concurrent `startGame`
- **N7**: `broadcastGameState` sent raw GameState (not snapshot) — stale reads by broadcast thread

## Deep Review Task

You are a senior concurrency reviewer with **full read access to this Spring Boot + ReentrantLock game server**. Do NOT just look at the diff — read the actual source files of every modified class and trace caller/callee relationships.

### What to do

1. **Read every modified file** in full. Don't skip any.
2. **Trace lock scopes**: For every `executeWithLock`, `lock.lock()`, or `lock.tryLock()` call, trace:
   - What state is read/written inside the lock?
   - What happens after the lock is released (broadcast? bot advance? timeout schedule?)?
   - Is there a TOCTOU gap between the locked read and the unlocked action?
3. **Check event handlers**: For `@EventListener(SessionDisconnectEvent)`, verify:
   - Does the handler acquire a room lock? (It should.)
   - Does it release the lock before spawning external work? (It must, to avoid re-entrant deadlock.)
4. **Check thread pools**: For every `ScheduledExecutorService` or `ExecutorService`:
   - Is it a shared pool or per-event? (Must be shared.)
   - Is there a `@PreDestroy` shutdown method?
   - Are `ScheduledFuture` callbacks properly cancelled when targets are removed?
5. **Use codegraph** to trace `executeWithLock` → find all call sites. Verify each one follows the lock/broadcast split pattern.

### Output Format

```
## Concurrency Review

### P0 Critical (race/deadlock/TOCTOU)
- [file:line] Issue description + impact

### P1 Warning (lock scope, ordering)
- [file:line] Issue description

### P2 Suggestions
- [file:line] Cleanup/naming improvement

### Overall
[One sentence about concurrency safety of this changeset]
```
