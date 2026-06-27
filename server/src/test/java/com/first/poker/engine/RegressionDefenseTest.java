package com.first.poker.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Regression defense: every bug fixed today gets a test here.
 * If any of these fail, a previously-fixed regression has returned.
 * Run before any refactor or new feature merge.
 */
class RegressionDefenseTest {

    // ──────────── Bug: Need exactly 7 cards crash ────────────
    // Fix: HandEvaluator returns null (not throws) when < 5 cards.
    // Cause: timeout-fold during flop → endHandFlow → checkAndApplyBonuses → resolveHands
    //        with only 3 community cards → crash.

    @Test
    void evaluateWith0Cards_ReturnsNull() {
        assertNull(HandEvaluator.evaluate(List.of()));
    }

    @Test
    void evaluateWith3Cards_ReturnsNull() {
        var cards = List.of(
            new Card(Card.Suit.SPADES, Card.Rank.ACE),
            new Card(Card.Suit.SPADES, Card.Rank.KING),
            new Card(Card.Suit.SPADES, Card.Rank.QUEEN)
        );
        assertNull(HandEvaluator.evaluate(cards));
    }

    @Test
    void evaluateWithNull_ReturnsNull() {
        assertNull(HandEvaluator.evaluate(null));
    }

    @Test
    void evaluateWith5Cards_Succeeds() {
        var cards = List.of(
            new Card(Card.Suit.SPADES, Card.Rank.ACE),
            new Card(Card.Suit.SPADES, Card.Rank.KING),
            new Card(Card.Suit.SPADES, Card.Rank.QUEEN),
            new Card(Card.Suit.SPADES, Card.Rank.JACK),
            new Card(Card.Suit.SPADES, Card.Rank.TEN)
        );
        var result = HandEvaluator.evaluate(cards);
        assertNotNull(result);
        assertEquals("Royal Flush", result.name());
    }

    @Test
    void evaluateWith7Cards_Succeeds() {
        var cards = List.of(
            new Card(Card.Suit.SPADES, Card.Rank.ACE),
            new Card(Card.Suit.SPADES, Card.Rank.KING),
            new Card(Card.Suit.SPADES, Card.Rank.QUEEN),
            new Card(Card.Suit.SPADES, Card.Rank.JACK),
            new Card(Card.Suit.SPADES, Card.Rank.TEN),
            new Card(Card.Suit.HEARTS, Card.Rank.TWO),
            new Card(Card.Suit.CLUBS, Card.Rank.THREE)
        );
        var result = HandEvaluator.evaluate(cards);
        assertNotNull(result);
        assertEquals("Royal Flush", result.name());
    }

    // ──────────── Bug: resolveHands with <5 community cards ────────────
    // Fix: resolveHands skips players where evaluatePlayerHand returns null.
    // Cause: early hand end before all streets dealt.

    @Test
    void resolveHandsWithIncompleteBoard_ReturnsEmpty() {
        var player = new GamePlayerState(
            "p1", "Player1", 0, 1000, 0, 0, false, false,
            List.of(
                new Card(Card.Suit.SPADES, Card.Rank.ACE),
                new Card(Card.Suit.SPADES, Card.Rank.KING)
            )
        );
        var communityCards = List.of(
            new Card(Card.Suit.SPADES, Card.Rank.QUEEN)
        );

        var results = HandResolver.resolveHands(List.of(player), communityCards);
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty map when cards insufficient");
    }

    @Test
    void resolveHandsWithFullBoard_ReturnsResult() {
        var player = new GamePlayerState(
            "p1", "Player1", 0, 1000, 0, 0, false, false,
            List.of(
                new Card(Card.Suit.SPADES, Card.Rank.ACE),
                new Card(Card.Suit.SPADES, Card.Rank.KING)
            )
        );
        var communityCards = List.of(
            new Card(Card.Suit.SPADES, Card.Rank.QUEEN),
            new Card(Card.Suit.SPADES, Card.Rank.JACK),
            new Card(Card.Suit.SPADES, Card.Rank.TEN),
            new Card(Card.Suit.HEARTS, Card.Rank.TWO),
            new Card(Card.Suit.CLUBS, Card.Rank.THREE)
        );

        var results = HandResolver.resolveHands(List.of(player), communityCards);
        assertEquals(1, results.size());
        assertNotNull(results.get("p1"));
    }

    // ──────────── Sanity: all size transitions from 0→7 ────────────
    @Test
    void allCardCountTransitionsAreSafe() {
        var suits = List.of(
            Card.Suit.SPADES, Card.Suit.HEARTS,
            Card.Suit.DIAMONDS, Card.Suit.CLUBS,
            Card.Suit.SPADES, Card.Suit.HEARTS,
            Card.Suit.DIAMONDS
        );
        var ranks = List.of(
            Card.Rank.ACE, Card.Rank.KING, Card.Rank.QUEEN,
            Card.Rank.JACK, Card.Rank.TEN, Card.Rank.TWO,
            Card.Rank.THREE
        );
        for (int n = 0; n <= 7; n++) {
            var cards = new ArrayList<Card>();
            for (int i = 0; i < n; i++) {
                cards.add(new Card(suits.get(i), ranks.get(i)));
            }
            if (n < 5) {
                assertNull(HandEvaluator.evaluate(cards),
                    "n=" + n + " should return null");
            } else {
                assertNotNull(HandEvaluator.evaluate(cards),
                    "n=" + n + " should not return null");
            }
        }
    }
}
