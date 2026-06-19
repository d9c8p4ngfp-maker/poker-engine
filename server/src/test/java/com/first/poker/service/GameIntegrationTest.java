package com.first.poker.service;

import com.first.poker.engine.*;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class GameIntegrationTest {

    @Test
    void shouldPlayFullHandToShowdown() {
        var room = new Room("RX", "test", RoomConfig.withDefaults());
        var pA = new Player("A", "Alice", 0, 1000);
        pA.setOwner(true);
        room.addPlayer(pA);
        room.setOwner(pA);
        room.addPlayer(new Player("B", "Bob", 1, 1000));
        room.addPlayer(new Player("C", "Charlie", 2, 1000));

        var session = new GameSessionService();
        var state = session.startGame(room, "A");
        assertEquals(GamePhase.PRE_FLOP, state.phase());

        // Preflop: all call the BB
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CALL, 0).state();
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CALL, 0).state();
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0).state();
        assertEquals(GamePhase.FLOP, state.phase());

        // Flop: check around
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0).state();
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0).state();
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0).state();
        assertEquals(GamePhase.TURN, state.phase());

        // Turn: check around
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0).state();
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0).state();
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0).state();
        assertEquals(GamePhase.RIVER, state.phase());

        // River: check around → showdown
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0).state();
        state = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0).state();
        var finalResult = session.applyAction("RX", state.currentPlayer().playerId(),
            GameAction.CHECK, 0);

        assertEquals(GamePhase.SHOWDOWN, finalResult.state().phase());
        assertTrue(finalResult.handComplete());
        assertFalse(finalResult.winners().isEmpty());

        // Chips should be conserved
        int total = finalResult.state().players().stream()
            .mapToInt(GamePlayerState::chips).sum();
        assertEquals(3000, total);
    }
}
