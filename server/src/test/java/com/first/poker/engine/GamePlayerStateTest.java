package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class GamePlayerStateTest {

    @Test
    void shouldCreatePlayerState() {
        var state = new GamePlayerState("p1", "Alice", 0, 1000, 0, 0, false, false, List.of());
        assertEquals("p1", state.playerId());
        assertEquals("Alice", state.nickname());
        assertEquals(0, state.seatIndex());
        assertEquals(1000, state.chips());
        assertEquals(0, state.totalBet());
        assertEquals(0, state.roundBet());
        assertFalse(state.folded());
        assertFalse(state.allIn());
        assertTrue(state.holeCards().isEmpty());
    }

    @Test
    void shouldCreateFromPlayerModel() {
        var player = new com.first.poker.model.Player("p1", "Alice", 0, 1000);
        var state = GamePlayerState.fromPlayer(player);
        assertEquals("p1", state.playerId());
        assertEquals("Alice", state.nickname());
        assertEquals(0, state.seatIndex());
        assertEquals(1000, state.chips());
        assertEquals(0, state.totalBet());
        assertFalse(state.folded());
        assertFalse(state.allIn());
    }

    @Test
    void shouldApplyChipsDeduction() {
        var state = new GamePlayerState("p1", "A", 0, 1000, 0, 0, false, false, List.of());
        var updated = state.withChipsDeducted(100);
        assertEquals(900, updated.chips());
        assertEquals(100, updated.totalBet());
        assertEquals(100, updated.roundBet());
        assertNotSame(state, updated);
    }

    @Test
    void shouldCapDeductionAtRemainingChips() {
        var state = new GamePlayerState("p1", "A", 0, 50, 0, 0, false, false, List.of());
        var updated = state.withChipsDeducted(100);
        assertEquals(0, updated.chips());
        assertEquals(50, updated.totalBet());
        assertTrue(updated.allIn());
    }

    @Test
    void shouldSetFolded() {
        var state = new GamePlayerState("p1", "A", 0, 1000, 0, 0, false, false, List.of());
        var updated = state.withFolded();
        assertTrue(updated.folded());
    }

    @Test
    void shouldSetHoleCards() {
        var state = new GamePlayerState("p1", "A", 0, 1000, 0, 0, false, false, List.of());
        var updated = state.withHoleCards(List.of(Card.fromString("Ah"), Card.fromString("Kh")));
        assertEquals(2, updated.holeCards().size());
        assertEquals("Ah", updated.holeCards().get(0).toString());
    }

    @Test
    void chipsNeverNegative() {
        var state = new GamePlayerState("p1", "A", 0, 30, 0, 0, false, false, List.of());
        var updated = state.withChipsDeducted(100);
        assertEquals(0, updated.chips(), "Chips should be capped at 0, not go negative");
    }
}
