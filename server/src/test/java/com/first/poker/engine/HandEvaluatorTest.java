package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class HandEvaluatorTest {

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

    @Test
    void wheelRanksBelowSixHighStraight() {
        var wheel = cards("Ah", "2d", "3s", "4c", "5h", "9d", "Kc");
        var sixHigh = cards("6h", "5d", "4s", "3c", "2h", "9d", "Kc");
        int wheelRank = HandEvaluator.evaluate(wheel).rank();
        int sixHighRank = HandEvaluator.evaluate(sixHigh).rank();
        assertTrue(wheelRank < sixHighRank,
            "Wheel (A-2-3-4-5) should rank below 6-high straight (2-3-4-5-6), got " + wheelRank + " vs " + sixHighRank);
    }

    @Test
    void pairKickerDecides() {
        var highKicker = cards("Ah", "As", "Kh", "Qd", "2c", "5d", "3c");
        var lowKicker = cards("Ad", "Ac", "Jh", "Td", "2s", "5s", "3h");
        assertTrue(HandEvaluator.evaluate(highKicker).rank() > HandEvaluator.evaluate(lowKicker).rank(),
            "Pair of Aces with KQ kicker should beat Pair of Aces with JT kicker");
    }
}
