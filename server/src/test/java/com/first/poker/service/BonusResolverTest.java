package com.first.poker.service;

import com.first.poker.engine.BonusResolver;
import com.first.poker.engine.Card;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BonusResolverTest {

    @Test
    void shouldDetect27Game() {
        List<Card> holeCards = List.of(
            Card.fromString("2h"),
            Card.fromString("7s")
        );
        var chips = Map.of("p1", 500, "p2", 800);

        var result = BonusResolver.check27Game("p1", holeCards, chips, 50);
        assertTrue(result.isPresent());
        assertEquals("27_GAME", result.get().type());
        assertEquals("p1", result.get().winnerId());
        assertTrue(result.get().transfers().containsKey("p2"));
    }

    @Test
    void shouldNotDetect27GameWithout2And7() {
        List<Card> holeCards = List.of(
            Card.fromString("Ah"),
            Card.fromString("Kh")
        );
        var chips = Map.of("p1", 500, "p2", 800);

        var result = BonusResolver.check27Game("p1", holeCards, chips, 50);
        assertFalse(result.isPresent());
    }

    @Test
    void shouldCapTransfersAtAvailableChips() {
        List<Card> holeCards = List.of(
            Card.fromString("2d"),
            Card.fromString("7c")
        );
        var chips = Map.of("p1", 1, "p2", 30);

        var result = BonusResolver.check27Game("p1", holeCards, chips, 100);
        assertTrue(result.isPresent());
        assertEquals(30, result.get().transfers().get("p2"));
    }

    @Test
    void emptyHandNoBonus() {
        assertFalse(BonusResolver.check27Game("p1", List.of(), Map.of(), 10).isPresent());
    }
}
