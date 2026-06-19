package com.first.poker.model;

import com.first.poker.model.enums.RoomStatus;
import org.junit.jupiter.api.Test;
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
}
