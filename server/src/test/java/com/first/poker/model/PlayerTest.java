package com.first.poker.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    @Test
    void shouldCreatePlayerWithDefaults() {
        Player p = new Player("p1", "Alice", 0, 1000);

        assertEquals("p1", p.getPlayerId());
        assertEquals("Alice", p.getNickname());
        assertEquals(0, p.getSeatIndex());
        assertEquals(1000, p.getChips());
        assertEquals(0, p.getBetInRound());
        assertFalse(p.isFolded());
        assertFalse(p.isAllIn());
        assertNull(p.getLastAction());
        assertTrue(p.isConnected());
    }

    @Test
    void shouldRecordActionAndReduceChips() {
        Player p = new Player("p2", "Bob", 1, 500);
        p.placeBet(100);
        assertEquals(400, p.getChips());
        assertEquals(100, p.getBetInRound());
    }

    @Test
    void shouldGoAllInWhenBetExceedsChips() {
        Player p = new Player("p3", "Charlie", 2, 50);
        p.placeBet(200);
        assertEquals(0, p.getChips());
        assertEquals(50, p.getBetInRound());
        assertTrue(p.isAllIn());
    }
}
