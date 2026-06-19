package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class BettingRoundSoleSurvivorTest {

    private GamePlayerState player(String id, int chips) {
        return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, List.of());
    }

    private RoomConfig config(int sb) {
        RoomConfig c = RoomConfig.withDefaults();
        c.setSmallBlind(sb);
        return c;
    }

    @Test @DisplayName("Sole survivor wins blinds without showdown")
    void soleSurvivorWinsBlindsAndPot() {
        // 3 players, pC is BB, pA and pB fold → pC wins pot=30
        var players = List.of(player("pA", 1000), player("pB", 1000), player("pC", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();

        // pA(UTG) folds
        assertEquals("pA", state.currentPlayer().playerId());
        var r1 = GameEngine.processAction(state, GameAction.FOLD, 0);
        state = r1.state();
        assertFalse(r1.handComplete());

        // pB(SB) folds → sole survivor pC
        assertEquals("pB", state.currentPlayer().playerId());
        var r2 = GameEngine.processAction(state, GameAction.FOLD, 0);

        assertTrue(r2.handComplete(), "Sole survivor should end hand");
        assertFalse(r2.winners().isEmpty(), "Should have a winner");
        assertEquals("pC", r2.winners().getFirst().playerId());
        assertEquals("Last Standing", r2.winners().getFirst().handName());

        // pC wins 30 (SB+BB from folded players): pC was BB, chips=980, wins 30 → 1010
        var pC = r2.state().players().get(2);
        assertEquals(1010, pC.chips(), "pC should win SB(10)+BB(20)=30");

        // Total chips conserved
        int total = r2.state().players().stream().mapToInt(GamePlayerState::chips).sum();
        assertEquals(3000, total);
    }

    @Test @DisplayName("Preflop raise, everyone folds")
    void preflopRaiseEveryoneFolds() {
        var players = List.of(player("pA", 1000), player("pB", 1000), player("pC", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();

        // pA(UTG) raises to 60
        assertEquals("pA", state.currentPlayer().playerId());
        state = GameEngine.processAction(state, GameAction.RAISE, 60).state();

        // pB(SB) folds
        assertEquals("pB", state.currentPlayer().playerId());
        state = GameEngine.processAction(state, GameAction.FOLD, 0).state();

        // pC(BB) folds → sole survivor pA
        assertEquals("pC", state.currentPlayer().playerId());
        var finalResult = GameEngine.processAction(state, GameAction.FOLD, 0);

        assertTrue(finalResult.handComplete());
        assertEquals("pA", finalResult.winners().getFirst().playerId());

        // pA committed 60, wins back own 60 + blinds 30 → chips = 1000 - 60 + 90 = 1030
        var pA = finalResult.state().players().get(0);
        assertEquals(1030, pA.chips());

        int total = finalResult.state().players().stream().mapToInt(GamePlayerState::chips).sum();
        assertEquals(3000, total);
    }
}
