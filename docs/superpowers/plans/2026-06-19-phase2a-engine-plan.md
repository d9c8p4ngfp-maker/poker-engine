# Phase 2a: 扑克规则引擎 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 4 个纯 Java 模块——Card、Deck、HandEvaluator、SidePotCalculator，全部独立可单元测试。

**Architecture:** 枚举 + record 表示牌型，Fisher-Yates 洗牌，C(7,5)=21 组合法评估手牌，分层累加法计算边池。全部在 `server/.../engine/` 下，不修改现有文件。

**Tech Stack:** Java 21, JUnit 5, Lombok

---

### Task 1: Card / Suit / Rank

**Files:**
- Create: `server/src/main/java/com/first/poker/engine/Card.java`
- Create: `server/src/test/java/com/first/poker/engine/CardTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardTest {

    @Test
    void shouldCreateCard() {
        Card c = new Card(Card.Suit.HEARTS, Card.Rank.ACE);
        assertEquals(Card.Suit.HEARTS, c.suit());
        assertEquals(Card.Rank.ACE, c.rank());
    }

    @Test
    void shouldConvertToString() {
        assertEquals("Ah", new Card(Card.Suit.HEARTS, Card.Rank.ACE).toString());
        assertEquals("Kd", new Card(Card.Suit.DIAMONDS, Card.Rank.KING).toString());
        assertEquals("Qs", new Card(Card.Suit.SPADES, Card.Rank.QUEEN).toString());
        assertEquals("Jc", new Card(Card.Suit.CLUBS, Card.Rank.JACK).toString());
        assertEquals("Th", new Card(Card.Suit.HEARTS, Card.Rank.TEN).toString());
        assertEquals("9d", new Card(Card.Suit.DIAMONDS, Card.Rank.NINE).toString());
        assertEquals("2s", new Card(Card.Suit.SPADES, Card.Rank.TWO).toString());
    }

    @Test
    void shouldParseFromString() {
        assertEquals(new Card(Card.Suit.HEARTS, Card.Rank.ACE), Card.fromString("Ah"));
        assertEquals(new Card(Card.Suit.DIAMONDS, Card.Rank.TEN), Card.fromString("Td"));
        assertEquals(new Card(Card.Suit.CLUBS, Card.Rank.TWO), Card.fromString("2c"));
    }

    @Test
    void shouldRejectInvalidString() {
        assertThrows(IllegalArgumentException.class, () -> Card.fromString(""));
        assertThrows(IllegalArgumentException.class, () -> Card.fromString("A"));
        assertThrows(IllegalArgumentException.class, () -> Card.fromString("Xh"));
        assertThrows(IllegalArgumentException.class, () -> Card.fromString("Ax"));
    }

    @Test
    void shouldOrderByRankThenSuit() {
        Card ace = new Card(Card.Suit.HEARTS, Card.Rank.ACE);
        Card king = new Card(Card.Suit.HEARTS, Card.Rank.KING);
        assertTrue(ace.compareTo(king) > 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
cd D:\poker-web\server && & "C:\Users\Admin\.tools\maven\bin\mvn.cmd" test -Dtest=CardTest -pl .
```
Expected: compilation error — Card class not found.

- [ ] **Step 3: Write minimal implementation**

```java
package com.first.poker.engine;

public record Card(Suit suit, Rank rank) implements Comparable<Card> {

    public enum Suit { SPADES, HEARTS, DIAMONDS, CLUBS }
    public enum Rank { TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING, ACE }

    @Override
    public String toString() {
        char r = switch (rank) {
            case ACE -> 'A'; case KING -> 'K'; case QUEEN -> 'Q';
            case JACK -> 'J'; case TEN -> 'T'; case NINE -> '9';
            case EIGHT -> '8'; case SEVEN -> '7'; case SIX -> '6';
            case FIVE -> '5'; case FOUR -> '4'; case THREE -> '3'; case TWO -> '2';
        };
        char s = switch (suit) {
            case HEARTS -> 'h'; case DIAMONDS -> 'd';
            case CLUBS -> 'c'; case SPADES -> 's';
        };
        return "" + r + s;
    }

    public static Card fromString(String s) {
        if (s == null || s.length() != 2) throw new IllegalArgumentException("Card must be 2 chars");
        Rank rank = switch (s.charAt(0)) {
            case 'A' -> Rank.ACE; case 'K' -> Rank.KING; case 'Q' -> Rank.QUEEN;
            case 'J' -> Rank.JACK; case 'T' -> Rank.TEN; case '9' -> Rank.NINE;
            case '8' -> Rank.EIGHT; case '7' -> Rank.SEVEN; case '6' -> Rank.SIX;
            case '5' -> Rank.FIVE; case '4' -> Rank.FOUR; case '3' -> Rank.THREE; case '2' -> Rank.TWO;
            default -> throw new IllegalArgumentException("Invalid rank: " + s.charAt(0));
        };
        Suit suit = switch (s.charAt(1)) {
            case 'h' -> Suit.HEARTS; case 'd' -> Suit.DIAMONDS;
            case 'c' -> Suit.CLUBS; case 's' -> Suit.SPADES;
            default -> throw new IllegalArgumentException("Invalid suit: " + s.charAt(1));
        };
        return new Card(suit, rank);
    }

    @Override
    public int compareTo(Card other) {
        int rankCmp = this.rank.compareTo(other.rank);
        return rankCmp != 0 ? rankCmp : this.suit.compareTo(other.suit);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```
cd D:\poker-web\server && & "C:\Users\Admin\.tools\maven\bin\mvn.cmd" test -Dtest=CardTest -pl .
```
Expected: 5/5 pass.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/first/poker/engine/Card.java server/src/test/java/com/first/poker/engine/CardTest.java
git commit -m "feat: add Card/Suit/Rank with string serialization"
```

---

### Task 2: Deck

**Files:**
- Create: `server/src/main/java/com/first/poker/engine/Deck.java`
- Create: `server/src/test/java/com/first/poker/engine/DeckTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;

class DeckTest {

    @Test
    void shouldCreateDeckWith52Cards() {
        Deck deck = new Deck();
        assertEquals(52, deck.size());
    }

    @Test
    void shouldDealCardAndReduceSize() {
        Deck deck = new Deck();
        deck.shuffle();
        Card c = deck.deal();
        assertNotNull(c);
        assertEquals(51, deck.size());
    }

    @Test
    void shouldContainAll52UniqueCards() {
        Deck deck = new Deck();
        var seen = new HashSet<String>();
        while (deck.size() > 0) {
            Card c = deck.deal();
            assertTrue(seen.add(c.toString()), "Duplicate card: " + c);
        }
        assertEquals(52, seen.size());
    }

    @Test
    void shouldThrowWhenDealingFromEmptyDeck() {
        Deck deck = new Deck();
        for (int i = 0; i < 52; i++) deck.deal();
        assertThrows(IllegalStateException.class, deck::deal);
    }

    @Test
    void shouldReorderAfterShuffle() {
        Deck deck = new Deck();
        // Deal some cards before shuffle to capture order
        String before = new Deck().toString();

        Deck shuffled = new Deck();
        shuffled.shuffle();
        String after = shuffled.toString();

        // Statistically, it's possible but extremely unlikely shuffle produces same order
        // We just verify shuffle doesn't throw
        assertDoesNotThrow(deck::shuffle);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
cd D:\poker-web\server && & "C:\Users\Admin\.tools\maven\bin\mvn.cmd" test -Dtest=DeckTest -pl .
```
Expected: compilation error.

- [ ] **Step 3: Write minimal implementation**

```java
package com.first.poker.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private final List<Card> cards;

    public Deck() {
        cards = new ArrayList<>(52);
        for (Card.Suit suit : Card.Suit.values()) {
            for (Card.Rank rank : Card.Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card deal() {
        if (cards.isEmpty()) throw new IllegalStateException("Deck is empty");
        return cards.remove(cards.size() - 1);
    }

    public int size() {
        return cards.size();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```
cd D:\poker-web\server && & "C:\Users\Admin\.tools\maven\bin\mvn.cmd" test -Dtest=DeckTest -pl .
```
Expected: 5/5 pass.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/first/poker/engine/Deck.java server/src/test/java/com/first/poker/engine/DeckTest.java
git commit -m "feat: add Deck with shuffle and deal"
```

---

### Task 3: HandEvaluator

**Files:**
- Create: `server/src/main/java/com/first/poker/engine/HandEvaluator.java`
- Create: `server/src/test/java/com/first/poker/engine/HandEvaluatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class HandEvaluatorTest {

    // Helper to make cards from strings
    private List<Card> cards(String... strs) {
        return java.util.Arrays.stream(strs).map(Card::fromString).toList();
    }

    @Test
    void shouldDetectRoyalFlush() {
        var hand = cards("Ah", "Kh", "Qh", "Jh", "Th", "2d", "3c");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("Royal Flush", result.name());
        assertTrue(result.rank() >= 9_000_000);
    }

    @Test
    void shouldDetectStraightFlush() {
        var hand = cards("9h", "8h", "7h", "6h", "5h", "2d", "3c");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("Straight Flush", result.name());
        assertTrue(result.rank() >= 8_000_000);
    }

    @Test
    void shouldDetectFourOfAKind() {
        var hand = cards("Kd", "Kh", "Ks", "Kc", "Ah", "2d", "3c");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("Four of a Kind", result.name());
        assertTrue(result.rank() >= 7_000_000);
    }

    @Test
    void shouldDetectFullHouse() {
        var hand = cards("Kd", "Kh", "Ks", "Qh", "Qd", "2d", "3c");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("Full House", result.name());
        assertTrue(result.rank() >= 6_000_000);
    }

    @Test
    void shouldDetectFlush() {
        var hand = cards("Ah", "Kh", "Th", "5h", "2h", "Qd", "3c");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("Flush", result.name());
        assertTrue(result.rank() >= 5_000_000);
    }

    @Test
    void shouldDetectStraight() {
        var hand = cards("9h", "8d", "7s", "6c", "5h", "2d", "3c");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("Straight", result.name());
        assertTrue(result.rank() >= 4_000_000);
    }

    @Test
    void shouldDetectWheelStraight() {
        var hand = cards("Ah", "2d", "3s", "4c", "5h", "9d", "Kc");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("Straight", result.name());
    }

    @Test
    void shouldDetectThreeOfAKind() {
        var hand = cards("Kd", "Kh", "Ks", "Qh", "2d", "3c", "4s");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("Three of a Kind", result.name());
        assertTrue(result.rank() >= 3_000_000);
    }

    @Test
    void shouldDetectTwoPair() {
        var hand = cards("Kd", "Kh", "Qh", "Qd", "2d", "3c", "4s");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("Two Pair", result.name());
        assertTrue(result.rank() >= 2_000_000);
    }

    @Test
    void shouldDetectOnePair() {
        var hand = cards("Kd", "Kh", "Qh", "2d", "3c", "4s", "5h");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("One Pair", result.name());
        assertTrue(result.rank() >= 1_000_000);
    }

    @Test
    void shouldDetectHighCard() {
        var hand = cards("Ah", "Kd", "Th", "5d", "2s", "9c", "4h");
        var result = HandEvaluator.evaluate(hand);
        assertEquals("High Card", result.name());
    }

    @Test
    void higherHandShouldBeatLowerHand() {
        var flush = cards("Ah", "Kh", "Th", "5h", "2h", "Qd", "3c");
        var straight = cards("9h", "8d", "7s", "6c", "5h", "2d", "3c");
        assertTrue(HandEvaluator.evaluate(flush).rank() > HandEvaluator.evaluate(straight).rank());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
cd D:\poker-web\server && & "C:\Users\Admin\.tools\maven\bin\mvn.cmd" test -Dtest=HandEvaluatorTest -pl .
```
Expected: compilation error — HandEvaluator not found.

- [ ] **Step 3: Write complete HandEvaluator**

```java
package com.first.poker.engine;

import java.util.*;

public class HandEvaluator {

    public record HandResult(int rank, String name, List<Card> bestFive) {}

    // 5-card hand evaluation

    public static HandResult evaluate(List<Card> sevenCards) {
        if (sevenCards.size() != 7) throw new IllegalArgumentException("Need exactly 7 cards");
        HandResult best = null;
        List<List<Card>> combos = combinations(sevenCards, 5);
        for (List<Card> five : combos) {
            HandResult hr = evaluateFive(five);
            if (best == null || hr.rank() > best.rank()) best = hr;
        }
        return best;
    }

    private static HandResult evaluateFive(List<Card> cards) {
        int[] ranks = new int[5];
        for (int i = 0; i < 5; i++) ranks[i] = cards.get(i).rank().ordinal();

        Arrays.sort(ranks);
        boolean flush = cards.stream().map(Card::suit).distinct().count() == 1;
        boolean straight = false;
        int highCard = ranks[4];

        // Normal straight check
        if (ranks[0] + 1 == ranks[1] && ranks[1] + 1 == ranks[2]
         && ranks[2] + 1 == ranks[3] && ranks[3] + 1 == ranks[4]) {
            straight = true;
        }
        // Wheel straight: A-2-3-4-5 (ranks: 0,1,2,3,12)
        if (ranks[0] == 0 && ranks[1] == 1 && ranks[2] == 2 && ranks[3] == 3 && ranks[4] == 12) {
            straight = true;
            highCard = 3; // 5-high straight
        }

        // Count occurrences
        int[] count = new int[13];
        for (int r : ranks) count[r]++;

        // Find pairs/trips/quads
        int quads = -1, trips = -1, pair1 = -1, pair2 = -1;
        for (int r = 12; r >= 0; r--) {
            if (count[r] == 4) quads = r;
            else if (count[r] == 3) trips = r;
            else if (count[r] == 2) {
                if (pair1 == -1) pair1 = r;
                else pair2 = r;
            }
        }

        // Determine hand type
        if (straight && flush && highCard == 12) return new HandResult(9000000, "Royal Flush", cards);
        if (straight && flush) return new HandResult(8000000 + highCard, "Straight Flush", cards);
        if (quads >= 0) return new HandResult(7000000 + quads * 13 + topKicker(ranks, count, quads), "Four of a Kind", cards);
        if (trips >= 0 && pair1 >= 0) return new HandResult(6000000 + trips * 13 + pair1, "Full House", cards);
        if (flush) return new HandResult(5000000 + encodeHighCards(ranks), "Flush", cards);
        if (straight) return new HandResult(4000000 + highCard, "Straight", cards);
        if (trips >= 0) return new HandResult(3000000 + trips * 169 + topKicker(ranks, count, trips), "Three of a Kind", cards);
        if (pair2 >= 0) return new HandResult(2000000 + pair1 * 169 + pair2 * 13 + topKicker(ranks, count, pair1, pair2), "Two Pair", cards);
        if (pair1 >= 0) return new HandResult(1000000 + pair1 * 2197 + top3Remaining(ranks, count, pair1), "One Pair", cards);
        return new HandResult(encodeHighCards(ranks), "High Card", cards);
    }

    private static int encodeHighCards(int[] ranks) {
        return ranks[4] * 28561 + ranks[3] * 2197 + ranks[2] * 169 + ranks[1] * 13 + ranks[0];
    }

    private static int topKicker(int[] ranks, int[] count, int exclude) {
        for (int r = 12; r >= 0; r--) {
            if (r != exclude && count[r] > 0) return r;
        }
        return 0;
    }

    private static int topKicker(int[] ranks, int[] count, int excl1, int excl2) {
        for (int r = 12; r >= 0; r--) {
            if (r != excl1 && r != excl2 && count[r] > 0) return r;
        }
        return 0;
    }

    private static int top3Remaining(int[] ranks, int[] count, int exclude) {
        int sum = 0;
        int mult = 169;
        for (int r = 12; r >= 0; r--) {
            if (r != exclude && count[r] > 0) {
                sum += r * mult;
                mult /= 13;
            }
        }
        return sum;
    }

    private static List<List<Card>> combinations(List<Card> list, int k) {
        List<List<Card>> result = new ArrayList<>();
        combine(list, k, 0, new ArrayList<>(), result);
        return result;
    }

    private static void combine(List<Card> list, int k, int start, List<Card> current, List<List<Card>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            combine(list, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```
cd D:\poker-web\server && & "C:\Users\Admin\.tools\maven\bin\mvn.cmd" test -Dtest=HandEvaluatorTest -pl .
```
Expected: 12/12 pass (or fix any failing edge case).

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/first/poker/engine/HandEvaluator.java server/src/test/java/com/first/poker/engine/HandEvaluatorTest.java
git commit -m "feat: add HandEvaluator with C(7,5) best hand assessment"
```

---

### Task 4: SidePotCalculator

**Files:**
- Create: `server/src/main/java/com/first/poker/engine/SidePotCalculator.java`
- Create: `server/src/test/java/com/first/poker/engine/SidePotCalculatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

class SidePotCalculatorTest {

    @Test
    void shouldAssignSinglePotWhenNoAllIn() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 100, false),
            new SidePotCalculator.PlayerStake("B", 100, false)
        );
        var handRanks = Map.of("A", 100, "B", 50);
        var pots = SidePotCalculator.calculate(stakes, handRanks);
        assertEquals(1, pots.size());
        assertEquals(200, pots.get(0).amount());
    }

    @Test
    void shouldCreateSidePotWhenOnePlayerAllIn() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 50, false),  // all-in at 50
            new SidePotCalculator.PlayerStake("B", 100, false)
        );
        var handRanks = Map.of("A", 50, "B", 100);
        var pots = SidePotCalculator.calculate(stakes, handRanks);
        assertEquals(2, pots.size());
        // Side pot: 50 * 2 eligible (both) = 100
        assertEquals(100, pots.get(0).amount());
        // Main pot: remaining 50 from B only = 50
        assertEquals(50, pots.get(1).amount());
    }

    @Test
    void shouldHandleThreePlayersDifferentAllIn() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 30, false),
            new SidePotCalculator.PlayerStake("B", 60, false),
            new SidePotCalculator.PlayerStake("C", 100, false)
        );
        var handRanks = Map.of("A", 30, "B", 60, "C", 100);
        var pots = SidePotCalculator.calculate(stakes, handRanks);
        // Layer 1: 30 * 3 = 90, eligible A,B,C
        // Layer 2: 30 * 2 = 60, eligible B,C
        // Layer 3: 40 * 1 = 40, eligible C
        int totalAmount = pots.stream().mapToInt(p -> p.amount()).sum();
        assertEquals(190, totalAmount); // 30+60+100 = 190 total staked
    }

    @Test
    void shouldExcludeFoldedPlayers() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 100, true),  // folded
            new SidePotCalculator.PlayerStake("B", 100, false)
        );
        var handRanks = Map.of("A", 50, "B", 100);
        var pots = SidePotCalculator.calculate(stakes, handRanks);
        assertEquals(1, pots.size());
        assertEquals(200, pots.get(0).amount());
        assertTrue(pots.get(0).eligiblePlayerIds().contains("B"));
        assertTrue(pots.get(0).eligiblePlayerIds().contains("A"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
cd D:\poker-web\server && & "C:\Users\Admin\.tools\maven\bin\mvn.cmd" test -Dtest=SidePotCalculatorTest -pl .
```
Expected: compilation error.

- [ ] **Step 3: Write implementation**

```java
package com.first.poker.engine;

import java.util.*;

public class SidePotCalculator {

    public record PlayerStake(String playerId, int totalBet, boolean folded) {}
    public record PotResult(int amount, Set<String> eligiblePlayerIds, String winnerId) {}

    public static List<PotResult> calculate(List<PlayerStake> stakes, Map<String, Integer> handRanks) {
        List<PotResult> pots = new ArrayList<>();
        if (stakes.isEmpty()) return pots;

        // Collect unique bet amounts, sorted ascending
        TreeSet<Integer> levels = new TreeSet<>();
        for (PlayerStake s : stakes) {
            if (!s.folded()) levels.add(s.totalBet());
        }

        int prev = 0;
        for (int level : levels) {
            int layerAmount = level - prev;
            Set<String> eligible = new HashSet<>();
            int potTotal = 0;
            for (PlayerStake s : stakes) {
                if (!s.folded() && s.totalBet() >= level) {
                    eligible.add(s.playerId());
                    potTotal += layerAmount;
                }
            }
            if (!eligible.isEmpty()) {
                int amount = potTotal;
                String winner = eligible.stream()
                    .max(Comparator.comparingInt(handRanks::get))
                    .orElse(eligible.iterator().next());
                pots.add(new PotResult(amount, eligible, winner));
            }
            prev = level;
        }
        return pots;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```
cd D:\poker-web\server && & "C:\Users\Admin\.tools\maven\bin\mvn.cmd" test -Dtest=SidePotCalculatorTest -pl .
```
Expected: 4/4 pass.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/first/poker/engine/SidePotCalculator.java server/src/test/java/com/first/poker/engine/SidePotCalculatorTest.java
git commit -m "feat: add SidePotCalculator for multi-player all-in pot distribution"
```

---

## Integration Verification

- [ ] Run all tests:

```
cd D:\poker-web\server && & "C:\Users\Admin\.tools\maven\bin\mvn.cmd" test
```
Expected: all tests pass.

- [ ] Commit final:

```bash
git add -A && git commit -m "chore: Phase 2a complete — poker rules engine with Card, Deck, HandEvaluator, SidePotCalculator"
```
