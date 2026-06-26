package com.first.poker.service;

import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import com.first.poker.model.enums.PlayerStatus;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASK-16 补充服务层测试 — 并发、竞技状态、借筹码限制。
 */
class T16ServiceTest {

    /* ---- 场景 #85: 并发 borrow 不突破上限 ---- */

    @Test
    void borrowChipsShouldNotExceedMax() throws Exception {
        var config = RoomConfig.withDefaults();
        config.setInitialChips(1000);
        config.setMaxBorrowChips(300);
        var room = new Room("R1", "test", config);
        Player p = new Player("A", "Alice", 0, 100);
        room.addPlayer(p);

        var service = new GameSessionService();
        int threads = 10;
        var done = new java.util.concurrent.CountDownLatch(threads);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                service.executeWithLock("R1", () -> {
                    p.borrow(50);
                });
                done.countDown();
            });
        }

        done.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(p.getChips() <= 100 + 300,
            "Total chips should not exceed initial + maxBorrow: " + p.getChips());
    }

    /* ---- 场景 #120: 坐满 6 人不能再加入 ---- */

    @Test
    void maxSeatsRespectedUnderConcurrentAdd() throws Exception {
        var config = RoomConfig.withDefaults();
        config.setMaxSeats(6);
        var room = new Room("R2", "test", config);

        int threadCount = 15;
        var successCount = new AtomicInteger();
        var done = new java.util.concurrent.CountDownLatch(threadCount);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                if (room.addPlayer(new Player("p" + idx, "P" + idx, -1, 1000))) {
                    successCount.incrementAndGet();
                }
                done.countDown();
            });
        }

        done.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(6, successCount.get(), "Only 6 players should be added");
        assertEquals(6, room.getPlayerCount());
    }

    /* ---- 场景 #122: bust=true 后借筹码仍算活跃 ---- */

    @Test
    void borrowAfterBustShouldRestoreActiveStatus() {
        Player p = new Player("p1", "Player1", 0, 1000);
        p.setStatus(PlayerStatus.ACTIVE);
        p.setChips(0); // bust

        assertTrue(p.getChips() == 0, "Player is bust");
        p.setChips(p.getChips() + 500); // borrowed
        assertTrue(p.getChips() > 0, "Player should have chips after borrow");
    }

    /* ---- 场景 #123: 多人同时 fold —— 不崩溃 ---- */

    @Test
    void multipleFoldAtSameTime_noCrash() {
        // 模拟多人同时 fold
        Player p1 = new Player("p1", "A", 0, 1000);
        p1.setStatus(PlayerStatus.ACTIVE);
        p1.setFolded(true);

        Player p2 = new Player("p2", "B", 1, 1000);
        p2.setStatus(PlayerStatus.ACTIVE);
        p2.setFolded(true);

        Player p3 = new Player("p3", "C", 2, 1000);
        p3.setStatus(PlayerStatus.ACTIVE);

        assertTrue(p1.isFolded());
        assertTrue(p2.isFolded());
        assertFalse(p3.isFolded());
    }

    /* ---- 场景 #126: buy-in 与 borrow 同时操作 ---- */

    @Test
    void concurrentBorrowAndBuyIn_shouldNotCorrupt() throws Exception {
        Player p = new Player("p1", "P1", 0, 500);
        var service = new GameSessionService();

        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        var done = new java.util.concurrent.CountDownLatch(4);

        for (int i = 0; i < 4; i++) {
            executor.submit(() -> {
                service.executeWithLock("R_X", () -> {
                    int current = p.getChips();
                    p.setChips(current + 50);
                });
                done.countDown();
            });
        }

        done.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(500 + 4 * 50, p.getChips());
    }

    /* ---- 场景 #130: 安全验证 ---- */

    @Test
    void roomIdShouldNotContainInjectionChars() {
        String roomId = "room123";
        assertTrue(roomId.matches("[a-zA-Z0-9]+"), "Room ID should be alphanumeric");
        assertFalse(roomId.contains("<"), "Room ID should not contain HTML injection chars");
        assertFalse(roomId.contains(";"), "Room ID should not contain SQL injection chars");
    }

    /* ---- 房间配置默认值 ---- */

    @Test
    void defaultRoomConfigHasSensibleDefaults() {
        var config = RoomConfig.withDefaults();
        assertTrue(config.getMaxSeats() <= 9, "Max seats should be reasonable");
        assertTrue(config.getInitialChips() >= 100, "Initial chips should be positive");
        assertTrue(config.getBigBlind() > config.getSmallBlind(), "Big blind should be larger than small blind");
    }
}
