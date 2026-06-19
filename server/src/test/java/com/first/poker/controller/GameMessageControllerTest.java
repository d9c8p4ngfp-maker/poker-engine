package com.first.poker.controller;

import com.first.poker.dto.GameActionRequest;
import com.first.poker.engine.*;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import com.first.poker.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

class GameMessageControllerTest {

    @Test
    void shouldStartGameAndBroadcast() {
        var roomService = mock(RoomService.class);
        var gameSession = mock(GameSessionService.class);
        var broadcast = mock(BroadcastService.class);
        var timeout = mock(GameTimeoutScheduler.class);
        var disconnect = mock(GameDisconnectHandler.class);
        var controller = new GameMessageController(roomService, gameSession, broadcast, timeout, disconnect);

        var room = new Room("R1", "test", RoomConfig.withDefaults());
        room.addPlayer(new Player("A", "Alice", 0, 1000));
        room.addPlayer(new Player("B", "Bob", 1, 1000));

        when(roomService.findRoom("R1")).thenReturn(room);

        var deck = new Deck();
        var state = GameState.create(
            room.getPlayers().stream().map(GamePlayerState::fromPlayer).toList(),
            0, 10, 20, deck
        );
        when(gameSession.startGame(room, "A")).thenReturn(state);

        var req = new GameActionRequest();
        req.setPlayerId("A");
        controller.startGame("R1", req);

        verify(gameSession).startGame(room, "A");
        verify(broadcast, atLeastOnce()).sendToRoom(eq("R1"), eq("game"), any());
        verify(disconnect, atLeastOnce()).registerPlayer(eq("R1"), any());
        verify(timeout).scheduleTimeout(eq("R1"), any(), eq(30));
    }

    @Test
    void shouldRejectStartFromNonOwner() {
        var roomService = mock(RoomService.class);
        var gameSession = mock(GameSessionService.class);
        var broadcast = mock(BroadcastService.class);
        var timeout = mock(GameTimeoutScheduler.class);
        var disconnect = mock(GameDisconnectHandler.class);
        var controller = new GameMessageController(roomService, gameSession, broadcast, timeout, disconnect);

        var room = new Room("R1", "test", RoomConfig.withDefaults());
        room.addPlayer(new Player("A", "Alice", 0, 1000));
        room.addPlayer(new Player("B", "Bob", 1, 1000));

        when(roomService.findRoom("R1")).thenReturn(room);
        doThrow(new IllegalArgumentException("Only room owner can start the game"))
            .when(gameSession).startGame(room, "B");

        var req = new GameActionRequest();
        req.setPlayerId("B");
        assertThrows(IllegalArgumentException.class, () ->
            controller.startGame("R1", req));
    }
}
