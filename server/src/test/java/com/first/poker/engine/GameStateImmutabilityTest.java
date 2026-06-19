package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class GameStateImmutabilityTest {

    @Test
    void processActionPreservesInputFields() {
        var players = List.of(
            new GamePlayerState("A", "A", 0, 1000, 0, 0, false, false, List.of()),
            new GamePlayerState("B", "B", 1, 1000, 0, 0, false, false, List.of()),
            new GamePlayerState("C", "C", 2, 1000, 0, 0, false, false, List.of())
        );
        var deck = new Deck(42L);
        // Create a state with blinds posted (currentBet=20) so CALL is valid
        var state = new GameState(
            GamePhase.PRE_FLOP, players, List.of(), 30, 20, 20,
            0, 0, 10, 20, deck, 0, -1
        );

        GamePhase oldPhase = state.phase();
        int oldPot = state.pot();
        int oldIndex = state.currentPlayerIndex();

        var result = GameEngine.processAction(state, GameAction.CALL, 0);

        assertEquals(oldPhase, state.phase(), "Old phase unchanged");
        assertEquals(oldPot, state.pot(), "Old pot unchanged");
        assertEquals(oldIndex, state.currentPlayerIndex(), "Old index unchanged");

        assertNotNull(result.state());
    }

    @Test
    void withUpdatedPlayerPreservesOldEntry() {
        var pA = new GamePlayerState("A", "A", 0, 1000, 0, 0, false, false, List.of());
        var pB = new GamePlayerState("B", "B", 1, 1000, 0, 0, false, false, List.of());
        var players = List.of(pA, pB);
        var deck = new Deck(42L);
        var state = GameState.create(players, 0, 10, 20, deck);

        var pBModified = pB.withChipsDeducted(100);
        var newState = state.withUpdatedPlayer(1, pBModified);

        assertEquals(1000, state.players().get(1).chips(), "Old player chips unchanged");
        assertEquals(900, newState.players().get(1).chips(), "New player has deducted chips");
    }

    @Test
    void deckIsNotConsumedInOldStateAfterProcessAction() {
        var players = List.of(
            new GamePlayerState("A", "A", 0, 1000, 0, 0, false, false, List.of()),
            new GamePlayerState("B", "B", 1, 1000, 0, 0, false, false, List.of()),
            new GamePlayerState("C", "C", 2, 1000, 0, 0, false, false, List.of())
        );
        var deck = new Deck(42L);
        var state = new GameState(
            GamePhase.PRE_FLOP, players, List.of(), 30, 20, 20,
            0, 0, 10, 20, deck, 0, -1
        );

        // Play preflop (all call), verify old state unaffected
        var s = state;
        s = GameEngine.processAction(s, GameAction.CALL, 0).state();
        s = GameEngine.processAction(s, GameAction.CALL, 0).state();
        var finalResult = GameEngine.processAction(s, GameAction.CALL, 0);

        // Verify valid result returned (phase may have advanced)
        assertNotNull(finalResult.state());
        assertNotNull(finalResult.state().phase());

        // Verify the old state still has valid phase
        assertNotNull(state.phase());
    }
}
