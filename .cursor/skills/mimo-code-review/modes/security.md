# Mode: security

## When to Use

- Modified: DTOs (`GameActionRequest`, `JoinRoomRequest`, `CreateRoomRequest`), `RoomService.joinRoom`, `RoomController`, `GameMessageController`
- Changes adding: `@Valid`, `@NotBlank`, password/auth fields, `@JsonIgnore`, any authorization check
- User mentions: "安全", "校验", "验证", "密码", "auth", "DTO", "注入"

## Known Historical Bugs

- **C13**: `JoinRoomRequest.password` existed but `joinRoom` never checked it — dead code, false security
- **C12**: `GameActionRequest` no `@NotBlank` — null `playerId` → NPE → 500
- **M8**: `borrowChips` didn't verify `playerId` was in the room — any player could borrow from any room
- **M14**: `queueAccept` unconditionally reset chips — exploitable to reset one's own chip count
- **Password leak**: `Room.getPassword()` serialized to API responses — exposed passwords to any client

## Deep Review Task

You are a security reviewer with **full read access**. Read the actual source files, not just a diff.

### What to do

1. **Read every modified DTO and controller** in full.
2. **Validation chain**: For every endpoint receiving user input:
   - Does the DTO have `@NotBlank`/`@NotNull`/`@Valid` on every required field?
   - Is the controller method annotated with `@Validated`?
   - Is the parameter annotated with `@Valid`?
3. **Authorization**: For every mutating endpoint:
   - Does it verify the caller is the room owner (for owner-only actions)?
   - Does it verify the caller is a member of the room?
   - Does it verify the action target (player being kicked, etc.) is in the room?
4. **Data leaks**: For every entity/model returned in API responses:
   - Are passwords, internal IDs, or secrets excluded via `@JsonIgnore` or DTO projection?
   - Are stack traces ever returned to the client?
5. **Use codegraph** to find all endpoints and trace their authorization guards.

### Output Format

```
## Security Review

### P0 Critical (auth bypass / data leak)
- [file:line] Issue description + impact

### P1 Warning (missing validation)
- [file:line] Issue description

### P2 Suggestions
- [file:line] Hardening recommendation

### Overall
[One sentence about security posture]
```
