package com.first.poker.service;

import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomRegistry {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room createRoom(String name, RoomConfig config) {
        String roomId = generateUniqueId();
        Room room = new Room(roomId, name, config);
        rooms.put(roomId, room);
        return room;
    }

    public Room findById(String roomId) {
        return rooms.get(roomId);
    }

    public List<Room> listPublicRooms() {
        return new ArrayList<>(rooms.values());
    }

    public boolean removeRoom(String roomId) {
        return rooms.remove(roomId) != null;
    }

    private String generateUniqueId() {
        String id;
        do {
            id = Room.generateRoomId();
        } while (rooms.containsKey(id));
        return id;
    }
}
