# Phase 2a: 扑克规则引擎 — Spec

> 日期: 2026-06-19 | 状态: 已确认 | 父方案: `docs/planning/0619/德州扑克-方案设计.md`

## 1. 目标

实现服务器端权威的德州扑克规则引擎。4 个纯 Java 模块，零依赖 Room/Player/WebSocket，全部可独立单元测试。

## 2. 已确认决策

| 项 | 决策 |
|----|------|
| 牌型表示 | `enum Suit` + `enum Rank` + `record Card`，序列化时转两字符字符串 `"Ah"` |
| 手牌评估算法 | 组合法 C(7,5)=21：先评估 5 张牌型，再从 21 种组合中选最优 |
| Deck 策略 | 每手 `new Deck()` → `shuffle()` → 逐个 `deal()`，不复用 |
| 烧牌 | MVP 不做 |
| 模块位置 | `server/.../engine/`，不修改现有文件 |
| 依赖关系 | Card → Deck → HandEvaluator → SidePotCalculator |

## 3. 模块设计

### 3.1 Card / Suit / Rank

```
Suit:  SPADES, HEARTS, DIAMONDS, CLUBS
Rank:  TWO(2), THREE(3), ..., KING(13), ACE(14)

Card(Suit suit, Rank rank)
  - toString(): "Ah", "Kd", "Ts"
  - fromString("Ah"): Card
```

**序列化规则**: 第一个字符 rank（`A K Q J T 9..2`），第二个字符 suit（`h d c s`）。ACE 是 `A`，TEN 是 `T`。

### 3.2 Deck

```
Deck()
  - 内部: 52 张 Card 列表
  - shuffle(): Fisher-Yates 洗牌
  - deal(): 返回并移除牌堆顶一张牌
  - size(): 剩余牌数

Deck deck = new Deck();
deck.shuffle();
Card c1 = deck.deal(); // 第一张
Card c2 = deck.deal(); // 第二张
```

### 3.3 HandEvaluator

**接口**:
```java
HandResult evaluate(List<Card> sevenCards)
// → { rank: int, name: String, bestFive: List<Card> }
```

**算法**: 生成 C(7,5)=21 种组合 → 对每个 5 张组合判定牌型 → 返回 rank 值最优的。

**10 种牌型及 rank 范围**（越大越强）:

| # | 牌型 | rank 起 | 示例 |
|---|------|---------|------|
| 1 | High Card | 0 | K-high |
| 2 | One Pair | 1,000,000 | KK |
| 3 | Two Pair | 2,000,000 | KK 88 |
| 4 | Three of a Kind | 3,000,000 | KKK |
| 5 | Straight | 4,000,000 | 9-K 顺子 |
| 6 | Flush | 5,000,000 | 红桃 K-high |
| 7 | Full House | 6,000,000 | KKK 88 |
| 8 | Four of a Kind | 7,000,000 | KKKK |
| 9 | Straight Flush | 8,000,000 | 同花 9-K |
| 10 | Royal Flush | 9,000,000 | 同花 10-A |

同级别用 kicker 比大小。

### 3.4 SidePotCalculator

**接口**:
```java
List<PotResult> calculate(List<PlayerStake> stakes, Map<String, Integer> handRanks)
// PlayerStake: { playerId, totalBet, folded }
// PotResult: { amount, winnerPlayerIds[] }
```

**算法**（按方案文档第 8 节）:
1. 收集所有未弃牌玩家的总下注额，去重排序
2. 从最小层开始，计算该层每位玩家贡献的金额 → 形成一个 pot
3. 该 pot 的有资格竞争者 = 未弃牌且下注额 >= 该层的玩家
4. 由有资格者中 handRank 最高者赢得该 pot

## 4. 文件结构

```
server/src/main/java/com/first/poker/engine/
  Card.java                [NEW] Suit/Rank 枚举 + Card record
  Deck.java                [NEW] 52 张牌 + shuffle + deal
  HandEvaluator.java       [NEW] C(7,5) 最佳手牌评估
  SidePotCalculator.java   [NEW] 边池分配

server/src/test/java/com/first/poker/engine/
  CardTest.java
  DeckTest.java
  HandEvaluatorTest.java
  SidePotCalculatorTest.java
```

## 5. 边界条件覆盖

- **Card**: fromString 非法输入抛异常、A high vs A low（顺子中 A 可当 1 用）
- **Deck**: 发完 52 张后再 deal 抛异常、shuffle 后顺序与排序前不同（统计性测试）
- **HandEvaluator**: 10 种牌型各一例、7 张同花色选最大 5 张、有对又有顺选更优牌型
- **SidePot**: 2 人不同 allin 金额、3 人 3 个层级、无人 allin 全部进主池、固定盲注下小盲大盲各下注不同

## 6. 不计入

- 不修改 Room/Player/任何现有文件
- 不接入 WebSocket 或 REST
- 不做烧牌
- 不实现查表法
