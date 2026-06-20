# Mode: frontend-state

## When to Use

- Modified: `RoomView.vue`, `room.ts` (store), `useWebSocket.ts`, `ActionPanel.vue`, `GameOver.vue`, `HandResult.vue`, `BustChoice.vue`, `user.ts`
- Changes in: Pinia store mutations, WebSocket message handlers, `watch` side effects, `computed` derivations, `localStorage` interactions
- User mentions: "前端", "Vue", "Pinia", "状态", "state", "store", "UI"

## Known Historical Bugs

- **C11**: `player_left` handler did local `filter()` without server re-fetch — local and server player lists diverged
- **M18**: `updateFromSnapshot` overwrote server `connected` with stale local value — player showed as offline when connected
- **M4**: WebSocket reconnect + `onMounted refreshRoom` double-join race → 409 conflict
- **M1**: 0-chip auto-FOLD `watch` triggered same FOLD as server-side `autoPlayBots` — double submission
- **M3**: Game-over overlay rendered on top of active table — UI state stuck
- **M7**: `localStorage` user data lost on `shift+refresh` — race between `onMounted` and store hydration

## Deep Review Task

You are a Vue 3 + Pinia frontend reviewer with **full read access**. Read actual source files — don't just scan the diff.

### What to do

1. **Read every modified `.vue`, `.ts` store, and composable** in full.
2. **State consistency**: For every WebSocket message handler that mutates store state:
   - Does it also re-fetch from server (`refreshRoom`)? Or is it optimistic?
   - If optimistic, what's the rollback path on error?
   - Does it handle the case where the handler fires twice (reconnect replay)?
3. **Reactivity chains**: For every `watch`:
   - Could it form an infinite loop (watch A mutates B, watch B mutates A)?
   - Is the condition guard tight enough (e.g., `watch` on `player.chips` fires for ANY player, not just current)?
4. **Concurrent async paths**: Check for pairs like `onMounted` + WebSocket callback both calling `joinRoom` or `refreshRoom`.
5. **Lifecycle**: Are `watch` effects cleaned up with `watchEffect` return or `onUnmounted`? Could a stale watcher fire after the component unmounts?
6. **Use codegraph** to find all `store.$patch`, `store.$state`, and direct mutations — verify no direct state writes bypass the store's intended methods.

### Output Format

```
## Frontend State Review

### P0 Critical (data loss / inconsistent state)
- [file:line] Issue description + impact

### P1 Warning (race / duplicate call)
- [file:line] Issue description

### P2 Suggestions
- [file:line] Performance/readability improvement

### Overall
[One sentence about frontend state safety]
```
