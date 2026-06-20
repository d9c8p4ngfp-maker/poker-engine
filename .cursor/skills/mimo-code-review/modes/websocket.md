# Mode: websocket

## When to Use

- Modified: `useWebSocket.ts`, WebSocket config, STOMP/SockJS setup, disconnect/reconnect handlers, `WebSocketConfig.java`, `WebSocketEventListener.java`
- User mentions: "WebSocket", "重连", "断线", "SockJS", "STOMP", "reconnect", "session"

## Known Historical Bugs

- **M16**: Fixed 2s reconnect interval, no exponential backoff, no max attempts — infinite connection storms on server restart
- **M17**: Messages sent during disconnect silently dropped — no outbound queue, no retry
- **N9/N10**: No `onWebSocketClose`/`onWebSocketError` handlers — silent failures
- **N3/N4**: JoiningLock not released on WebSocket error — permanent lockout from room
- **MED-3**: `sessionToPlayer` mapping unreliable — disconnect event arrived for wrong player

## Deep Review Task

You are a WebSocket connectivity reviewer with **full read access**. Read actual source files.

### What to do

1. **Read the full WebSocket client code** (`useWebSocket.ts`) and server config.
2. **Reconnect behavior**: Trace the full reconnect path:
   - Is there exponential backoff (start 1s, double each attempt)?
   - Is there a max retry limit after which the user is informed?
   - Does reconnection re-join the room? (It should.)
   - Is there a guard to prevent duplicate joins?
3. **Message reliability**: 
   - What happens to outbound messages while disconnected? Queued? Dropped?
   - Does the server-side `SessionDisconnectEvent` handler clean up correctly?
   - Does the grace-period timer interact correctly with reconnect?
4. **Session mapping**: Read `sessionToPlayer` usage — is it `ConcurrentHashMap`? Cleaned up on disconnect?
5. **Use codegraph** to find all `@EventListener(SessionDisconnectEvent)` handlers and verify they don't deadlock with room locks.

### Output Format

```
## WebSocket Review

### P0 Critical (connection storm / data loss)
- [file:line] Issue description + impact

### P1 Warning (degraded UX)
- [file:line] Issue description

### P2 Suggestions
- [file:line] Improvement

### Overall
[One sentence about WebSocket robustness]
```
