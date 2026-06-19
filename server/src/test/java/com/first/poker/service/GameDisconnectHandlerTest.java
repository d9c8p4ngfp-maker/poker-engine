package com.first.poker.service;

import com.first.poker.engine.GameAction;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GameDisconnectHandlerTest {

    @Test
    void shouldFoldOnDisconnect() {
        var foldedRoom = new AtomicReference<String>();
        var foldedPlayer = new AtomicReference<String>();

        // Simulate GameSessionService that tracks room participants
        var disconnectHandler = new GameDisconnectHandler((roomId, playerId) -> {
            foldedRoom.set(roomId);
            foldedPlayer.set(playerId);
        });

        disconnectHandler.registerPlayer("R1", "pA");
        disconnectHandler.handleDisconnect("pA");

        assertEquals("R1", foldedRoom.get());
        assertEquals("pA", foldedPlayer.get());
    }

    @Test
    void shouldIgnoreUnknownPlayer() {
        var foldedRoom = new AtomicReference<String>();
        var disconnectHandler = new GameDisconnectHandler((roomId, playerId) -> {
            foldedRoom.set(roomId);
        });

        disconnectHandler.handleDisconnect("unknown");
        // Should not have called fold
        assertNull(foldedRoom.get());
    }
}
