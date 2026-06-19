package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ActionValidatorTest {

    private GamePlayerState p(String id, int chips, int totalBet, int roundBet) {
        return new GamePlayerState(id, id, 0, chips, totalBet, roundBet, false, false, List.of());
    }

    private GamePlayerState pFolded(String id) {
        return new GamePlayerState(id, id, 0, 1000, 0, 0, true, false, List.of());
    }

    private GameState state(GamePhase phase, List<GamePlayerState> players, int currentBet) {
        var deck = new Deck();
        return new GameState(phase, players, List.of(), 100, currentBet,
            20, 0, 0, 10, 20, deck, 0, -1);
    }

    @Test
    void shouldAllowFold() {
        var players = List.of(p("A", 1000, 20, 20));
        var s = state(GamePhase.PRE_FLOP, players, 20);
        var actions = ActionValidator.legalActions(s);
        assertTrue(actions.contains(GameAction.FOLD));
    }

    @Test
    void shouldAllowCheckWhenNoBetToMatch() {
        var players = List.of(p("A", 1000, 0, 0));
        var s = state(GamePhase.FLOP, players, 0);
        var actions = ActionValidator.legalActions(s);
        assertTrue(actions.contains(GameAction.CHECK));
        assertFalse(actions.contains(GameAction.CALL));
    }

    @Test
    void shouldAllowCallWhenBehind() {
        var players = List.of(p("A", 1000, 20, 10));
        var s = state(GamePhase.FLOP, players, 20);
        var actions = ActionValidator.legalActions(s);
        assertTrue(actions.contains(GameAction.CALL));
        assertFalse(actions.contains(GameAction.CHECK));
    }

    @Test
    void shouldAllowBetWhenNoPreviousBet() {
        var players = List.of(p("A", 1000, 0, 0));
        var s = state(GamePhase.FLOP, players, 0);
        var actions = ActionValidator.legalActions(s);
        assertTrue(actions.contains(GameAction.BET));
    }

    @Test
    void shouldAllowRaiseWhenThereIsPreviousBet() {
        var players = List.of(p("A", 1000, 20, 0));
        var s = state(GamePhase.FLOP, players, 20);
        var actions = ActionValidator.legalActions(s);
        assertTrue(actions.contains(GameAction.RAISE));
        assertTrue(actions.contains(GameAction.CALL));
        assertTrue(actions.contains(GameAction.FOLD));
    }

    @Test
    void shouldDisallowActionsForFoldedPlayer() {
        var players = List.of(pFolded("A"));
        var s = state(GamePhase.FLOP, players, 0);
        var actions = ActionValidator.legalActions(s);
        assertEquals(0, actions.size());
    }

    @Test
    void shouldDisallowBetLargerThanChips() {
        var players = List.of(p("A", 50, 0, 0));
        var s = state(GamePhase.FLOP, players, 0);
        var actions = ActionValidator.legalActions(s);
        // BET is allowed, but validate with amount will cap it
        assertTrue(actions.contains(GameAction.BET));
        assertDoesNotThrow(() -> ActionValidator.validate(s, GameAction.BET, 50));
        assertThrows(IllegalArgumentException.class, () -> ActionValidator.validate(s, GameAction.BET, 100));
    }
}
