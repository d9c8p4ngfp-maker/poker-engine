package com.first.poker.service;

import com.first.poker.engine.GameAction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameDisconnectHandlerTest {

    @Test
    void shouldRegisterAndUnregisterPlayer() {
        var roomService = mock(RoomService.class);
        var gameSession = mock(GameSessionService.class);
        var broadcast = mock(BroadcastService.class);
        var registry = mock(RoomRegistry.class);
        var broadcastHelper = mock(GameBroadcastHelper.class);
        var handler = new GameDisconnectHandler(roomService, gameSession, broadcast, registry, broadcastHelper);

        handler.registerPlayer("R1", "pA");
        // Registration should succeed without error
        handler.unregisterPlayer("pA");
    }

    @Test
    void shouldRegisterSessionMapping() {
        var roomService = mock(RoomService.class);
        var gameSession = mock(GameSessionService.class);
        var broadcast = mock(BroadcastService.class);
        var registry = mock(RoomRegistry.class);
        var broadcastHelper = mock(GameBroadcastHelper.class);
        var handler = new GameDisconnectHandler(roomService, gameSession, broadcast, registry, broadcastHelper);

        handler.registerSession("s1", "pA");
        // Session registration should succeed
    }
}
