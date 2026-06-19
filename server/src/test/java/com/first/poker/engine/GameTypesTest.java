package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameTypesTest {

    @Test
    void shouldHaveAllActions() {
        assertEquals(5, GameAction.values().length);
        assertNotNull(GameAction.valueOf("FOLD"));
        assertNotNull(GameAction.valueOf("CHECK"));
        assertNotNull(GameAction.valueOf("CALL"));
        assertNotNull(GameAction.valueOf("BET"));
        assertNotNull(GameAction.valueOf("RAISE"));
    }

    @Test
    void shouldHaveAllPhases() {
        assertEquals(6, GamePhase.values().length);
        assertNotNull(GamePhase.valueOf("PRE_FLOP"));
        assertNotNull(GamePhase.valueOf("FLOP"));
        assertNotNull(GamePhase.valueOf("TURN"));
        assertNotNull(GamePhase.valueOf("RIVER"));
        assertNotNull(GamePhase.valueOf("SHOWDOWN"));
        assertNotNull(GamePhase.valueOf("HAND_OVER"));
    }

    @Test
    void shouldOrderPhasesCorrectly() {
        assertTrue(GamePhase.PRE_FLOP.ordinal() < GamePhase.FLOP.ordinal());
        assertTrue(GamePhase.FLOP.ordinal() < GamePhase.TURN.ordinal());
        assertTrue(GamePhase.TURN.ordinal() < GamePhase.RIVER.ordinal());
        assertTrue(GamePhase.RIVER.ordinal() < GamePhase.SHOWDOWN.ordinal());
    }
}
