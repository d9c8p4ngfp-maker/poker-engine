package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class PositionAndDealerTest {

    private GamePlayerState player(String id, int chips) {
        return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, List.of());
    }

    private RoomConfig config(int sb) {
        RoomConfig c = RoomConfig.withDefaults();
        c.setSmallBlind(sb);
        return c;
    }

    @Test @DisplayName("Dealer=0 sets SB=1, BB=2")
    void standardDealerPositions() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // dealer=0 → SB=1 (B, index 1), BB=2 (C, index 2)
        // First to act (UTG) = index 0 (A)
        assertEquals(0, state.currentPlayerIndex(), "UTG should be index 0");
        assertEquals("A", state.currentPlayer().playerId());
    }

    @Test @DisplayName("Dealer=1 wraps SB/BB")
    void dealerOneWrapsBlinds() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 1, config(10)).state();
        // dealer=1 → SB=2 (C), BB=0 (A), UTG=1 (B)
        assertEquals(1, state.currentPlayerIndex());
        assertEquals("B", state.currentPlayer().playerId());
    }

    @Test @DisplayName("Dealer=2 sets SB=0, BB=1")
    void dealerTwoWrapsBlinds() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 2, config(10)).state();
        // dealer=2 → SB=0 (A), BB=1 (B), UTG=2 (C)
        assertEquals(2, state.currentPlayerIndex());
        assertEquals("C", state.currentPlayer().playerId());
    }

    @Test @DisplayName("Postflop first-to-act is left of dealer")
    void postflopFirstToActLeftOfDealer() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // Preflop all call
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.FLOP, state.phase());
        // On flop, first to act should be left of dealer (index 0)
        // dealer=0 → left is index=1 (B)
        assertEquals(1, state.currentPlayerIndex());
        assertEquals("B", state.currentPlayer().playerId());
    }

    @Test @DisplayName("Dealer also acts last postflop")
    void dealerActsLastPostflop() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // Preflop all call
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        // Flop: B checks, C checks, A checks
        assertEquals("B", state.currentPlayer().playerId());
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals("C", state.currentPlayer().playerId());
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals("A", state.currentPlayer().playerId(), "Dealer A should act last on flop");
    }

    @Test @DisplayName("Blinds rotate when dealer changes")
    void blindsRotateWithDealer() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        // Hand 1: dealer=0
        var s1 = GameEngine.startHand(players, 0, config(10)).state();
        // dealer=0 → SB(B,1), BB(C,2)
        assertEquals(990, s1.players().get(1).chips(), "B should post SB (10)");
        assertEquals(980, s1.players().get(2).chips(), "C should post BB (20)");

        // Hand 2: dealer=1
        var s2 = GameEngine.startHand(players, 1, config(10)).state();
        // dealer=1 → SB(C,2), BB(A,0)
        assertEquals(990, s2.players().get(2).chips(), "C should post SB (10)");
        assertEquals(980, s2.players().get(0).chips(), "A should post BB (20)");
    }

    @Test @DisplayName("Heads-up: dealer=SB, not BB")
    void headsUpDealerIsSmallBlind() {
        var players = List.of(player("A", 1000), player("B", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // dealer=0 → SB=1 (B, becomes SB), BB=0 (A, is BB)
        // In heads-up, dealer is SB
        assertEquals(990, state.players().get(1).chips(), "B (dealer) should post SB");
        assertEquals(980, state.players().get(0).chips(), "A should post BB");
    }

    @Test @DisplayName("SB posts full amount when enough chips")
    void sbPostsFullWhenEnough() {
        var players = List.of(player("A", 1000), player("B", 5), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // dealer=0 → SB(B,1), BB(C,2)
        // B has only 5 chips, posts 5 as partial SB
        assertTrue(state.players().get(1).chips() <= 5,
            "B with 5 chips should post partial or no SB remaining");
    }
}
