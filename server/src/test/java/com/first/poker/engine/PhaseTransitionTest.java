package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class PhaseTransitionTest {

    private GamePlayerState p(String id, int chips) {
        return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, List.of());
    }

    @Test
    void shouldStartHandWithBlindsAndHoleCards() {
        var players = List.of(p("A", 1000), p("B", 1000), p("C", 1000));
        var config = RoomConfig.withDefaults();
        config.setSmallBlind(10);

        var state = PhaseTransition.startHand(players, 0, config);

        // Phase is PRE_FLOP
        assertEquals(GamePhase.PRE_FLOP, state.phase());

        // Blinds posted: SB (index 1) loses 10, BB (index 2) loses 20
        assertEquals(990, state.players().get(1).chips()); // SB
        assertEquals(980, state.players().get(2).chips()); // BB

        // Each player has 2 hole cards
        for (var p : state.players()) {
            assertEquals(2, p.holeCards().size());
        }

        // currentBet is big blind (20)
        assertEquals(20, state.currentBet());

        // First to act preflop (3 players): index 0 (UTG, after BB wraps around)
        assertEquals(0, state.currentPlayerIndex());
    }

    @Test
    void shouldAdvanceFromPreFlopToFlop() {
        var players = List.of(p("A", 1000), p("B", 1000), p("C", 1000));
        var config = RoomConfig.withDefaults();
        var state = PhaseTransition.startHand(players, 0, config);

        var flop = PhaseTransition.advancePhase(state);

        assertEquals(GamePhase.FLOP, flop.phase());
        assertEquals(3, flop.communityCards().size());
        assertEquals(0, flop.currentBet()); // bet resets

        // roundBet reset for all players
        for (var p : flop.players()) {
            assertEquals(0, p.roundBet());
        }

        // Pot should collect blinds (10 + 20 = 30)
        // Actually, pot only collects at end of round, and blinds are in pot
        // For now, let's verify just phase transition
    }

    @Test
    void shouldAdvanceFromFlopToTurnToRiver() {
        var players = List.of(p("A", 1000), p("B", 1000), p("C", 1000));
        var config = RoomConfig.withDefaults();
        var state = PhaseTransition.startHand(players, 0, config);

        var flop = PhaseTransition.advancePhase(state);
        assertEquals(GamePhase.FLOP, flop.phase());
        assertEquals(3, flop.communityCards().size());

        var turn = PhaseTransition.advancePhase(flop);
        assertEquals(GamePhase.TURN, turn.phase());
        assertEquals(4, turn.communityCards().size());

        var river = PhaseTransition.advancePhase(turn);
        assertEquals(GamePhase.RIVER, river.phase());
        assertEquals(5, river.communityCards().size());
    }

    @Test
    void shouldAdvanceToShowdown() {
        var players = List.of(p("A", 1000), p("B", 1000));
        var config = RoomConfig.withDefaults();
        var state = PhaseTransition.startHand(players, 0, config);

        // manually advance through phases
        var flop = PhaseTransition.advancePhase(state);
        var turn = PhaseTransition.advancePhase(flop);
        var river = PhaseTransition.advancePhase(turn);
        var showdown = PhaseTransition.advancePhase(river);

        assertEquals(GamePhase.SHOWDOWN, showdown.phase());
    }
}
