package com.first.poker.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class RoomDissolutionScheduler {

    private final RoomRegistry registry;
    private final GameSessionService gameSession;
    private final BroadcastService broadcast;

    public RoomDissolutionScheduler(RoomRegistry registry, GameSessionService gameSession,
                                     BroadcastService broadcast) {
        this.registry = registry;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void dissolveInactiveRooms() {
        long now = System.currentTimeMillis();
        long thirtyMinutes = 30 * 60 * 1000;

        for (var room : registry.listPublicRooms()) {
            if (room.getLastActivity() + thirtyMinutes < now) {
                String roomId = room.getRoomId();
                System.out.println("[DISSOLVE-INACTIVE] " + roomId + " inactive for 30+ minutes");
                gameSession.executeWithLock(roomId, () -> {
                    gameSession.endGame(roomId);
                    registry.removeRoom(roomId);
                    var payload = new HashMap<String, Object>();
                    payload.put("type", "room_dissolved");
                    payload.put("roomId", roomId);
                    broadcast.sendToRoom(roomId, payload);
                });
            }
        }
    }
}
