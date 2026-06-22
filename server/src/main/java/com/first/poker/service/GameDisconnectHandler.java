package com.first.poker.service;

import com.first.poker.engine.GameAction;
import com.first.poker.engine.GameEngine;
import com.first.poker.engine.GameState;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.enums.PlayerStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import jakarta.annotation.PreDestroy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class GameDisconnectHandler {

    private final Map<String, String> playerRooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> graceTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService graceExecutor = Executors.newScheduledThreadPool(4);
    private final RoomService roomService;
    private final GameSessionService gameSession;
    private final BroadcastService broadcast;
    private final RoomRegistry registry;
    private final GameBroadcastHelper broadcastHelper;

    public GameDisconnectHandler(RoomService roomService, GameSessionService gameSession,
                                  BroadcastService broadcast, RoomRegistry registry,
                                  GameBroadcastHelper broadcastHelper) {
        this.roomService = roomService;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
        this.registry = registry;
        this.broadcastHelper = broadcastHelper;
    }

    @PreDestroy
    public void shutdown() {
        graceExecutor.shutdown();
        System.out.println("[DISCONNECT] Grace executor shut down");
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

    public void cancelGraceTimer(String playerId) {
        ScheduledFuture<?> timer = graceTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
            System.out.println("[GRACE-CANCEL] " + playerId + " grace timer cancelled (player left)");
        }
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

        // Phase 1: Mark player as DISCONNECTED and auto-fold under lock.
        // Capture the ActionResult so we can handle handComplete properly.
        AtomicReference<GameEngine.ActionResult> foldResult = new AtomicReference<>(null);
        gameSession.executeWithLock(fRoomId, () -> {
            var room = roomService.findRoom(fRoomId);
            if (room == null) return;

            room.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(fPlayerId))
                .findFirst()
                .ifPresent(p -> {
                    boolean isPlaying = gameSession.hasActiveSession(fRoomId);
                    if (isPlaying) {
                        p.setStatus(PlayerStatus.DISCONNECTED);
                    }
                    p.setConnected(false);
                });

            room.setLastActivity(System.currentTimeMillis());

            boolean isPlaying = gameSession.hasActiveSession(fRoomId);

            // Auto-fold if it's the player's turn
            if (isPlaying) {
                try {
                    var state = gameSession.getState(fRoomId);
                    if (state != null && state.currentPlayer().playerId().equals(fPlayerId)) {
                        GameEngine.ActionResult result = gameSession.applyAction(fRoomId, fPlayerId, GameAction.FOLD, 0);
                        foldResult.set(result);
                    }
                } catch (Exception e) {
                    System.out.println("[DISCONNECT-FOLD] " + fPlayerId + " fold failed: " + e.getMessage());
                }
            }
        });

        GameEngine.ActionResult fr = foldResult.get();

        // Phase 2: Broadcast disconnect notice + updated state + handle handComplete
        var dcPayload = new HashMap<String, Object>();
        dcPayload.put("type", "player_disconnected");
        dcPayload.put("playerId", fPlayerId);
        broadcast.sendToRoom(fRoomId, dcPayload);

        if (fr != null) {
            broadcastHelper.broadcastGameState(fRoomId, fr.state());

            if (fr.handComplete()) {
                System.out.println("[DISCONNECT-HAND-COMPLETE] " + fRoomId + " triggered by " + fPlayerId + " disconnect-fold");
                gameSession.endGame(fRoomId, () -> {
                    var finalRoom = roomService.findRoom(fRoomId);
                    if (finalRoom != null) broadcastHelper.syncRoomChips(fRoomId, fr.state());
                });
                if (broadcastHelper.checkGameOver(fRoomId, fr)) {
                    // game-over broadcast already sent by checkGameOver
                } else if (!fr.winners().isEmpty()) {
                    broadcastHelper.broadcastWinners(fRoomId, fr);
                }
            } else {
                // Hand not complete — let autoPlayBots continue
                broadcastHelper.autoPlayBots(fRoomId);
                var state = gameSession.getState(fRoomId);
                if (state != null) broadcastHelper.scheduleNextTimeout(fRoomId, state);
            }
        }

        // Phase 3: Grace period timer (5 min) — prevents temporary disconnections
        // (refresh, WiFi blip, switch tabs) from prematurely removing the player.
        // Only truly long absences trigger expiry.
        ScheduledFuture<?> timer = graceExecutor.schedule(() -> {
            gameSession.executeWithLock(fRoomId, () -> {
                try {
                    var r = roomService.findRoom(fRoomId);
                    if (r == null) return;
                    r.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(fPlayerId)
                                  && p.getStatus() == PlayerStatus.DISCONNECTED)
                        .findFirst()
                        .ifPresent(p -> {
                            p.setStatus(PlayerStatus.LEFT);
                            graceTimers.remove(fPlayerId);
                            System.out.println("[DISCONNECT-EXPIRE] " + fPlayerId + " marked LEFT in " + fRoomId);
                        });
                    broadcast.sendToRoom(fRoomId, "room", roomToUpdatedResponse(r));
                } catch (Exception e) {
                    System.err.println("[DISCONNECT-EXPIRE-ERROR] " + fPlayerId + ": " + e.getMessage());
                }
            });
        }, 300, TimeUnit.SECONDS);
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
        Map<String, Object> res = new HashMap<>();
        res.put("roomId", room.getRoomId());
        res.put("name", room.getName());
        res.put("status", room.getStatus().name());
        res.put("players", room.getPlayers().stream().map(p -> {
            Map<String, Object> pm = new HashMap<>();
            pm.put("playerId", p.getPlayerId());
            pm.put("nickname", p.getNickname());
            pm.put("seatIndex", p.getSeatIndex());
            pm.put("chips", p.getChips());
            pm.put("borrowCount", p.getBorrowCount());
            pm.put("connected", p.isConnected());
            pm.put("owner", p.isOwner());
            return pm;
        }).toList());
        res.put("smallBlind", room.getConfig().getSmallBlind());
        res.put("bigBlind", room.getConfig().getBigBlind());
        res.put("maxSeats", room.getConfig().getMaxSeats());
        res.put("dealerPlayerId", room.getDealerPlayerId());
        res.put("initialChips", room.getConfig().getInitialChips());
        return res;
    }
}
