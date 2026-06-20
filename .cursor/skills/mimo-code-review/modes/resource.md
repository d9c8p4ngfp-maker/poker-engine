# Mode: resource

## When to Use

- Modified: `GameDisconnectHandler`, `RoomDissolutionScheduler`, any `ExecutorService`/`ScheduledExecutorService` usage
- Changes adding: `@PreDestroy`, `shutdown()`, `ScheduledFuture`, timer/countdown logic
- User mentions: "线程泄漏", "资源", "shutdown", "cleanup", "内存泄漏", "timer"

## Known Historical Bugs

- **C2**: Per-disconnect `newSingleThreadScheduledExecutor()`, never shutdown — permanent thread leak compounding with every disconnect
- **M2**: `handleLeave` didn't cancel pending grace timer — `ScheduledFuture` fired on removed player, causing NPE
- **M6**: Room lock not removed from `roomLocks` map after room dissolved — `ConcurrentHashMap` memory leak
- **M9**: Countdown timer not cancelled when game ended via `bustEndsGame` — timer fired after game was over, starting a phantom hand

## Deep Review Task

You are a resource management reviewer with **full read access**. Read actual source files.

### What to do

1. **Read every file that creates threads, timers, or executors**.
2. **Thread pool audit**: For every `ExecutorService`/`ScheduledExecutorService`:
   - Is it a shared, named pool (not anonymous per-event creation)?
   - Is there a `@PreDestroy` method that calls `shutdown()` + `awaitTermination()`?
   - If `shutdownNow()` is used, are interrupted tasks handled safely?
3. **Timer lifecycle**: For every `ScheduledFuture`/timer:
   - Is it cancelled in the "entity removed" path (player leave, room dissolve, game end)?
   - Is the cancelled future removed from the tracking map?
   - Does cancellation happen BEFORE the target entity is removed?
4. **Map growth**: For every `ConcurrentHashMap`:
   - Are entries removed when rooms/players are gone?
   - Is there a size limit or cleanup sweep?
5. **Use codegraph** to find all `shutdown`, `cancel`, `remove` calls — verify they exist for every corresponding creation/put.

### Output Format

```
## Resource Review

### P0 Critical (thread/resource leak)
- [file:line] Issue description + impact

### P1 Warning (missing cleanup)
- [file:line] Issue description

### P2 Suggestions
- [file:line] Improvement

### Overall
[One sentence about resource safety]
```
