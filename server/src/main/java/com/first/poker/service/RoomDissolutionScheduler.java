package com.first.poker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class RoomDissolutionScheduler {
    private static final Logger log = LoggerFactory.getLogger(RoomDissolutionScheduler.class);

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
                log.info("[DISSOLVE-INACTIVE] {} inactive for 30+ minutes", roomId);
                gameSession.executeWithLock(roomId, () -> {
                    gameSession.endGameAndCleanupLock(roomId, null);
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
