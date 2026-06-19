package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class GameStateTest {

    private GamePlayerState player(String id, int chips) {
        return new GamePlayerState(id, id, 0, chips, 0, false, false, List.of());
    }

    @Test
    void shouldCreateInitialState() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var deck = new Deck();
        deck.shuffle();
        var state = GameState.create(players, 0, 10, 20, deck);

        assertEquals(GamePhase.PRE_FLOP, state.phase());
        assertEquals(3, state.players().size());
        assertEquals(0, state.currentBet());
        assertEquals(20, state.minRaise());
        assertEquals(0, state.pot());
        assertEquals(0, state.communityCards().size());
    }

    @Test
    void shouldGetCurrentPlayer() {
        var players = List.of(player("A", 1000), player("B", 1000));
        var deck = new Deck();
        var state = GameState.create(players, 0, 10, 20, deck);

        assertEquals("A", state.currentPlayer().playerId());
    }

    @Test
    void shouldSetPot() {
        var players = List.of(player("A", 1000), player("B", 1000));
        var deck = new Deck();
        var state = GameState.create(players, 0, 10, 20, deck);

        var updated = state.withPot(150);
        assertEquals(150, updated.pot());
        assertEquals(0, state.pot()); // original unchanged
    }

    @Test
    void shouldAdvancePlayerIndex() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var deck = new Deck();
        var state = GameState.create(players, 0, 10, 20, deck);

        assertEquals("A", state.currentPlayer().playerId());

        var next = state.withNextPlayer();
        int idx = next.currentPlayerIndex();
        assertEquals(1, idx);
        assertEquals("B", next.currentPlayer().playerId());

        var next2 = next.withNextPlayer();
        assertEquals("C", next2.currentPlayer().playerId());

        // wrap around
        var next3 = next2.withNextPlayer();
        assertEquals("A", next3.currentPlayer().playerId());
    }

    @Test
    void shouldSkipFoldedAndAllInPlayers() {
        var p1 = player("A", 1000).withFolded();
        var p2 = player("B", 1000);
        var players = List.of(p1, p2);
        var deck = new Deck();
        var state = GameState.create(players, 0, 10, 20, deck).withNextPlayer();

        // should skip folded player A
        assertEquals("B", state.currentPlayer().playerId());
    }
}
