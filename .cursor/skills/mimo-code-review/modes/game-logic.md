# Mode: game-logic

## When to Use

- Modified: `GameEngine`, `HandResolver`, `BettingRoundManager`, `SidePotCalculator`, `ActionValidator`, `GameSessionService` (game-flow methods)
- Changes in: `syncRoomChips`, `dealerIndex`, `GameState`, `GamePlayerState`, `GamePhase`
- User mentions: "牌局", "盲注", "边池", "all-in", "dealer", "chip sync", "hand result"

## Known Historical Bugs

- **C1**: `Room.dealerIndex` initialized to 0, never incremented — blinds never rotate across hands
- **M12**: `syncRoomChips` overwrites borrow-amount — in-hand chip loans erased after hand
- **M11**: Bots added mid-game get ACTIVE status but aren't in GameState — inconsistent player roster
- **M10**: `startNewHand` didn't filter ACTIVE players correctly — folded/left players dealt cards
- **M13**: `endGame` called before `syncRoomChips` — winner chips not persisted

## Deep Review Task

You are a poker engine reviewer with **full read access**. Read the actual source files — don't just scan the diff.

### What to do

1. **Read all modified game-logic files** in full.
2. **Trace a complete hand lifecycle**: `startNewHand` → deal → betting rounds → showdown → `endGame` → `syncRoomChips` → `checkGameOver`. Verify:
   - `dealerIndex` increments correctly and wraps modulo active player count
   - Blind amounts come from `RoomConfig`, not hardcoded
   - Posting blinds deducts correctly, including edge case where SB=BB (heads-up)
   - All-in handling: side pot calculation correct, player excluded from further betting
3. **Check chip integrity**: After every hand, does `Room.chips == GameState.playerChips` for all players? Trace `syncRoomChips` — does it preserve in-hand borrows?
4. **Edge cases**: What happens when:
   - Only 1 player remains (everyone else folded)?
   - Multiple all-in players, one wins?
   - Sole survivor before showdown (all others folded)?
   - A player disconnects mid-hand, then hand completes?
5. **Use codegraph** to find all callers of `endGame`, `startNewHand`, `syncRoomChips` — verify correct ordering.

### Output Format

```
## Game Logic Review

### P0 Critical (incorrect game rule)
- [file:line] Issue description + impact

### P1 Warning (edge case missing)
- [file:line] Issue description

### P2 Suggestions
- [file:line] Cleanup/clarity improvement

### Overall
[One sentence about game rule correctness]
```
