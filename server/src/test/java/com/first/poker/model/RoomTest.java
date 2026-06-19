package com.first.poker.model;

import com.first.poker.model.enums.RoomStatus;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RoomTest {

    @Test
    void shouldCreateRoomWithIdAndConfig() {
        RoomConfig config = RoomConfig.withDefaults();
        Room room = new Room("ABC123", "测试房间", config);

        assertEquals("ABC123", room.getRoomId());
        assertEquals("测试房间", room.getName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertEquals(0, room.getPlayers().size());
        assertEquals(config, room.getConfig());
    }

    @Test
    void shouldAddPlayerToRoom() {
        Room room = new Room("R001", "测试", RoomConfig.withDefaults());
        Player p = new Player("p1", "Alice", 0, 1000);

        boolean ok = room.addPlayer(p);
        assertTrue(ok);
        assertEquals(1, room.getPlayers().size());
        assertEquals("Alice", room.getPlayers().get(0).getNickname());
    }

    @Test
    void shouldRejectDuplicatePlayer() {
        Room room = new Room("R001", "测试", RoomConfig.withDefaults());
        room.addPlayer(new Player("p1", "Alice", 0, 1000));
        boolean ok = room.addPlayer(new Player("p1", "Alice2", 1, 1000));
        assertFalse(ok);
        assertEquals(1, room.getPlayers().size());
    }

    @Test
    void shouldRejectWhenFull() {
        RoomConfig config = RoomConfig.withDefaults();
        config.setMaxSeats(2);
        Room room = new Room("R001", "测试", config);
        assertTrue(room.addPlayer(new Player("p1", "A", 0, 1000)));
        assertTrue(room.addPlayer(new Player("p2", "B", 1, 1000)));
        assertFalse(room.addPlayer(new Player("p3", "C", 2, 1000)));
    }

    @Test
    void shouldRemovePlayer() {
        Room room = new Room("R001", "测试", RoomConfig.withDefaults());
        room.addPlayer(new Player("p1", "Alice", 0, 1000));
        boolean removed = room.removePlayer("p1");
        assertTrue(removed);
        assertEquals(0, room.getPlayers().size());
    }

    @Test
    void shouldGenerateSixCharRoomId() {
        String id = Room.generateRoomId();
        assertEquals(6, id.length());
    }

    @Test
    void addPlayer_shouldAssignSeatIndexInsideSync() {
        Room room = new Room("R001", "测试", RoomConfig.withDefaults());
        Player p = new Player("p1", "Alice", -1, 1000);
        boolean ok = room.addPlayer(p);
        assertTrue(ok);
        assertEquals(0, p.getSeatIndex());
    }

    @Test
    void addPlayer_shouldAssignSequentialSeatIndices() {
        Room room = new Room("R001", "测试", RoomConfig.withDefaults());
        Player p1 = new Player("p1", "A", -1, 1000);
        Player p2 = new Player("p2", "B", -1, 1000);
        Player p3 = new Player("p3", "C", -1, 1000);
        assertTrue(room.addPlayer(p1));
        assertTrue(room.addPlayer(p2));
        assertTrue(room.addPlayer(p3));
        assertEquals(0, p1.getSeatIndex());
        assertEquals(1, p2.getSeatIndex());
        assertEquals(2, p3.getSeatIndex());
    }

    @Test
    void addPlayer_concurrentShouldNotExceedMaxSeats() throws Exception {
        RoomConfig config = RoomConfig.withDefaults();
        config.setMaxSeats(3);
        Room room = new Room("R001", "测试", config);
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                if (room.addPlayer(new Player("p" + idx, "Player" + idx, -1, 1000))) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            });
        }
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertEquals(3, successCount.get());
        assertEquals(3, room.getPlayers().size());
    }
}
