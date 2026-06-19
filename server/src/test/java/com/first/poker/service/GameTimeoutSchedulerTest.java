package com.first.poker.service;

import com.first.poker.engine.GameAction;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GameTimeoutSchedulerTest {

    @Test
    void shouldExecuteTimeoutCallback() {
        var called = new AtomicBoolean(false);
        var capturedAction = new AtomicReference<String>();

        GameTimeoutScheduler scheduler = new GameTimeoutScheduler((roomId, playerId) -> {
            called.set(true);
            capturedAction.set(roomId + ":" + playerId);
        });

        scheduler.scheduleTimeout("R1", "pA", 1);
        // Wait for timeout to fire
        try { Thread.sleep(1200); } catch (InterruptedException e) {}
        scheduler.cancelTimeout("R1");

        assertTrue(called.get());
        assertEquals("R1:pA", capturedAction.get());
    }

    @Test
    void cancelShouldPreventTimeout() {
        var called = new AtomicBoolean(false);
        GameTimeoutScheduler scheduler = new GameTimeoutScheduler((roomId, playerId) -> {
            called.set(true);
        });

        scheduler.scheduleTimeout("R2", "pB", 5);
        scheduler.cancelTimeout("R2");
        try { Thread.sleep(200); } catch (InterruptedException e) {}

        // Should not have fired since we cancelled quickly
        assertFalse(called.get());
    }

    @Test
    void scheduleShouldReplacePreviousTimeout() {
        AtomicReference<String> lastPlayer = new AtomicReference<>();
        GameTimeoutScheduler scheduler = new GameTimeoutScheduler((roomId, playerId) -> {
            lastPlayer.set(playerId);
        });

        scheduler.scheduleTimeout("R3", "pA", 1);
        scheduler.scheduleTimeout("R3", "pB", 1);
        try { Thread.sleep(1200); } catch (InterruptedException e) {}
        scheduler.cancelTimeout("R3");

        // Should be pB (last scheduled)
        assertEquals("pB", lastPlayer.get());
    }
}
