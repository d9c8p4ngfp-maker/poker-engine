package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class GameEngineTest {

    private GamePlayerState p(String id, int chips) {
        return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, List.of());
    }

    @Test
    void shouldStartHand() {
        var players = List.of(p("A", 1000), p("B", 1000), p("C", 1000));
        var config = RoomConfig.withDefaults();

        var result = GameEngine.startHand(players, 0, config);

        assertEquals(GamePhase.PRE_FLOP, result.state().phase());
        assertTrue(result.events().contains("BLINDS_POSTED"));
        assertTrue(result.events().contains("CARDS_DEALT"));
    }

    @Test
    void shouldProcessFoldAction() {
        var players = List.of(p("A", 1000), p("B", 1000), p("C", 1000));
        var config = RoomConfig.withDefaults();
        var startResult = GameEngine.startHand(players, 0, config);

        // A folds at UTG
        var result = GameEngine.processAction(startResult.state(), GameAction.FOLD, 0);
        assertTrue(result.events().contains("PLAYER_FOLDED"));
        assertFalse(result.handComplete());
    }

    @Test
    void shouldRejectCheckWhenMustCall() {
        var players = List.of(p("A", 1000), p("B", 1000), p("C", 1000));
        var config = RoomConfig.withDefaults();
        var startResult = GameEngine.startHand(players, 0, config);

        // At preflop, currentBet=20. UTG has roundBet=0, can't check.
        assertThrows(IllegalArgumentException.class, () ->
            GameEngine.processAction(startResult.state(), GameAction.CHECK, 0));
    }

    @Test
    void shouldCompletePreFlopBettingRoundWithEveryoneCalling() {
        var players = List.of(p("A", 1000), p("B", 1000), p("C", 1000));
        var config = RoomConfig.withDefaults();
        var result = GameEngine.startHand(players, 0, config);
        var state = result.state();

        // A (UTG) calls 20
        var r1 = GameEngine.processAction(state, GameAction.CALL, 0);
        state = r1.state();
        assertEquals(GamePhase.PRE_FLOP, state.phase());
        assertEquals(1, state.currentPlayerIndex()); // now B (SB) acts

        // B (SB) calls 10 more (already posted 10)
        var r2 = GameEngine.processAction(state, GameAction.CALL, 0);
        state = r2.state();
        assertEquals(2, state.currentPlayerIndex()); // now C (BB) acts

        // C (BB) checks (already posted 20)
        var r3 = GameEngine.processAction(state, GameAction.CHECK, 0);
        state = r3.state();

        // Round should be complete, phase advanced to FLOP
        assertEquals(GamePhase.FLOP, state.phase());
        assertTrue(r3.events().contains("PHASE_ADVANCED"));
        assertEquals(3, state.communityCards().size());
    }

    @Test
    void shouldCompleteFullHandToShowdown() {
        var players = List.of(p("A", 1000), p("B", 1000), p("C", 1000));
        var config = RoomConfig.withDefaults();
        var result = GameEngine.startHand(players, 0, config);
        var state = result.state();

        // Preflop: A calls, B calls, C checks (all match BB of 20)
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.FLOP, state.phase());

        // Flop: A bets, B & C call
        state = GameEngine.processAction(state, GameAction.BET, 20).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        assertEquals(GamePhase.TURN, state.phase());

        // Turn: all check
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.RIVER, state.phase());

        // River: all check
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        var finalResult = GameEngine.processAction(state, GameAction.CHECK, 0);

        assertEquals(GamePhase.SHOWDOWN, finalResult.state().phase());
        assertTrue(finalResult.handComplete());
    }
}
