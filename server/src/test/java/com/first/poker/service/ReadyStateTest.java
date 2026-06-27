package com.first.poker.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ReadyStateTest {

    @Test
    void shouldMarkAndRetrieveReady() {
        var service = new GameSessionService();
        service.markReady("R1", "A");
        service.markReady("R1", "B");
        assertTrue(service.allReady("R1", List.of("A", "B")));
        assertEquals(2, service.getReadyPlayers("R1").size());
    }

    @Test
    void shouldNotBeAllReadyWhenMissingPlayer() {
        var service = new GameSessionService();
        service.markReady("R1", "A");
        assertFalse(service.allReady("R1", List.of("A", "B")));
    }

    @Test
    void shouldClearReady() {
        var service = new GameSessionService();
        service.markReady("R1", "A");
        service.markReady("R1", "B");
        service.clearReady("R1");
        assertTrue(service.getReadyPlayers("R1").isEmpty());
    }

    @Test
    void shouldRemoveReadyForPlayer() {
        var service = new GameSessionService();
        service.markReady("R1", "A");
        service.markReady("R1", "B");
        service.removeReady("R1", "A");
        assertFalse(service.allReady("R1", List.of("A", "B")));
        assertTrue(service.allReady("R1", List.of("B")));
    }

    @Test
    void shouldHandleEmptyReadySet() {
        var service = new GameSessionService();
        assertTrue(service.getReadyPlayers("R1").isEmpty());
        assertFalse(service.allReady("R1", List.of("A")));
    }

    @Test
    void shouldIsolateRooms() {
        var service = new GameSessionService();
        service.markReady("R1", "A");
        service.markReady("R2", "B");
        assertTrue(service.allReady("R1", List.of("A")));
        assertFalse(service.allReady("R1", List.of("B")));
        assertTrue(service.allReady("R2", List.of("B")));
    }

    @Test
    void shouldClearReadyOnStartGame() {
        var service = new GameSessionService();
        service.markReady("R1", "A");

        var config = com.first.poker.model.RoomConfig.withDefaults();
        var room = new com.first.poker.model.Room("R1", "test", config);
        var p1 = new com.first.poker.model.Player("A", "Alice", 0, 1000);
        var p2 = new com.first.poker.model.Player("B", "Bob", 1, 1000);
        p1.setOwner(true);
        room.setOwner(p1);
        room.addPlayer(p1);
        room.addPlayer(p2);

        service.startGame(room, "A");
        assertTrue(service.getReadyPlayers("R1").isEmpty());
    }

    @Test
    void shouldAutoContinueBeFalseByDefault() {
        var config = com.first.poker.model.RoomConfig.withDefaults();
        assertFalse(config.isAutoContinue());
    }

    @Test
    void shouldSetAutoContinue() {
        var config = com.first.poker.model.RoomConfig.withDefaults();
        config.setAutoContinue(true);
        assertTrue(config.isAutoContinue());
    }
}
