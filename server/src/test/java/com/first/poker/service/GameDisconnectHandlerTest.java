package com.first.poker.service;

import com.first.poker.engine.GameAction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
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
    }

    // ── P1-1 (C2): Shared thread pool ──

    @Test
    void graceExecutorShouldBeShared_notCreateNewThreads() throws Exception {
        var roomService = mock(RoomService.class);
        var gameSession = mock(GameSessionService.class);
        var broadcast = mock(BroadcastService.class);
        var registry = mock(RoomRegistry.class);
        var broadcastHelper = mock(GameBroadcastHelper.class);
        var handler = new GameDisconnectHandler(roomService, gameSession, broadcast, registry, broadcastHelper);

        // Verify the executor field exists and is a real (non-null) shared pool
        Field executorField = GameDisconnectHandler.class.getDeclaredField("graceExecutor");
        executorField.setAccessible(true);
        var executor = executorField.get(handler);
        assertNotNull(executor, "graceExecutor should be initialized");
        assertTrue(executor instanceof java.util.concurrent.ScheduledExecutorService,
            "graceExecutor should be a ScheduledExecutorService");
    }

    // ── P1-2 (M2): Cancel grace timer ──

    @Test
    @SuppressWarnings("unchecked")
    void cancelGraceTimer_shouldCancelAndRemoveTimer() throws Exception {
        var roomService = mock(RoomService.class);
        var gameSession = mock(GameSessionService.class);
        var broadcast = mock(BroadcastService.class);
        var registry = mock(RoomRegistry.class);
        var broadcastHelper = mock(GameBroadcastHelper.class);
        var handler = new GameDisconnectHandler(roomService, gameSession, broadcast, registry, broadcastHelper);

        // Use reflection to access graceTimers map
        Field graceTimersField = GameDisconnectHandler.class.getDeclaredField("graceTimers");
        graceTimersField.setAccessible(true);
        Map<String, ScheduledFuture<?>> graceTimers = (Map<String, ScheduledFuture<?>>) graceTimersField.get(handler);

        // Insert a mock timer directly into the map
        ScheduledFuture<?> mockTimer = mock(ScheduledFuture.class);
        graceTimers.put("hangPlayer", mockTimer);

        handler.cancelGraceTimer("hangPlayer");

        // Verify timer was removed from map
        assertNull(graceTimers.get("hangPlayer"), "timer should be removed from graceTimers");
        // Verify timer was cancelled
        verify(mockTimer).cancel(false);
    }

    @Test
    void cancelGraceTimer_shouldNotThrow_whenNoTimer() {
        var roomService = mock(RoomService.class);
        var gameSession = mock(GameSessionService.class);
        var broadcast = mock(BroadcastService.class);
        var registry = mock(RoomRegistry.class);
        var broadcastHelper = mock(GameBroadcastHelper.class);
        var handler = new GameDisconnectHandler(roomService, gameSession, broadcast, registry, broadcastHelper);

        // Should not throw on player with no grace timer
        assertDoesNotThrow(() -> handler.cancelGraceTimer("phantom"));
    }

    // ── onReconnect cancels grace timer ──

    @Test
    @SuppressWarnings("unchecked")
    void onReconnect_shouldCancelGraceTimer() throws Exception {
        var roomService = mock(RoomService.class);
        var gameSession = mock(GameSessionService.class);
        var broadcast = mock(BroadcastService.class);
        var registry = mock(RoomRegistry.class);
        var broadcastHelper = mock(GameBroadcastHelper.class);
        var handler = new GameDisconnectHandler(roomService, gameSession, broadcast, registry, broadcastHelper);

        handler.registerPlayer("R1", "reconnector");

        // Put a mock grace timer into the map
        Field graceTimersField = GameDisconnectHandler.class.getDeclaredField("graceTimers");
        graceTimersField.setAccessible(true);
        Map<String, ScheduledFuture<?>> graceTimers = (Map<String, ScheduledFuture<?>>) graceTimersField.get(handler);
        ScheduledFuture<?> mockTimer = mock(ScheduledFuture.class);
        graceTimers.put("reconnector", mockTimer);

        handler.onReconnect("reconnector");

        // Verify timer was cancelled
        verify(mockTimer).cancel(false);
        assertNull(graceTimers.get("reconnector"));
    }
}
