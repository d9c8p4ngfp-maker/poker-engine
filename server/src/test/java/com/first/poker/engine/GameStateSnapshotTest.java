package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

class GameStateSnapshotTest {

    private GamePlayerState p(String id, int chips, int roundBet, int totalBet) {
        return new GamePlayerState(id, id, 0, chips, totalBet, roundBet, false, false,
            List.of(Card.fromString("Ah"), Card.fromString("Kh")));
    }

    @Test
    void shouldBuildPublicSnapshotWithoutHoleCards() {
        var players = List.of(p("A", 1000, 0, 0), p("B", 1000, 0, 0));
        var deck = new Deck();
        var state = GameState.create(players, 0, 10, 20, deck);

        var snapshot = GameStateSnapshot.buildPublic(state, null);

        assertEquals("PREFLOP", snapshot.get("phase"));
        @SuppressWarnings("unchecked")
        var playersList = (List<Map<String, Object>>) snapshot.get("players");
        assertEquals(2, playersList.size());
        // No hole cards in public snapshot
        assertNull(playersList.get(0).get("holeCards"));
        assertNull(playersList.get(1).get("holeCards"));
        // myHoleCards should not be present in public
        assertNull(snapshot.get("myHoleCards"));
    }

    @Test
    void shouldBuildPrivateSnapshotWithMyHoleCardsOnly() {
        var players = List.of(p("A", 1000, 0, 0), p("B", 1000, 0, 0));
        var deck = new Deck();
        var state = GameState.create(players, 0, 10, 20, deck);

        var snapshot = GameStateSnapshot.buildForPlayer(state, "A", null);

        @SuppressWarnings("unchecked")
        var myCards = (List<String>) snapshot.get("myHoleCards");
        assertNotNull(myCards);
        assertEquals(2, myCards.size());
        assertTrue(myCards.contains("Ah") || myCards.contains("Kh"));

        // Still no holeCards on public player list
        @SuppressWarnings("unchecked")
        var playersList = (List<Map<String, Object>>) snapshot.get("players");
        assertNull(playersList.get(0).get("holeCards"));
    }

    @Test
    void shouldIncludeCommunityCards() {
        var players = List.of(p("A", 1000, 0, 0));
        var deck = new Deck();
        var state = new GameState(GamePhase.FLOP, players,
            List.of(Card.fromString("Ah"), Card.fromString("Kd"), Card.fromString("Qs")),
            100, 20, 20, 0, 0, 10, 20, deck, 0, -1);

        var snapshot = GameStateSnapshot.buildPublic(state, null);

        @SuppressWarnings("unchecked")
        var cards = (List<String>) snapshot.get("communityCards");
        assertEquals(3, cards.size());
        assertEquals("Ah", cards.get(0));
        assertEquals("Kd", cards.get(1));
        assertEquals("Qs", cards.get(2));
    }

    @Test
    void shouldIncludePotAndCurrentBet() {
        var players = List.of(p("A", 1000, 0, 0));
        var deck = new Deck();
        var state = GameState.create(players, 0, 10, 20, deck);
        var withPot = new GameState(state.phase(), state.players(), state.communityCards(),
            150, 20, 20, 0, 0, 10, 20, deck, 0, -1);

        var snapshot = GameStateSnapshot.buildPublic(withPot, null);

        assertEquals(150, snapshot.get("pot"));
        assertEquals(20, snapshot.get("currentBet"));
    }

    @Test
    void shouldMapPhaseToRoomSnapshotFormat() {
        var players = List.of(p("A", 1000, 0, 0));
        var deck = new Deck();
        var state = new GameState(GamePhase.RIVER, players, List.of(), 200, 40, 20, 0, 0, 10, 20, deck, 0, -1);

        var snapshot = GameStateSnapshot.buildPublic(state, null);

        assertEquals("RIVER", snapshot.get("bettingRound"));
        assertEquals("PLAYING", snapshot.get("status"));
    }
}
