package com.first.poker.service;

import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class RoomRegistryTest {

    private RoomRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RoomRegistry();
    }

    @Test
    void shouldCreateAndStoreRoom() {
        Room room = registry.createRoom("测试房", RoomConfig.withDefaults());
        assertNotNull(room);
        assertEquals(6, room.getRoomId().length());
        assertEquals("测试房", room.getName());
    }

    @Test
    void shouldFindRoomById() {
        Room created = registry.createRoom("房间A", RoomConfig.withDefaults());
        Room found = registry.findById(created.getRoomId());
        assertNotNull(found);
        assertEquals(created.getRoomId(), found.getRoomId());
    }

    @Test
    void shouldReturnNullForNonExistentRoom() {
        assertNull(registry.findById("NOEXIST"));
    }

    @Test
    void shouldListRooms() {
        registry.createRoom("A", RoomConfig.withDefaults());
        registry.createRoom("B", RoomConfig.withDefaults());
        List<Room> rooms = registry.listPublicRooms();
        assertEquals(2, rooms.size());
    }

    @Test
    void shouldRemoveRoom() {
        Room created = registry.createRoom("C", RoomConfig.withDefaults());
        assertTrue(registry.removeRoom(created.getRoomId()));
        assertNull(registry.findById(created.getRoomId()));
    }
}
