package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class PotTrackingRegressionTest {

    private GamePlayerState player(String id, int chips) {
        return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, List.of());
    }

    private RoomConfig config(int sb) {
        RoomConfig c = RoomConfig.withDefaults();
        c.setSmallBlind(sb);
        return c;
    }

    @Test @DisplayName("Pot is non-zero after blinds posted")
    void potAfterBlindsPosted() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        assertEquals(30, state.pot(), "Pot should be SB(10)+BB(20)=30 after blinds posted");
    }

    @Test @DisplayName("Pot increases per action during preflop")
    void potIncreasesPerActionDuringRound() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        assertEquals(30, state.pot()); // blinds posted

        // pA(UTG) calls 20
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        assertEquals(50, state.pot(), "Pot should be 30+20=50 after pA calls");

        // pB(SB) calls 10 (already posted 10)
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        assertEquals(60, state.pot(), "Pot should be 50+10=60 after pB calls");

        // pC(BB) checks
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(60, state.pot(), "Pot unchanged after check");
        assertEquals(GamePhase.FLOP, state.phase());
    }

    @Test @DisplayName("Pot tracks bets and raises correctly")
    void potTracksBetsAndRaises() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();

        // preflop: all call → pot=60
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.FLOP, state.phase());
        assertEquals(60, state.pot());

        // FLOP: B bets 20, C calls, A calls
        state = GameEngine.processAction(state, GameAction.BET, 20).state();
        assertEquals(80, state.pot(), "Pot 60+20=80 after bet");

        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        assertEquals(100, state.pot(), "Pot 80+20=100 after call");

        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        assertEquals(120, state.pot(), "Pot 100+20=120 after call");
        assertEquals(GamePhase.TURN, state.phase());
    }
}
