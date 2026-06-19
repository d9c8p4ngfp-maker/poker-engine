# Phase 2b: 游戏引擎 Implementation Plan

> **目标:** 纯函数式德州扑克引擎 —— GameState + Action → NewState，全部单元测试覆盖，不依赖 Spring/WebSocket。

---

## 架构

```
GameEngine (编排)
  ├── ActionValidator    (动作合法性)
  ├── BettingRoundManager (回合推进、下注平衡)
  ├── PhaseTransition    (发牌、阶段切换)
  └── HandResolver       (HandEvaluator + SidePotCalculator)
```

所有模块在 `server/.../engine/` 下，不修改现有文件。

---

### Task 1: GameAction + GamePhase 枚举

**Files:**
- Create: `server/src/main/java/com/first/poker/engine/GameAction.java`
- Create: `server/src/test/java/com/first/poker/engine/GameActionTest.java`

枚举定义:

```java
public enum GameAction { FOLD, CHECK, CALL, BET, RAISE }
public enum GamePhase { PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN, HAND_OVER }
```

测试: 验证枚举值存在即可。

---

### Task 2: GamePlayerState

**Files:**
- Create: `.../engine/GamePlayerState.java`
- Create: `.../engine/GamePlayerStateTest.java`

```
record GamePlayerState(
    String playerId, String nickname, int seatIndex,
    int chips, int totalBet, boolean folded, boolean allIn,
    List<Card> holeCards
)
```

测试: 不可变性、from Player model 构造、chips/totalBet 分离。

---

### Task 3: GameState

**Files:**
- Create: `.../engine/GameState.java`
- Create: `.../engine/GameStateTest.java`

```
record GameState(
    GamePhase phase,
    List<GamePlayerState> players,
    List<Card> communityCards,
    int pot,
    int currentBet,         // 本阶段最高下注额
    int minRaise,           // 最小加注额
    int currentPlayerIndex, // ordering list 中的索引
    int dealerIndex,
    int smallBlindPosition, // ordering 中小盲位置
    Deck deck
)
```

**关键概念**: `players` 列表按座位顺序排列（从 dealer 下一个开始），`currentPlayerIndex` 指此列表中的位置。

---

### Task 4: ActionValidator

**Files:**
- Create: `.../engine/ActionValidator.java`
- Create: `.../engine/ActionValidatorTest.java`

```
static List<GameAction> legalActions(GameState state, String playerId)
static void validate(GameState state, String playerId, GameAction action, int amount)
```

规则:
- FOLD 始终合法（除非已 fold/allIn）
- CHECK: 仅当 currentBet == playerBetInRound
- CALL: 仅当 currentBet > playerBetInRound
- BET: 仅当 currentBet == 0 且 amount >= bigBlind
- RAISE: 仅当 currentBet > 0 且 amount >= currentBet + minRaise
- 非 currentPlayer 不能操作
- 已 fold/allIn 玩家自动跳过
- amount 不能超过玩家剩余筹码

---

### Task 5: BettingRoundManager

**Files:**
- Create: `.../engine/BettingRoundManager.java`
- Create: `.../engine/BettingRoundManagerTest.java`

```
static GameState applyAction(GameState state, GameAction action, int amount)
static boolean isRoundComplete(GameState state)
static int nextPlayerIndex(GameState state)
```

**isRoundComplete 判定**: 所有 active 玩家下注额相等 且 每个人都至少行动过一次。

**nextPlayerIndex**: 跳过已 fold/allIn 的玩家，循环到下一个。

**applyAction**:
- FOLD → 标记 folded, 推进
- CHECK → 推进
- CALL → 补足差额, 推进
- BET/RAISE → 更新 currentBet, 推进

---

### Task 6: PhaseTransition

**Files:**
- Create: `.../engine/PhaseTransition.java`
- Create: `.../engine/PhaseTransitionTest.java`

```
static GameState startHand(List<GamePlayerState> players, int dealerIndex, RoomConfig config)
static GameState advancePhase(GameState state)
```

**startHand**:
1. 创建 Deck 并 shuffle
2. Post blinds (SB/BB)
3. Deal 2 张底牌给每个活跃玩家
4. Phase = PRE_FLOP
5. currentPlayer = UTG (大盲后第一个)

**advancePhase**:
- PRE_FLOP → FLOP: deal 3 community cards, reset bets
- FLOP → TURN: deal 1, reset bets
- TURN → RIVER: deal 1, reset bets
- RIVER → SHOWDOWN: resolve hands

---

### Task 7: HandResolver

**Files:**
- Create: `.../engine/HandResolver.java`
- Create: `.../engine/HandResolverTest.java`

```
record ResolvedHand(int rank, String name, List<Card> bestFive)

static Map<String, ResolvedHand> resolveHands(GameState state)
static List<SidePotCalculator.PotResult> distributePots(GameState state, Map<String, ResolvedHand> hands)
```

---

### Task 8: GameEngine

**Files:**
- Create: `.../engine/GameEngine.java`
- Create: `.../engine/GameEngineTest.java`

编排层: 接收 GameState + Action → 返回新 GameState + events

```
record GameEvent { Type type; Map<String, Object> data; }
// Type: CARDS_DEALT, PLAYER_ACTED, PHASE_CHANGED, HAND_RESOLVED
```

集成测试: 完整一手牌流程 (3 人, Preflop → Showdown)。

---

## TDD 流程

每步都是 RED → GREEN → COMMIT:
1. 写单元测试 → 编译失败
2. 写最小实现 → 测试通过
3. `git commit`
