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

    private boolean contains(List<ActionValidator.LegalAction> actions, GameAction type) {
        return actions.stream().anyMatch(a -> a.type() == type);
    }

    @Test
    void shouldAllowFold() {
        var players = List.of(p("A", 1000, 20, 20));
        var s = state(GamePhase.PRE_FLOP, players, 20);
        var actions = ActionValidator.legalActions(s);
        assertTrue(contains(actions, GameAction.FOLD));
    }

    @Test
    void shouldAllowCheckWhenNoBetToMatch() {
        var players = List.of(p("A", 1000, 0, 0));
        var s = state(GamePhase.FLOP, players, 0);
        var actions = ActionValidator.legalActions(s);
        assertTrue(contains(actions, GameAction.CHECK));
        assertFalse(contains(actions, GameAction.CALL));
    }

    @Test
    void shouldAllowCallWhenBehind() {
        var players = List.of(p("A", 1000, 20, 10));
        var s = state(GamePhase.FLOP, players, 20);
        var actions = ActionValidator.legalActions(s);
        assertTrue(contains(actions, GameAction.CALL));
        assertFalse(contains(actions, GameAction.CHECK));
    }

    @Test
    void shouldAllowBetWhenNoPreviousBet() {
        var players = List.of(p("A", 1000, 0, 0));
        var s = state(GamePhase.FLOP, players, 0);
        var actions = ActionValidator.legalActions(s);
        assertTrue(contains(actions, GameAction.BET));
    }

    @Test
    void shouldAllowRaiseWhenThereIsPreviousBet() {
        var players = List.of(p("A", 1000, 20, 0));
        var s = state(GamePhase.FLOP, players, 20);
        var actions = ActionValidator.legalActions(s);
        assertTrue(contains(actions, GameAction.RAISE));
        assertTrue(contains(actions, GameAction.CALL));
        assertTrue(contains(actions, GameAction.FOLD));
    }

    @Test
    void shouldDisallowActionsForFoldedPlayer() {
        var players = List.of(pFolded("A"));
        var s = state(GamePhase.FLOP, players, 0);
        var actions = ActionValidator.legalActions(s);
        assertEquals(0, actions.size());
    }

    @Test
    void shouldAllowBetAllChipsAsAllIn() {
        var players = List.of(p("A", 50, 0, 0));
        var s = state(GamePhase.FLOP, players, 0);
        var actions = ActionValidator.legalActions(s);
        assertTrue(contains(actions, GameAction.BET));
        assertDoesNotThrow(() -> ActionValidator.validate(s, GameAction.BET, 50));
        assertDoesNotThrow(() -> ActionValidator.validate(s, GameAction.BET, 100));
    }

    @Test
    void zeroChipsPlayerOnlyFold() {
        var pAllIn = new GamePlayerState("Z", "Z", 0, 0, 0, 0, false, true, List.of());
        var players = List.of(pAllIn, p("B", 1000, 0, 0));
        var s = state(GamePhase.FLOP, players, 20);
        var actions = ActionValidator.legalActions(s);
        assertTrue(actions.isEmpty(), "All-in player with 0 chips should have no legal actions");
    }

    @Test
    void allInPlayerNotActive() {
        var pAllIn = new GamePlayerState("Z", "Z", 0, 0, 100, 100, false, true, List.of());
        var players = List.of(pAllIn, p("B", 1000, 0, 0));
        var s = state(GamePhase.FLOP, players, 40);
        var actions = ActionValidator.legalActions(s);
        assertTrue(actions.isEmpty(), "All-in player should have no legal actions");
    }

    @Test
    void minRaiseEnforced() {
        var players = List.of(p("A", 200, 40, 0));
        var s = state(GamePhase.FLOP, players, 40);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ActionValidator.validate(s, GameAction.RAISE, 50));
        assertTrue(ex.getMessage().contains("Raise must be at least"));
    }
}
