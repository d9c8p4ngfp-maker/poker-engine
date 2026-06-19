package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

class HandResolverTest {

    @Test
    void shouldEvaluateHandForSinglePlayer() {
        var player = new GamePlayerState("A", "A", 0, 1000, 0, 0, false, false,
            List.of(Card.fromString("Ah"), Card.fromString("Kh")));
        var community = List.of(
            Card.fromString("Qh"), Card.fromString("Jh"), Card.fromString("Th"),
            Card.fromString("2d"), Card.fromString("3c")
        );

        var result = HandResolver.evaluatePlayerHand(player, community);
        assertEquals("Royal Flush", result.name());
    }

    @Test
    void shouldDetermineWinnerBetweenTwoPlayers() {
        var p1 = new GamePlayerState("A", "A", 0, 1000, 50, 50, false, false,
            List.of(Card.fromString("Ah"), Card.fromString("Ad")));
        var p2 = new GamePlayerState("B", "B", 1, 1000, 50, 50, false, false,
            List.of(Card.fromString("Kh"), Card.fromString("Kd")));

        var community = List.of(
            Card.fromString("As"), Card.fromString("Ac"), Card.fromString("2h"),
            Card.fromString("3d"), Card.fromString("4c")
        );

        var hands = HandResolver.resolveHands(List.of(p1, p2), community);
        assertTrue(hands.get("A").rank() > hands.get("B").rank());
    }

    @Test
    void shouldDetectTie() {
        var p1 = new GamePlayerState("A", "A", 0, 1000, 50, 50, false, false,
            List.of(Card.fromString("Ah"), Card.fromString("Kh")));
        var p2 = new GamePlayerState("B", "B", 1, 1000, 50, 50, false, false,
            List.of(Card.fromString("Ad"), Card.fromString("Kd")));

        var community = List.of(
            Card.fromString("2s"), Card.fromString("3c"), Card.fromString("4h"),
            Card.fromString("5d"), Card.fromString("6c")
        );

        var hands = HandResolver.resolveHands(List.of(p1, p2), community);
        assertEquals(hands.get("A").rank(), hands.get("B").rank());
    }

    @Test
    void shouldDistributeSinglePot() {
        var p1 = new GamePlayerState("A", "A", 0, 1000, 50, 50, false, false,
            List.of(Card.fromString("Ah"), Card.fromString("Ad")));
        var p2 = new GamePlayerState("B", "B", 1, 1000, 50, 50, false, false,
            List.of(Card.fromString("Kh"), Card.fromString("Kd")));

        var community = List.of(
            Card.fromString("As"), Card.fromString("Ac"), Card.fromString("2h"),
            Card.fromString("3d"), Card.fromString("4c")
        );

        var hands = HandResolver.resolveHands(List.of(p1, p2), community);
        var pots = HandResolver.distributePots(List.of(p1, p2), hands);

        assertEquals(1, pots.size());
        assertEquals(100, pots.get(0).amount());
        assertTrue(pots.get(0).eligiblePlayerIds().contains("A"));
    }
}
