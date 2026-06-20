package com.first.poker.service;

import com.first.poker.engine.GameAction;
import com.first.poker.engine.GameEngine;
import com.first.poker.engine.GamePhase;
import com.first.poker.engine.GamePlayerState;
import com.first.poker.engine.GameState;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class GameSessionServiceTest {

    private Room makeRoom(String roomId, List<Player> players) {
        var config = RoomConfig.withDefaults();
        var room = new Room(roomId, "test", config);
        players.forEach(room::addPlayer);
        // Set first player as owner (matching createRoom behavior)
        if (!players.isEmpty()) {
            players.get(0).setOwner(true);
            room.setOwner(players.get(0));
        }
        return room;
    }

    @Test
    void shouldStartGameAndReturnState() {
        var room = makeRoom("R1", List.of(
            new Player("A", "Alice", 0, 1000),
            new Player("B", "Bob", 1, 1000)
        ));
        var service = new GameSessionService();

        var state = service.startGame(room, "A");
        assertNotNull(state);
        assertEquals(2, state.players().size());
        assertEquals(GamePhase.PRE_FLOP, state.phase());
    }

    @Test
    void shouldApplyActionAndAdvance() {
        var room = makeRoom("R2", List.of(
            new Player("A", "Alice", 0, 1000),
            new Player("B", "Bob", 1, 1000)
        ));
        var service = new GameSessionService();

        var state = service.startGame(room, "A");
        // First player (UTG) calls
        var result = service.applyAction("R2", state.currentPlayer().playerId(), GameAction.CALL, 0);
        assertNotNull(result);
        assertFalse(result.handComplete());
    }

    @Test
    void shouldRejectActionFromWrongRoom() {
        var service = new GameSessionService();
        assertThrows(IllegalStateException.class, () ->
            service.applyAction("NONEXISTENT", "A", GameAction.CHECK, 0));
    }

    @Test
    void shouldRejectStartForAlreadyPlayingRoom() {
        var room = makeRoom("R3", List.of(
            new Player("A", "Alice", 0, 1000),
            new Player("B", "Bob", 1, 1000)
        ));
        var service = new GameSessionService();
        service.startGame(room, "A");
        assertThrows(IllegalStateException.class, () -> service.startGame(room, "A"));
    }

    @Test
    void shouldOnlyAllowOwnerToStart() {
        var room = makeRoom("R4", List.of(
            new Player("A", "Alice", 0, 1000),
            new Player("B", "Bob", 1, 1000)
        ));
        var service = new GameSessionService();
        assertThrows(IllegalArgumentException.class, () -> service.startGame(room, "B")); // B is not owner (index 0)
    }

    @Test
    void executeWithLock_shouldRunTaskUnderLock() {
        var service = new GameSessionService();
        var executed = new boolean[1];
        service.executeWithLock("R1", () -> executed[0] = true);
        assertTrue(executed[0]);
    }

    @Test
    void executeWithLock_shouldBeReentrant() {
        var service = new GameSessionService();
        var executed = new boolean[1];
        service.executeWithLock("R1", () -> {
            service.executeWithLock("R1", () -> executed[0] = true);
        });
        assertTrue(executed[0]);
    }

    @Test
    void endGame_shouldRunBeforeUnlockInsideLock() {
        var room = makeRoom("R5", List.of(
            new Player("A", "Alice", 0, 1000),
            new Player("B", "Bob", 1, 1000)
        ));
        var service = new GameSessionService();
        service.startGame(room, "A");
        var taskRan = new boolean[1];
        service.endGame("R5", () -> taskRan[0] = true);
        assertTrue(taskRan[0]);
        assertFalse(service.hasActiveSession("R5"));
    }

    @Test
    void shouldRejectStartWhenOnlyOnePlayerHasChips() {
        // Owner has 0 chips, only the other player has chips → 1 eligible player
        var owner = new Player("A", "Alice", 0, 0);
        var other = new Player("B", "Bob", 1, 1000);
        var room = makeRoom("R6", List.of(owner, other));
        var service = new GameSessionService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.startGame(room, "A"));
        assertTrue(ex.getMessage().contains("at least 2 players"));
        assertFalse(service.hasActiveSession("R6"),
            "No zombie session should be left behind");
    }

    @Test
    void shouldRejectStartWhenAllPlayersHaveZeroChips() {
        var owner = new Player("A", "Alice", 0, 0);
        var other = new Player("B", "Bob", 1, 0);
        var room = makeRoom("R7", List.of(owner, other));
        var service = new GameSessionService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.startGame(room, "A"));
        assertTrue(ex.getMessage().contains("at least 2 players"));
        assertFalse(service.hasActiveSession("R7"),
            "No zombie session should be left behind");
    }
}
