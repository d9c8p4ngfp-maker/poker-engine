package com.first.poker.service;

import com.first.poker.engine.*;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GameStateSnapshotTest {

    @Test
    void gameStateRecordContainsAllRequiredFields() {
        List<GamePlayerState> players = List.of(
            new GamePlayerState("p1", "A1", 0, 900, 0, 0, false, false, List.of()),
            new GamePlayerState("p2", "B2", 1, 1000, 0, 0, false, false, List.of())
        );

        GameState state = new GameState(
            GamePhase.PRE_FLOP,
            players,
            new ArrayList<>(),
            100,
            20,
            20,
            0,
            0,
            5,
            10,
            new Deck(),
            0,
            0
        );

        assertEquals(100, state.pot());
        assertEquals(20, state.currentBet());
        assertEquals(20, state.minRaise());
        assertEquals(0, state.currentPlayerIndex());
        assertEquals(GamePhase.PRE_FLOP, state.phase());
        assertEquals(5, state.smallBlindAmount());
        assertEquals(10, state.bigBlindAmount());
    }

    @Test
    void gamePlayerStateTracksChipsAndBets() {
        GamePlayerState p = new GamePlayerState("p1", "Alice", 0, 1000, 50, 50, false, false, List.of());
        assertEquals(1000, p.chips());
        assertEquals(50, p.totalBet());
        assertEquals(50, p.roundBet());
        assertFalse(p.folded());
        assertFalse(p.allIn());
    }

    @Test
    void foldedPlayerStillPresentInState() {
        GamePlayerState p = new GamePlayerState("p1", "A", 0, 500, 0, 0, true, false, List.of());
        assertEquals(500, p.chips());
        assertTrue(p.folded());
    }

    @Test
    void createBuildsCorrectInitialState() {
        List<GamePlayerState> players = List.of(
            new GamePlayerState("p1", "A", 0, 1000, 0, 0, false, false, List.of()),
            new GamePlayerState("p2", "B", 1, 1000, 0, 0, false, false, List.of())
        );

        GameState gs = GameState.create(players, 0, 5, 10, new Deck());
        assertNotNull(gs);
        assertEquals(GamePhase.PRE_FLOP, gs.phase());
        assertEquals(0, gs.currentBet());
        assertEquals(2, gs.players().size());
    }
}
