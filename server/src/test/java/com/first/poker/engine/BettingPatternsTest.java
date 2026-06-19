package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class BettingPatternsTest {

    private GamePlayerState player(String id, int chips) {
        return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, List.of());
    }

    private RoomConfig config(int sb) {
        RoomConfig c = RoomConfig.withDefaults();
        c.setSmallBlind(sb);
        return c;
    }

    @Test @DisplayName("Check-check to next street")
    void checkCheckToNextStreet() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // Preflop: call, call, check → FLOP
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.FLOP, state.phase());
        assertEquals(60, state.pot());
    }

    @Test @DisplayName("Bet-call-fold across 3 players")
    void betCallFoldAcrossPlayers() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // Preflop: all call
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.FLOP, state.phase());
        // Flop: first player bets
        state = GameEngine.processAction(state, GameAction.BET, 20).state();
        assertEquals(80, state.pot());
    }

    @Test @DisplayName("Raise and re-raise")
    void raiseAndReraise() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // Preflop: A raises to 40
        state = GameEngine.processAction(state, GameAction.RAISE, 40).state();
        assertEquals(40, state.currentBet());
        // B re-raises to 80
        state = GameEngine.processAction(state, GameAction.RAISE, 80).state();
        assertEquals(80, state.currentBet());
    }

    @Test @DisplayName("All check through 3 streets")
    void allCheckThroughThreeStreets() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // Preflop
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.FLOP, state.phase());
        // Flop
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.TURN, state.phase());
        // Turn
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.RIVER, state.phase());
        // River → showdown
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        var finalResult = GameEngine.processAction(state, GameAction.CHECK, 0);
        assertTrue(finalResult.handComplete());
        assertEquals(60, finalResult.state().pot());
    }

    @Test @DisplayName("Fold resets action to next player")
    void foldResetsToNextPlayer() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        assertEquals("A", state.currentPlayer().playerId());
        // A folds
        state = GameEngine.processAction(state, GameAction.FOLD, 0).state();
        assertEquals("B", state.currentPlayer().playerId(), "After A folds, B (SB) should act");
    }

    @Test @DisplayName("Min-raise amount enforced by processAction")
    void minRaiseEnforcedInProcessAction() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // A raises to 40
        state = GameEngine.processAction(state, GameAction.RAISE, 40).state();
        // B must raise at least minRaise (= BB = 20) more than currentBet(=40) → 60+
        try {
            GameEngine.processAction(state, GameAction.RAISE, 50);
            fail("Should throw for raise below minimum");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Raise must be at least"));
        }
    }
}
