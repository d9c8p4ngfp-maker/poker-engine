package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ErrorPathTest {

    @Test
    void actionAfterHandOverThrows() {
        var players = List.of(
            new GamePlayerState("A", "A", 0, 1000, 0, 0, false, false, List.of()),
            new GamePlayerState("B", "B", 1, 1000, 0, 0, false, false, List.of())
        );
        var deck = new Deck(42L);
        var state = GameState.create(players, 0, 10, 20, deck);
        var showdownState = new GameState(
            GamePhase.SHOWDOWN, state.players(), List.of(), 0, 0, 20,
            0, 0, 10, 20, deck, 0, -1
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> GameEngine.processAction(showdownState, GameAction.CALL, 0));
        assertTrue(ex.getMessage().contains("Hand is already over"));
    }

    @Test
    void callWhenNothingToCallThrows() {
        // player with roundBet matching currentBet → nothing to call
        var player = new GamePlayerState("A", "A", 0, 1000, 0, 20, false, false, List.of());
        var players = List.of(player,
            new GamePlayerState("B", "B", 1, 1000, 0, 0, false, false, List.of()));
        var deck = new Deck(42L);
        var state = new GameState(
            GamePhase.FLOP, players, List.of(), 0, 20, 20,
            0, 0, 10, 20, deck, 0, -1
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ActionValidator.validate(state, GameAction.CALL, 0));
        assertTrue(ex.getMessage().contains("Nothing to call"));
    }

    @Test
    void betWhenMustRaiseThrows() {
        var player = new GamePlayerState("A", "A", 0, 1000, 0, 0, false, false, List.of());
        var players = List.of(player,
            new GamePlayerState("B", "B", 1, 1000, 0, 0, false, false, List.of()));
        var deck = new Deck(42L);
        var state = new GameState(
            GamePhase.FLOP, players, List.of(), 0, 40, 20,
            0, 0, 10, 20, deck, 0, -1
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ActionValidator.validate(state, GameAction.BET, 60));
        assertTrue(ex.getMessage().contains("Must raise"));
    }
}
