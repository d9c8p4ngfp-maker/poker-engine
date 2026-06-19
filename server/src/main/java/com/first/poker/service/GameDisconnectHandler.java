package com.first.poker.service;

import com.first.poker.engine.GameAction;
import com.first.poker.model.Player;
import com.first.poker.model.enums.PlayerStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class GameDisconnectHandler {

    private final Map<String, String> playerRooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> graceTimers = new ConcurrentHashMap<>();
    private final RoomService roomService;
    private final GameSessionService gameSession;
    private final BroadcastService broadcast;
    private final RoomRegistry registry;

    public GameDisconnectHandler(RoomService roomService, GameSessionService gameSession,
                                  BroadcastService broadcast, RoomRegistry registry) {
        this.roomService = roomService;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
        this.registry = registry;
    }

    public void registerPlayer(String roomId, String playerId) {
        playerRooms.put(playerId, roomId);
    }

    public void registerSession(String sessionId, String playerId) {
        sessionToPlayer.put(sessionId, playerId);
    }

    public void unregisterPlayer(String playerId) {
        playerRooms.remove(playerId);
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String playerId = sessionToPlayer.get(sessionId);
        if (playerId == null && event.getUser() != null) {
            playerId = event.getUser().getName();
        }
        if (playerId == null) return;

        String roomId = playerRooms.get(playerId);
        if (roomId == null) return;

        System.out.println("[DISCONNECT] session=" + sessionId + " player=" + playerId + " room=" + roomId);

        // Capture final copies for lambdas
        final String fPlayerId = playerId;
        final String fRoomId = roomId;
        final String fSessionId = sessionId;

        // Phase 1: Mark player as DISCONNECTED and auto-fold under lock
        gameSession.executeWithLock(fRoomId, () -> {
            var room = roomService.findRoom(fRoomId);
            if (room == null) return;

            room.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(fPlayerId))
                .findFirst()
                .ifPresent(p -> {
                    p.setStatus(PlayerStatus.DISCONNECTED);
                    p.setConnected(false);
                });

            room.setLastActivity(System.currentTimeMillis());

            boolean isPlaying = gameSession.hasActiveSession(fRoomId);

            // Auto-fold if it's the player's turn
            if (isPlaying) {
                try {
                    var state = gameSession.getState(fRoomId);
                    if (state != null && state.currentPlayer().playerId().equals(fPlayerId)) {
                        gameSession.applyAction(fRoomId, fPlayerId, GameAction.FOLD, 0);
                    }
                } catch (Exception e) {
                    System.out.println("[DISCONNECT-FOLD] " + fPlayerId + " fold failed: " + e.getMessage());
                }
            }
        });

        // Phase 2: Broadcast (outside lock, broadcast is thread-safe)
        var payload = new HashMap<String, Object>();
        payload.put("type", "player_disconnected");
        payload.put("playerId", fPlayerId);
        broadcast.sendToRoom(fRoomId, payload);

        // Phase 3: Grace period timer (60s) — accesses under lock
        var executor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> timer = executor.schedule(() -> {
            gameSession.executeWithLock(fRoomId, () -> {
                try {
                    var r = roomService.findRoom(fRoomId);
                    if (r == null) return;
                    r.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(fPlayerId)
                                  && p.getStatus() == PlayerStatus.DISCONNECTED)
                        .findFirst()
                        .ifPresent(p -> {
                            r.removePlayer(fPlayerId);
                            sessionToPlayer.remove(fSessionId);
                            playerRooms.remove(fPlayerId);
                            graceTimers.remove(fPlayerId);
                            System.out.println("[DISCONNECT-EXPIRE] " + fPlayerId + " removed from " + fRoomId);
                        });
                    broadcast.sendToRoom(fRoomId, "room", roomToUpdatedResponse(r));
                } catch (Exception e) {
                    System.err.println("[DISCONNECT-EXPIRE-ERROR] " + fPlayerId + ": " + e.getMessage());
                }
            });
            executor.shutdown();
        }, 60, TimeUnit.SECONDS);
        graceTimers.put(fPlayerId, timer);
    }

    public void onReconnect(String playerId) {
        ScheduledFuture<?> timer = graceTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
        }
        var roomId = playerRooms.get(playerId);
        if (roomId != null) {
            gameSession.executeWithLock(roomId, () -> {
                var room = roomService.findRoom(roomId);
                if (room != null) {
                    room.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst()
                        .ifPresent(p -> {
                            p.setStatus(PlayerStatus.ACTIVE);
                            p.setConnected(true);
                        });
                    room.setLastActivity(System.currentTimeMillis());
                }
            });
        }
    }

    private Map<String, Object> roomToUpdatedResponse(com.first.poker.model.Room room) {
        return Map.of(
            "roomId", room.getRoomId(),
            "name", room.getName(),
            "status", room.getStatus().name(),
            "players", room.getPlayers().stream().map(p -> Map.of(
                "playerId", p.getPlayerId(),
                "nickname", p.getNickname(),
                "seatIndex", p.getSeatIndex(),
                "chips", p.getChips(),
                "borrowCount", p.getBorrowCount(),
                "connected", p.isConnected(),
                "owner", p.isOwner()
            )).toList(),
            "smallBlind", room.getConfig().getSmallBlind(),
            "bigBlind", room.getConfig().getBigBlind(),
            "maxSeats", room.getConfig().getMaxSeats(),
            "dealerIndex", room.getDealerIndex()
        );
    }
}
