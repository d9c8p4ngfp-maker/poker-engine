package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class BettingRoundManagerTest {

    private GamePlayerState p(String id, int chips, int totalBet, int roundBet) {
        return new GamePlayerState(id, id, 0, chips, totalBet, roundBet, false, false, List.of());
    }

    private GamePlayerState pFolded(String id, int totalBet) {
        return new GamePlayerState(id, id, 0, 1000, totalBet, totalBet, true, false, List.of());
    }

    private GameState freshState(List<GamePlayerState> players, int currentBet, int dealerIndex) {
        var deck = new Deck();
        return GameState.create(players, dealerIndex, 10, 20, deck)
            .withCurrentBet(currentBet);
    }

    @Test
    void shouldApplyCheckAndAdvance() {
        var players = List.of(p("A", 1000, 0, 0), p("B", 1000, 0, 0));
        var state = freshState(players, 0, 0);
        int mask = (1 << players.size()) - 1; // all acted

        var result = BettingRoundManager.applyAction(state, GameAction.CHECK, 0, mask, -1);
        assertEquals(1, result.currentPlayerIndex());
    }

    @Test
    void shouldApplyCallAndDeductChips() {
        var players = List.of(p("A", 1000, 20, 0), p("B", 1000, 20, 20));
        var state = freshState(players, 20, 0);
        int mask = 1; // only B acted (the raiser)
        int lastAgg = 1;

        // B raised to 20, now A needs to call 20
        var result = BettingRoundManager.applyAction(state, GameAction.CALL, 0, mask, lastAgg);
        // A should have 20 fewer chips, roundBet should be 20
        var updatedA = result.players().get(0);
        assertEquals(980, updatedA.chips());
        assertEquals(20, updatedA.roundBet());
    }

    @Test
    void shouldApplyBetAndSetNewLevel() {
        var players = List.of(p("A", 1000, 0, 0), p("B", 1000, 0, 0));
        var state = freshState(players, 0, 0);
        int mask = (1 << players.size()) - 1;

        var result = BettingRoundManager.applyAction(state, GameAction.BET, 40, mask, -1);
        assertEquals(40, result.currentBet());
        assertEquals(960, result.players().get(0).chips());
        assertEquals(40, result.players().get(0).roundBet());
        assertEquals(0, result.lastAggressorIndex());
    }

    @Test
    void shouldApplyRaiseAndReopenBetting() {
        var players = List.of(p("A", 1000, 20, 10), p("B", 1000, 30, 30), p("C", 990, 20, 20));
        // A bet 10, B raised to 30, C called 20... now A needs to call or raise
        var state = freshState(players, 30, 0);
        int mask = (1 << 1); // Only B acted since last raise
        int lastAgg = 1;

        var result = BettingRoundManager.applyAction(state, GameAction.RAISE, 60, mask, lastAgg);
        assertEquals(60, result.currentBet());
        assertEquals(0, result.lastAggressorIndex());
        // acted mask should be reset to only include A (index 0)
        assertEquals(1 << 0, result.actedMask());
    }

    @Test
    void shouldDetectRoundComplete() {
        var players = List.of(
            p("A", 1000, 0, 0),
            p("B", 1000, 0, 0),
            p("C", 1000, 0, 0)
        );
        var state = freshState(players, 0, 0);
        int mask = (1 << players.size()) - 1;
        int lastAgg = -1;

        assertTrue(BettingRoundManager.isRoundComplete(state, mask, lastAgg));
    }

    @Test
    void shouldNotBeCompleteIfBetsUnequal() {
        var players = List.of(
            p("A", 980, 20, 20),
            p("B", 1000, 10, 10),
            p("C", 1000, 0, 0)
        );
        var state = freshState(players, 20, 0);
        int mask = (1 << 0) | (1 << 1);
        int lastAgg = 0;

        assertFalse(BettingRoundManager.isRoundComplete(state, mask, lastAgg));
    }

    @Test
    void shouldNotBeCompleteIfNotAllActedSinceLastRaise() {
        var players = List.of(
            p("A", 980, 20, 20),
            p("B", 980, 20, 20)
        );
        var state = freshState(players, 20, 0);
        int mask = (1 << 0); // Only A acted since last raise
        int lastAgg = 0; // A was the last aggressor

        // B hasn't acted since A's raise
        assertFalse(BettingRoundManager.isRoundComplete(state, mask, lastAgg));
    }
}
