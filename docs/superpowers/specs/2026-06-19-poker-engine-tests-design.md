# Poker Engine Test Suite — Comprehensive Coverage

> Based on `@idealic/poker-engine` methodology: 31 PokerStars fixtures + 9 croupier scenarios + stacks unit tests.
>
> **Reviewers**: Self-review (3 rounds) + Mimo agent (mimo-v2.5-pro, ses_11f9c1cf3ffexMEaaxUS71eShY, 2026-06-19)

---

## Goal

Eliminate all engine bugs by testing every poker scenario that has caused production issues, plus every edge case found in the industry-standard test suite. After this, no more "all-in froze," "pot disappeared," "chips evaporated," or "folded player's money vanished."

---

## Engine Module Architecture（完整 14 文件）

Spec 必须覆盖全部 engine 模块，非仅底层工具类。以下为完整清单：

| File | 类型 | 职责 | 字段 / 方法 |
|------|------|------|------------|
| `GameEngine.java` | Static facade | 核心调度：startHand, processAction | `ActionResult{state, events, handComplete, winners}` |
| `GameState.java` | Record | 不可变状态容器 | 18 fields: phase, players, communityCards, pot, currentBet, minRaise, currentPlayerIndex, dealerIndex, smallBlindAmount, bigBlindAmount, deck, actedMask, lastAggressorIndex |
| `GamePlayerState.java` | Record | 不可变玩家状态 | 9 fields: playerId, nickname, seatIndex, chips, totalBet, roundBet, folded, allIn, holeCards |
| `PhaseTransition.java` | Static | 阶段转换：startHand, advancePhase | Blind posting, card dealing, phase advance, first-to-act calculation |
| `BettingRoundManager.java` | Static | 下注回合逻辑 | applyAction, isRoundComplete, advanceToNextActive |
| `ActionValidator.java` | Static | 动作合法性 | legalActions, validate |
| `HandEvaluator.java` | Static | 7 选 5 牌型评估 | evaluate → `Hand{rank, name}` |
| `HandResolver.java` | Static | 摊牌判定 | resolveHands, distributePots |
| `SidePotCalculator.java` | Static | 边池切片 | calculate → `PotResult[]` |
| `GameStateSnapshot.java` | Static | 快照序列化 | buildPublic, buildPrivate |
| `Deck.java` | Class | 牌堆（mutable！） | deal, shuffle |
| `Card.java` | Record | 单张牌 | Rank, Suit |
| `GamePhase.java` | Enum | PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN, HAND_OVER |
| `GameAction.java` | Enum | FOLD, CHECK, CALL, BET, RAISE |

### 关键架构约束（Mimo 代码审查发现）

1. **GameState immutability 被 Deck 破坏**：`GameState` 是 record 但 `deck` 字段 mutable（`deal()` 改变内部状态）。`processAction` 返回的 `newState` 与输入 `state` **共享同一 deck 实例**，产生副作用。`GameStateImmutabilityTest` 必须覆盖 deck 共享问题。

2. **BettingRoundManager.isRoundComplete 不检测 sole survivor**：只检查 roundBet 是否相等，不检测 `nonFoldedCount == 1` 的早结算场景。→ **P0 bug**。

3. **Pot 在回合中始终为 0**：`PhaseTransition.startHand` 初始化 `pot=0`，盲注扣除后不更新 pot。pot 只在 `advancePhase` 时从 `roundBet` 累加。→ **P0 bug**。

4. **HandEvaluator 使用 `Rank.ordinal()` 而非 `rank.value`**：Wheel 检测依赖 enum 声明顺序，若 enum 顺序变化即失效。建议迁移到 `rank.numericValue()`。

5. **SidePotCalculator uncalled bet 注释与实现矛盾**：注释说 "Don't add to pots"，实现却把 uncalled bet 加入 pots 由 sole player 赢得。行为符合规则（uncalled bet 进入 pot），但注释错误。

---

## P0 Engine Bugs（Mimo 代码审查确认）

| # | Bug | 根因 | Fix 位置 | 工作量 |
|---|-----|------|---------|--------|
| B1 | Sole survivor not settled | `BettingRoundManager.isRoundComplete` 不检测 nonFoldedCount==1 | `GameEngine.processAction` — after FOLD, check activeCount==1 → award pot + return handComplete=true | 0.5d |
| B2 | Pot=0 during betting round | `PhaseTransition.startHand` 设 pot=0, `BettingRoundManager.applyAction` 不更新 pot | `BettingRoundManager.applyAction` — after withChipsDeducted, add committed amount to `state.pot()` | 0.5d |
| B3 | Preflop raise→all fold not settled | Same root cause as B1 (sole survivor detection missing) | Fixed by B1 | 0.5d |
| B4 | Deck mutable → immutability broken | `deck.deal()` mutates shared instance | `GameEngine.processAction` — copy deck before acting, or `GameState` use `deck.clone()` | 0.5d |
| B5 | HandEvaluator depends on `ordinal()` | `Rank.ordinal()` unstable to enum reorder | Migrate to `rank.numericValue()` | 0.5d |

---

## Architecture（去重后）

```
Layer 3: Multi-hand sequences (7 engine + 3 service)
  ├── MultiHandSequenceTest (new, 7)
  └── GameSessionServiceTest / RoomControllerTest (+3)

Layer 2: Scenario replays (31 net-new)
  ├── ShowdownScenariosTest (new, 7)
  ├── AllInEdgeCasesTest (new, 8)
  ├── BettingPatternsTest (new, 8)
  ├── PositionAndDealerTest (new, 8)
  └── PokerEngineFixtureTest (existing, 10) — DO NOT duplicate

Layer 1: Unit tests (26 net-new + 83 existing)
  ├── SidePotCalculatorTest (existing 4, +1)
  ├── BettingRoundManagerTest (existing 7, +1)
  ├── ActionValidatorTest (existing 7, +3)
  ├── ErrorPathTest (new, 3)
  ├── GameStateImmutabilityTest (new, 3) — 含 deck 共享断言
  ├── HandEvaluatorTest (existing 12, +2)
  ├── GamePlayerStateTest (existing 6, +1)
  ├── ProductionRegressionTest (new, 5)
  ├── DeckDeterminismTest (new, 2) — 固定 seed + 发牌可重复
  ├── BettingRoundSoleSurvivorTest (new, 2) — B1+B3 回归
  └── PotTrackingRegressionTest (new, 2) — B2 回归
```

**总计: 83 existing + 46 net-new = 129 engine tests**

### 计数核对

| Section | Count |
|---------|-------|
| ProductionRegressionTest | 5 |
| P0 engine fix tests (sole survivor + preflop fold-out) | 2×2 = 4* |
| SidePotCalculatorTest | 1 |
| BettingRoundManagerTest | 1 |
| ActionValidatorTest | 3 |
| ErrorPathTest | 3 |
| GameStateImmutabilityTest | 3 |
| HandEvaluatorTest | 2 |
| GamePlayerStateTest | 1 |
| ShowdownScenariosTest | 7 |
| AllInEdgeCasesTest | 8 |
| BettingPatternsTest | 6† |
| PositionAndDealerTest | 8 |
| MultiHandSequenceTest | 7 |
| DeckDeterminismTest | 2 |
| BettingRoundSoleSurvivorTest | 2 |
| PotTrackingRegressionTest | 2 |
| **Total net-new** | **46** |

\* P0 engine fix tests: `BettingRoundSoleSurvivorTest`(2) + `PotTrackingRegressionTest`(2) = 4.  
† `BettingPatternsTest`: 8 total minus 2 P0 (already counted in sole survivor / preflop sections) = 6 net-new.

---

## Existing Test Inventory（83 tests，前次加总误差已修正）

| File | Count | Covers |
|------|-------|--------|
| PokerEngineFixtureTest | 10 | Folded→pot, multi-level all-in, uncalled return, split pot, all-in preflop auto-deal, multi-way all-in, check-to-showdown, fold-continues-hand, pot-once-only, allIn flag |
| ActionValidatorTest | 7 | fold/check/call/bet/raise legality, folded player blocked, all-in bet allowed |
| BettingRoundManagerTest | 7 | check/call/bet/raise apply, round complete, unequal bets, not all acted since raise |
| GameEngineTest | 7 | startHand, fold, check rejection, preflop complete, full showdown, chip distribution, winner info |
| HandEvaluatorTest | 12 | 8 hand types + wheel detection + higherHandShouldBeatLowerHand |
| GameStateSnapshotTest | 5 | public/private snapshot, community cards, pot/currentBet, phase mapping |
| GameStateTest | 5 | create, currentPlayer, setPot, advance index, skip folded/allIn |
| GamePlayerStateTest | 6 | create, fromPlayer, chipsDeducted, cap at remaining, folded, holeCards |
| PhaseTransitionTest | 4 | startHand blinds/cards, preflop→flop, flop→turn→river, river→showdown |
| HandResolverTest | 4 | single eval, winner, tie, distribute pot |
| SidePotCalculatorTest | 4 | single pot, one all-in side pot, 3-player all-in, exclude folded from winning |
| DeckTest | 5 | 52 cards, deal, unique, empty throw, shuffle |
| CardTest | 5 | create, toString, parse, invalid, ordering |
| GameTypesTest | 3 | actions enum, phases enum, phase order |
| AllInCrashTest | 1 | 8-player all-in showdown no crash |
| **Total** | **83** | |

### Already Covered — 禁止重复编写

| 原计划「新增」 | 已有测试 |
|---------------|---------|
| SidePot folded contribution | `PokerEngineFixtureTest.sidePotShouldIncludeFoldedPlayerStakes` |
| SidePot four-level slicing | `PokerEngineFixtureTest.multiLevelAllInShouldCreateCorrectSidePots` |
| SidePot uncalled bet | `PokerEngineFixtureTest.uncalledBetReturn` |
| SidePot split pot | `PokerEngineFixtureTest.splitPotEvenlyBetweenTwoWinners` |
| All-in preflop auto-deal | `PokerEngineFixtureTest.allInPreflopShouldAutoDealAllStreets` |
| Multi-way all-in diff stacks | `PokerEngineFixtureTest.multiWayAllInDifferentStacks` |
| All-in call flag set | `PokerEngineFixtureTest.allInFlagWhenChipsReachZero` |
| Both all-in preflop | AllInCrashTest 8-player variant |
| Check to showdown | `PokerEngineFixtureTest.allPlayersCheckToShowdown` |
| Fold continues hand | `PokerEngineFixtureTest.foldDoesNotEndHandWhenOthersRemain` |
| Pot to winner | `GameEngineTest.shouldDistributeChipsAtShowdown` |
| Folded chips in pot (muck) | `PokerEngineFixtureTest.sidePotShouldIncludeFoldedPlayerStakes` |
| Hole cards in snapshot | `GameStateSnapshotTest` (扩展断言即可) |
| Wheel detection | `HandEvaluatorTest.shouldDetectWheelStraight` |
| Position skip allIn | `GameStateTest.shouldSkipFoldedAndAllInPlayers` |
| Round complete after raise | `BettingRoundManagerTest.shouldNotBeCompleteIfNotAllActedSinceLastRaise` |

### 真实缺口

| Gap | Priority |
|-----|----------|
| Sole survivor wins pot without showdown (B1) | P0 |
| Pot tracking during betting round (B2) | P0 |
| Preflop raise→all fold not settled (B3) | P0 |
| GameState immutability: deck sharing (B4) | P0 |
| HandEvaluator ordinal() dependency (B5) | P1 |
| Deck deterministic seed constructor | P1 |
| Error paths (hand over, min raise, nothing to call) | P1 |
| 0-chips / all-in player legal actions | P1 |
| Wheel vs 6-high straight rank comparison | P1 |
| Same pair kicker comparison | P1 |
| Chips never negative after deduction | P1 |
| Betting patterns: raise-call, reraise, check-raise, multi-limp, min bet | P2 |
| Position/dealer: 6-player, heads-up, post-flop first-to-act | P2 |
| Multi-hand chip/button continuity (engine layer) | P2 |
| SidePotCalculator comment/impl contradiction fix | P2 |
| 31 fixtures: 21 unmapped | P3 |
| bettingState.test.ts scenarios | P3 |

---

## In Scope

| Category | Scenarios | Net-new tests |
|----------|-----------|---------------|
| P0 engine fixes | Sole survivor (B1), pot mid-round (B2), preflop fold-out (B3), deck immutability (B4) | +7 |
| Side pot | Empty stakes safety net | +1 |
| All-in | BB all-in from blind, skip in turn order, incremental multi-street, 4-way with mid-fold, 3-way all-in, allIn flag persistence | +8 |
| Showdown | Winner info, snapshot fields, roundBet reset, chip conservation (NOT rank comparison) | +7 |
| Betting patterns | raise-call, reraise, check-raise, multi-limp, min bet, pot step tracking | +6 |
| Position/dealer | 6-player SB/BB/UTG, 3-player UTG wrap, heads-up SB=button, post-flop first act, skip folded/allIn | +8 |
| Multi-hand | 2-hand chips, button rotate, busted exclude, blind after rotate, totalBet reset, 3-hand audit | +7 engine |
| Edge / regression | error paths, legal actions, negative chips, Deck determinism | +10 |

**Out of Scope（明确排除）**：Ante、Sit-out/rejoin、Rake、PokerStars parser、Stats、Narrative、Partial blind posting。

---

## Test Infrastructure

**路径**：`server/src/test/java/com/first/poker/engine/`

**执行**：
```bash
cd server && mvn test -Dtest=com.first.poker.engine.*
```

**共享 helper**（`EngineTestSupport.java`）：

```java
static void assertTotalChipsConserved(GameState state, int expectedTotal) {
    int sum = state.players().stream().mapToInt(GamePlayerState::chips).sum();
    assertNotEquals(0, state.pot(), "Pot should not be zero at showdown");
    assertEquals(expectedTotal, sum, "Total player chips must be conserved");
}

static GamePlayerState player(String id, int chips) {
    return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, List.of());
}

static GamePlayerState playerWithCards(String id, int chips, List<Card> holeCards) {
    return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, holeCards);
}

static RoomConfig config(int sb) {
    RoomConfig c = RoomConfig.withDefaults();
    c.setSmallBlind(sb); // BB = sb * 2 via RoomConfig.withDefaults()
    return c;
}
```

**确定性卡牌（必修——Mimo 审查要求）**：

当前 `Deck()` 每次 new 都是随机洗牌，测试不可重复。必须添加：

```java
public Deck(long seed) {
    this.cards = new ArrayList<>(FULL_DECK);
    Collections.shuffle(this.cards, new Random(seed));
}
```

集成测试中：优先用 `Deck(42L)` 固定 seed；牌型特定测试用 `withHoleCards` 注入。

**筹码规则**：
- 全部 `int`，无小数。
- Split pot 余数：eligible 列表中 **第一个** 玩家获得。
- `withChipsDeducted`：`actual = min(amount, chips)`，保证 `chips >= 0`。

---

## Test Design Principles (TDD)

1. **RED first**：测试先于实现。
2. **Verify RED**：确认失败原因是逻辑缺失，不是 typo。
3. **GREEN minimal**：最小代码通过。
4. **Verify GREEN**：全量 `mvn test` 无回归。
5. **REFACTOR**：绿后再清理。
6. **One behavior per test**：测试名不含 "and"。
7. **Real code, no mocks**：调用真实 engine 方法。

### 复杂场景逐步构建（TDD）

以 `AllInEdgeCasesTest.incrementalAllInAcrossStreets` 为例：

1. 设置 3 玩家各 500 chips，`startHand` → 断言 `phase=PRE_FLOP`。
2. Preflop: pA bet 100, pB call, pC call → 断言 `phase=FLOP`，pA chips=400。
3. Flop: pA bet 200, pB call, pC call → 断言 pA chips=200。
4. Turn: pA bet 200 → 断言 pA.allIn=true, pA.chips=0。
5. pB call, pC fold → 断言 fast-forward 或 river betting。
6. 最终断言 `assertTotalChipsConserved(state, 1500)`。

---

## P0 Engine Fix + Regression Tests（0 Must-Pass Before Any Other Work）

### B1 + B3: Sole Survivor（含 preflop fold-out）

**File:** `server/src/test/java/com/first/poker/engine/BettingRoundSoleSurvivorTest.java`

#### Test 1: Sole survivor wins blinds without showdown
```
GIVEN: 3 players pA/pB/pC各1000, dealer=0, SB=10, BB=20
       startHand → pA(UTG) FOLD, pB(SB) FOLD
       此时仅 pC(BB) 存活。pC 已 post BB 20（chips=980）。
WHEN:  after pB folds, isRoundComplete returns true for sole survivor
THEN:  handComplete=true
       pC wins pot = 30 (SB + BB from folded players only)
       pC chips == 980 + 30 = 1010
       assertTotalChipsConserved(state, 3000)
```
**[RED: handComplete=false, game waits for pC action]**

#### Test 2: Preflop raise, everyone folds
```
GIVEN: 3 players, PRE_FLOP, SB=10, BB=20
       pA(UTG) RAISE 60 (committed 60, chips=940)
       pB(SB) FOLD (loses 10)
       pC(BB) FOLD (loses 20)
WHEN:  after pC folds, sole survivor detected
THEN:  handComplete=true
       pA chips == 1000 - 60 + 60(own returned) + 30(blinds won) = 1030
       assertTotalChipsConserved(state, 3000)
```
**[RED: handComplete=false OR pA chips wrong]**

### B2: Pot Tracking During Betting Round

**File:** `server/src/test/java/com/first/poker/engine/PotTrackingRegressionTest.java`

#### Test 1: Pot increases per action during preflop
```
GIVEN: 3 players, startHand → pot=0 (known bug), blinds posted (SB 10, BB 20)
       pA(UTG) CALL 20 → state.pot() should be 30
WHEN:  after each processAction or applyAction
THEN:  state.pot() reflects cumulative committed amounts up to that point
```
**[RED: pot == 0 after actions during round]**

#### Test 2: Pot correct at advancePhase
```
GIVEN: 3 players, preflop complete → advancePhase to FLOP
THEN:  state.pot() == 60 (3 players × 20 each in blinds+calls)
```
**[RED: pot stays 0 after phase advance]**

### B4: Deck Immutability

**File:** `GameStateImmutabilityTest.test3_deckNotSharedAfterProcessAction`

```
GIVEN: state with fresh Deck(42L), record pre-action deck state
WHEN:  result = processAction(state, CALL, 0)
THEN:  newState = result.state()
       newState.deck() is a DIFFERENT instance from state.deck()
       state.deck() still contains cards that were dealt from newState.deck()
```
**[RED: old state's deck has cards already dealt from it]**

### B5: HandEvaluator ordinal() Audit

**File:** `HandEvaluatorTest.test15_rankNumericValueNotOrdinal`

```
GIVEN: Rank enum with explicit numeric values (TWO=2, ..., ACE=14)
WHEN:  evaluate any hand
THEN:  Wheel detection uses rank.numericValue() not rank.ordinal()
       (Test: swap TWO and THREE in enum → Wheel should still be A2345)
```
**[RED: if ordinal() used, Wheel detection fails after enum reorder]**

---

## Layer 1: Unit Test Specifications

### DeckDeterminismTest — 2 new

**File:** `server/src/test/java/com/first/poker/engine/DeckDeterminismTest.java`

| # | Test | GIVEN | THEN | RED |
|---|------|-------|------|-----|
| 1 | fixedSeedProducesDeterministicDeal | Deck(42L).deal(5) | same cards every invocation | different cards per run |
| 2 | differentSeedsProduceDifferentDeals | Deck(42L) vs Deck(99L) | first 5 cards differ | identical |

### GameStateImmutabilityTest — 3 new

**File:** `server/src/test/java/com/first/poker/engine/GameStateImmutabilityTest.java`

| # | Test | THEN | RED |
|---|------|------|-----|
| 1 | processActionPreservesInputFields | original state phase/pot/index unchanged | state mutated |
| 2 | withUpdatedPlayerPreservesOld | old players list entry unchanged | old entry replaced |
| 3 | deckNotSharedAfterProcessAction | newState.deck() ≠ state.deck(); old deck not drained | old deck had cards dealt |

### SidePotCalculatorTest — +1

| # | Test | RED |
|---|------|-----|
| 5 | emptyStakesReturnsEmptyList | passes immediately — safety net |

### BettingRoundManagerTest — +1

| # | Test | RED |
|---|------|-----|
| 8 | potIncreasesPerApplyAction | pot unchanged after applyAction |

### ActionValidatorTest — +3

| # | Test | THEN | RED |
|---|------|------|-----|
| 8 | zeroChipsOnlyFold | legalActions empty (isActive=false) | returns actions |
| 9 | allInPlayerNotActive | legalActions empty | non-empty |
| 10 | minRaiseEnforced | throws "Raise must be at least 60" | no throw |

### ErrorPathTest — 3 new

**File:** `server/src/test/java/com/first/poker/engine/ErrorPathTest.java`

| # | Test | GIVEN | THEN | RED |
|---|------|-------|------|-----|
| 1 | actionAfterHandOver | phase=SHOWDOWN | throws "Hand is already over" | no throw |
| 2 | callWhenNothingToCall | currentBet=20, roundBet=20 | throws "Nothing to call" | no throw |
| 3 | betWhenMustRaise | currentBet=40 | throws "Must raise, not bet" | no throw |

### HandEvaluatorTest — +2

| # | Test | THEN | RED |
|---|------|------|-----|
| 13 | wheelRanksBelowSixHigh | wheel.rank() < sixHigh.rank() | equal or reversed |
| 14 | pairKickerDecides | AA+KQ > AA+JT | equal |

### GamePlayerStateTest — +1

| # | Test | RED |
|---|------|-----|
| 7 | chipsNeverNegative | chips=-70 after deduct 100 |

---

## Layer 2: Scenario Test Specifications

### ProductionRegressionTest — 5 new

**File:** `server/src/test/java/com/first/poker/engine/ProductionRegressionTest.java`

| # | Name | GIVEN/WHEN/THEN | RED |
|---|------|---------------|-----|
| 1 | potVisibleDuringBettingRound | 3 players preflop, after each call assert `state.pot()` increases | pot stays 0 mid-round |
| 2 | foldedPlayerChipsStayInPot | A bets 100 folds, B calls 100, showdown → B wins 200 total | B wins only 100 |
| 3 | allInPreflopDoesNotFreeze | 3 players, 2 all-in 1 fold → phase SHOWDOWN, handComplete=true | phase stuck PRE_FLOP |
| 4 | showdownRevealsNonFoldedHoleCards | SHOWDOWN state, buildPublic → non-folded have non-null holeCards | all holeCards null |
| 5 | zeroChipsPlayerCannotBet | player chips=0, allIn=true → legalActions empty, validate BET throws | BET still legal |

### ShowdownScenariosTest — 7 new（不测牌型比较）

| # | Test | Key assert |
|---|------|-----------|
| 1 | winnerHasNicknameAndHandName | winners non-empty, nickname/handName/amount > 0 |
| 2 | twoPlayerShowdownConservesChips | total == 2000 |
| 3 | threePlayerOneWinner | one player +pot, others +0, total == 3000 |
| 4 | snapshotShowsFiveCommunityCards | communityCards.size()==5 |
| 5 | snapshotPhaseFinished | phase=SHOWDOWN, status=FINISHED |
| 6 | roundBetZeroAtShowdown | all players roundBet==0 |
| 7 | winnerAmountEqualsPotNoRake | single-winner precond: winner.amount == sum of totalBets |

### AllInEdgeCasesTest — 8 new

| # | Test | Key assert |
|---|------|-----------|
| 1 | bbAllInFromBlindPost | pB chips=0, allIn after BB post |
| 2 | allInSkippedInTurnOrder | nextActive skips allIn player |
| 3 | incrementalAllInAcrossStreets | allIn on final street bet, conservation 1500 |
| 4 | allInWithFoldedContributions | winner gets folded BB |
| 5 | fourWayAllInOneFoldMid | flop continues for 2 non-allIn |
| 6 | shortAllInCallNotFullMatch | commits min(chips,toCall), allIn=true |
| 7 | threeWayAllInAutoDeal | SHOWDOWN, 5 board cards |
| 8 | allInFlagPersistsUntilHandReset | pA allIn=true via CALL, flag stays true through showdown |
  ```
  GIVEN: 2 players heads-up, pA chips=50, pB chips=1000
         pB RAISE 100, pA CALL (all-in, chips→0, allIn=true)
  WHEN:  fast-forward to SHOWDOWN completes
  THEN:  pA.allIn() still true, pA.chips() >= 0
  ```
  **[RED: allIn flag reset to false before showdown]**

### BettingPatternsTest — 6 new（P0 已拆分）

| # | Test | Actions summary |
|---|------|----------------|
| 1 | betRaiseCall | FLOP: B bet 20, C raise 60, A fold, B call 40 → TURN |
| 2 | betRaiseReraiseFoldCall | FLOP: B bet 20, C raise 60, D raise 120, A/B fold, C call → TURN |
| 3 | checkRaise | FLOP: B check, C bet 20, A fold, B raise 60, C call → TURN |
| 4 | multiLimpPreflop | 4 players all call/check → FLOP pot=80 |
| 5 | minBetRejected | validate BET 10 when minRaise=20 → throw |
| 6 | potStepsOnFlop | FLOP pot starts at 60 (3×20 preflop), B bet 20→80, C call→100, A call→120 |

### PositionAndDealerTest — 8 new

| # | Test | THEN |
|---|------|------|
| 1 | sixPlayerBlindPositions | dealer=0 → SB=1, BB=2, UTG=3 |
| 2 | threePlayerUtgWraps | dealer=0 → UTG=0 |
| 3 | headsUpSbIsButton | dealer=0 → SB=0, BB=1, current=0 preflop |
| 4 | postFlopFirstActLeftOfDealer | dealer=2, 6 players → first act index 3 |
| 5 | postFlopSkipsFolded | index 1 folded → skipped |
| 6 | postFlopSkipsAllIn | index 1 allIn → skipped |
| 7 | postFlopWrapsAround | 3 players dealer=2 → first act 0 |
| 8 | headsUpPostFlopSbActsFirst | dealer=0, 2 players FLOP → SB acts first |

*不含 sitting out — Out of Scope。*

---

## Layer 3: Multi-Hand Tests

### MultiHandSequenceTest — 7 engine tests

| # | Test | THEN |
|---|------|------|
| 1 | twoHandChipCarryover | hand2 chips match hand1 results |
| 2 | buttonRotates | dealer hand2 = (dealer hand1 + 1) % n |
| 3 | bustedPlayerExcluded | 0-chip player not in hand2 player list |
| 4 | blindsAfterRotation | hand2 SB/BB indices correct |
| 5 | totalBetResetNewHand | all totalBet==0 at hand2 start |
| 6 | threeHandConservation | sum chips constant across 3 hands |
| 7 | headsUpAfterBust | 3→2 players, SB=dealer |

### Service layer（非 engine 文件）

| # | Test | File | THEN |
|---|------|------|------|
| S1 | handCountIncrements | GameSessionServiceTest | handCount==2 after 2 hands |
| S2 | joinBetweenHands | RoomControllerTest | new player in hand2 only |
| S3 | joinDuringHandDeferred | RoomControllerTest | new player not in current hand |

---

## Source Material & Fixture Mapping

### Croupier 01–09 → Java

| idealic file | Scenario | Java test | Status |
|-------------|----------|-----------|--------|
| `01-complete-hand.test.ts` | Full hand to showdown | `GameEngineTest.shouldCompleteFullHandToShowdown` | ✅ |
| `02-all-fold.test.ts` | Everyone folds to one winner | **BettingRoundSoleSurvivorTest** | ❌ → P0 |
| `03-all-in.test.ts` | Single all-in preflop auto-deal | `PokerEngineFixtureTest` | ✅ |
| `04-multi-way-all-in.test.ts` | Different stack depths | `PokerEngineFixtureTest` | ✅ |
| `05-showdown.test.ts` | Showdown resolution | `GameEngineTest.shouldDistributeChipsAtShowdown` | ✅ partial |
| `06-reraise-fold.test.ts` | Bet-raise-reraise-fold | **BettingPatternsTest.test2** | ❌ |
| `07-betting-states.test.ts` | actedMask / round complete | **BettingRoundManagerTest + bettingState port** | ❌ partial |
| `08-multi-street.test.ts` | Check through streets | `PokerEngineFixtureTest` | ✅ |
| `09-sb-all-in.test.ts` | SB all-in < BB | Out of scope | ⏸ |

### stacks.test.ts → Java

| Scenario | Java test | Status |
|----------|-----------|--------|
| winners_folded | `PokerEngineFixtureTest.sidePotShouldIncludeFoldedPlayerStakes` | ✅ |
| multiway_winner_takes_all | `PokerEngineFixtureTest.multiLevelAllInShouldCreateCorrectSidePots` | ✅ |
| uncalled_return | `PokerEngineFixtureTest.uncalledBetReturn` | ✅ |
| split_pot | `PokerEngineFixtureTest.splitPotEvenlyBetweenTwoWinners` | ✅ |
| rake (out of scope) | — | ⏸ |

### 31 PokerStars fixtures → 翻译规则

1. **Input**：读取 `fixture.input` 中的 action 序列，按序调用 `GameEngine.processAction`。
2. **Setup**：holeCards/communityCards 通过 `GamePlayerState.withHoleCards()` 和 `PhaseTransition` 注入。
3. **Assert**：比对 `fixture.game` 的 `phase`、`pot`、每个 player 的 `chips`/`folded`/`allIn`/`totalBet`、winner id。

| Fixture range | 计划 | Status |
|--------------|------|--------|
| 01–10 | 已有 croupier/stacks 对应 | ✅ |
| 11–20 | Phase 2 | ❌ |
| 21–31 | Phase 3 | ❌ |

---

## Out of Scope + Future Extension Points

**排除**：Ante、Sit-out/rejoin、Rake、PokerStars parser、Stats、Narrative、Partial blind posting。

**未来扩展点**：
- `GamePlayerState` 可加 `boolean sittingOut` 字段；`PhaseTransition.startHand` 过滤 sittingOut 玩家。
- `PhaseTransition` 可加 ante 扣除逻辑；`SidePotCalculator` 可加 rake 层。
- `BettingRoundManager` 处理 player 列表时通过 filter 扩展。
- `HandEvaluator` 迁移 `ordinal()` → `numericValue()` 以支持 enum 安全重排。
- `SidePotCalculator` 注释修正：明确 uncalled bet 进入 pot（符合规则）。

---

## Success Criteria

- [ ] **5 P0 bugs fixed** (sole survivor + pot mid-round + preflop fold-out + deck immutability + ordinal() audit)
- [ ] **46 net-new tests pass**
- [ ] **83 existing tests pass** (zero regression)
- [ ] Every net-new test watched fail for documented `[RED]` reason before GREEN
- [ ] `mvn test -Dtest=com.first.poker.engine.*` green
- [ ] Coverage includes: all 14 engine modules, pot mid-round, deck immutability, error paths, legal actions for 0-chips/allIn, wheel vs 6-high, pair kicker, negative chips guard, production regressions, position/dealer, multi-hand continuity, Deck determinism
- [ ] `Deck(long seed)` constructor added
- [ ] `SidePotCalculator` comment corrected (uncalled bet behavior)
- [ ] Fixture mapping table updated as 11–31 fixtures ported (Phase 2/3)
- [ ] No duplicate tests against Already Covered list
