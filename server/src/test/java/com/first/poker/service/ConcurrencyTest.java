package com.first.poker.service;

import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    @Test
    void borrowChipsUnderLock_shouldNotCorruptDuringConcurrentAccess() throws Exception {
        var config = RoomConfig.withDefaults();
        config.setInitialChips(1000);
        var room = new Room("R1", "test", config);
        Player p = new Player("A", "Alice", 0, 100);
        room.addPlayer(p);

        var service = new GameSessionService();
        int threads = 10;
        var latch = new CountDownLatch(threads);
        var executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                service.executeWithLock("R1", () -> {
                    p.borrow(100);
                });
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(100 + 10 * 100, p.getChips());
        assertEquals(10, p.getBorrowCount());
    }

    @Test
    void addPlayer_concurrentAdd_shouldNotExceedMaxSeats() throws Exception {
        var config = RoomConfig.withDefaults();
        config.setMaxSeats(6);
        var room = new Room("R2", "test", config);

        int threadCount = 20;
        var latch = new CountDownLatch(threadCount);
        var successCount = new java.util.concurrent.atomic.AtomicInteger();
        var executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                if (room.addPlayer(new Player("p" + idx, "P" + idx, -1, 1000))) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(6, successCount.get());
        assertEquals(6, room.getPlayers().size());
        // Verify all seat indices are unique
        var seats = room.getPlayers().stream().map(Player::getSeatIndex).distinct().count();
        assertEquals(6, seats);
    }

    @Test
    void executeWithLock_differentRooms_fullyParallel() throws Exception {
        var service = new GameSessionService();
        var completed = new java.util.concurrent.atomic.AtomicInteger();

        // Room A holds its lock for 2 seconds, Room B should still be able to acquire its own
        var t1 = new Thread(() -> {
            service.executeWithLock("A", () -> {
                completed.incrementAndGet();
                try { Thread.sleep(2000); } catch (Exception ignored) {}
            });
        });
        var t2 = new Thread(() -> {
            service.executeWithLock("B", () -> {
                completed.incrementAndGet();
            });
        });

        t1.start();
        Thread.sleep(100); // Let t1 acquire lock A
        t2.start();

        t2.join(3000);
        assertFalse(t2.isAlive(), "Thread B should finish even while Thread A holds lock A");
        assertEquals(2, completed.get());
    }
}
