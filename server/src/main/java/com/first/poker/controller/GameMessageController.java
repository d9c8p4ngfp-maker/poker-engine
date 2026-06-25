package com.first.poker.controller;

import java.util.ArrayList;

import com.first.poker.dto.GameActionRequest;
import com.first.poker.engine.GameAction;
import com.first.poker.engine.GameEngine;
import com.first.poker.engine.GameStateSnapshot;
import com.first.poker.model.Room;
import com.first.poker.model.Player;
import com.first.poker.service.BroadcastService;
import com.first.poker.service.GameBroadcastHelper;
import com.first.poker.service.GameDisconnectHandler;
import com.first.poker.service.GameSessionService;
import com.first.poker.service.GameTimeoutScheduler;
import com.first.poker.service.RoomRegistry;
import com.first.poker.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@Validated
public class GameMessageController {

    private static final Logger log = LoggerFactory.getLogger(GameMessageController.class);

    private final RoomService roomService;
    private final GameSessionService gameSession;
    private final BroadcastService broadcast;
    private final GameTimeoutScheduler timeoutScheduler;
    private final GameDisconnectHandler disconnectHandler;
    private final RoomRegistry registry;
    private final GameBroadcastHelper helper;

    public GameMessageController(RoomService roomService, GameSessionService gameSession,
                                  BroadcastService broadcast, GameTimeoutScheduler timeoutScheduler,
                                  GameDisconnectHandler disconnectHandler, RoomRegistry registry,
                                  GameBroadcastHelper helper) {
        this.roomService = roomService;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
        this.timeoutScheduler = timeoutScheduler;
        this.disconnectHandler = disconnectHandler;
        this.registry = registry;
        this.helper = helper;
    }

    @MessageMapping("/game/{roomId}/start")
    public void startGame(@DestinationVariable String roomId, @Valid @Payload GameActionRequest req) {
        log.info("[START-GAME] {} requested by {}", roomId, req.getPlayerId());
        try {
            var room = roomService.findRoom(roomId);
            if (room == null) throw new IllegalArgumentException("Room not found: " + roomId);

            var state = gameSession.startGame(room, req.getPlayerId());

            // Register ALL room players for disconnect tracking
            for (var rp : room.getPlayers()) {
                disconnectHandler.registerPlayer(roomId, rp.getPlayerId());
            }

            autoPlayBots(roomId);
            // Broadcast game state BEFORE room status PLAYING so the frontend
            // has player/card/pot data ready when it switches to the table view.
            var initState = gameSession.getState(roomId);
            if (initState != null) {
                broadcastGameState(roomId, initState);
                helper.scheduleNextTimeout(roomId, initState);
            }
            // Now broadcast PLAYING — frontend switches to table with data already loaded
            broadcast.sendToRoom(roomId, roomToResponse(room));
            log.info("[START-GAME] {} broadcast room status={}", roomId, room.getStatus().name());
        } catch (Throwable e) {
            log.error("[START-GAME-ERROR] {}: {} - {}", roomId, e.getClass().getName(), e.getMessage(), e);
            e.printStackTrace(System.err);
            var errorPayload = new java.util.HashMap<String, Object>();
            errorPayload.put("error", "无法开始游戏: " + e.getMessage());
            broadcast.sendToPlayer(req.getPlayerId(), errorPayload);
        }
    }

    @MessageMapping("/game/{roomId}/ready")
    public void handleReady(@DestinationVariable String roomId,
                            @Payload java.util.Map<String, Object> payload) {
        String playerId = (String) payload.get("playerId");
        var room = roomService.findRoom(roomId);
        if (room == null || room.getStatus() != com.first.poker.model.enums.RoomStatus.WAITING) return;

        long activeCount = room.getPlayers().stream()
            .filter(p -> p.getStatus() == com.first.poker.model.enums.PlayerStatus.ACTIVE && p.getChips() > 0)
            .count();
        if (activeCount < room.getConfig().getMinPlayers()) return;

        gameSession.markReady(roomId, playerId);

        var activePlayers = room.getPlayers().stream()
            .filter(p -> p.getStatus() == com.first.poker.model.enums.PlayerStatus.ACTIVE && p.getChips() > 0)
            .map(com.first.poker.model.Player::getPlayerId)
            .toList();

        var readySet = gameSession.getReadyPlayers(roomId);
        var statusPayload = new java.util.HashMap<String, Object>();
        statusPayload.put("type", "ready_status");
        statusPayload.put("readyPlayers", readySet.stream().toList());
        statusPayload.put("totalActive", activePlayers.size());
        statusPayload.put("allReady", gameSession.allReady(roomId, activePlayers));
        broadcast.sendToRoom(roomId, statusPayload);

        log.info("[READY] {} player={} ready={}/{}", roomId, playerId,
                 readySet.size(), activePlayers.size());

        helper.tryAutoContinue(roomId);
    }

    @MessageMapping("/game/{roomId}/action")
    public void processAction(@DestinationVariable String roomId, @Valid @Payload GameActionRequest req) {
        // Manual validation: action is required for this endpoint but not for startGame
        if (req.getAction() == null || req.getAction().isBlank()) {
            var errorPayload = new java.util.HashMap<String, Object>();
            errorPayload.put("error", "action is required");
            broadcast.sendToPlayer(req.getPlayerId(), errorPayload);
            return;
        }
        log.info("[ACTION] {} {} {} amount={}", roomId, req.getPlayerId(), req.getAction(), req.getAmount());
        gameSession.executeWithLock(roomId, () -> {
            try {
                GameAction action;
                try {
                    action = GameAction.valueOf(req.getAction().toUpperCase());
                } catch (IllegalArgumentException e) {
                    var errorPayload = new java.util.HashMap<String, Object>();
                    errorPayload.put("error", "Invalid action: " + req.getAction());
                    broadcast.sendToPlayer(req.getPlayerId(), errorPayload);
                    return;
                }
                timeoutScheduler.cancelTimeout(roomId);
                var result = gameSession.applyActionNoLock(roomId, req.getPlayerId(), action, req.getAmount());
                var room = roomService.findRoom(roomId);
                if (room != null) room.setLastActivity(System.currentTimeMillis());
                if (result.handComplete()) {
                    log.info("[HAND-COMPLETE] {} winners={}", roomId, result.winners());
                    helper.endHandFlow(roomId, result);
                    return;
                }
                helper.broadcastGameState(roomId, result.state());
                helper.autoPlayBots(roomId);
                var currState = gameSession.getState(roomId);
                if (currState != null) helper.scheduleNextTimeout(roomId, currState);
            } catch (Throwable e) {
                log.error("[processAction] {} {}: {}", req.getPlayerId(), req.getAction(), e.getMessage(), e);
                var errorPayload = new java.util.HashMap<String, Object>();
                errorPayload.put("error", e.getMessage());
                broadcast.sendToPlayer(req.getPlayerId(), errorPayload);
            }
        });
    }

    @MessageMapping("/room/{roomId}/queue-accept")
    public void handleQueueAccept(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        log.info("[QUEUE-ACCEPT] {} player={}", roomId, playerId);

        gameSession.executeWithLock(roomId, () -> {
            var room = roomService.findRoom(roomId);
            if (room == null) return;

            room.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId)
                          && p.getStatus() == com.first.poker.model.enums.PlayerStatus.QUEUED)
                .findFirst()
                .ifPresent(p -> {
                    p.setStatus(com.first.poker.model.enums.PlayerStatus.ACTIVE);
                    p.setChips(room.getConfig().getInitialChips());
                    // Assign seat
                    int maxSeats = room.getConfig().getMaxSeats();
                    boolean[] occupied = new boolean[maxSeats];
                    for (var rp : room.getPlayers()) {
                        if (rp.getStatus() == com.first.poker.model.enums.PlayerStatus.ACTIVE
                            && rp.getSeatIndex() >= 0 && rp.getSeatIndex() < maxSeats) {
                            occupied[rp.getSeatIndex()] = true;
                        }
                    }
                    for (int i = 0; i < maxSeats; i++) {
                        if (!occupied[i]) {
                            p.setSeatIndex(i);
                            break;
                        }
                    }
                    broadcast.sendToRoom(roomId, roomToResponse(room));
                });
        });
    }

    @MessageMapping("/room/{roomId}/leave")
    public void handleLeave(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        log.info("[LEAVE] {} player={}", roomId, playerId);

        gameSession.executeWithLock(roomId, () -> {
            var room = roomService.findRoom(roomId);
            if (room == null) return;

            boolean wasOwner = room.getPlayers().stream()
                .anyMatch(p -> p.getPlayerId().equals(playerId) && p.isOwner());
            boolean isPlaying = gameSession.hasActiveSession(roomId);

            // Auto-fold if game is in progress
            if (isPlaying) {
                try {
                    var foldResult = gameSession.applyAction(roomId, playerId, GameAction.FOLD, 0);
                    if (foldResult.handComplete()) {
                        log.info("[LEAVE-HAND-COMPLETE] {} triggered by {} leaving", roomId, playerId);
                        helper.endHandFlow(roomId, foldResult);
                    } else {
                        helper.broadcastGameState(roomId, foldResult.state());
                        helper.autoPlayBots(roomId);
                        var postState = gameSession.getState(roomId);
                        if (postState != null) helper.scheduleNextTimeout(roomId, postState);
                    }
                } catch (Exception e) {
                    log.warn("[LEAVE-FOLD] {} fold failed (may not be their turn): {}", playerId, e.getMessage());
                }
            }

            disconnectHandler.cancelGraceTimer(playerId);
            if (isPlaying) {
                room.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setStatus(com.first.poker.model.enums.PlayerStatus.LEFT));
            } else {
                roomService.leaveRoom(roomId, playerId);
            }
            room.setLastActivity(System.currentTimeMillis());

            String newOwnerId = null;
            if (wasOwner) {
                if (roomService.hasHumanPlayers(roomId)) {
                    Player newOwner = roomService.transferOwnership(room, playerId);
                    newOwnerId = newOwner != null ? newOwner.getPlayerId() : null;
                } else {
                    // No human players left — dissolve room
                    log.info("[DISSOLVE] {} all humans left", roomId);
                    var dissolvePayload = new java.util.HashMap<String, Object>();
                    dissolvePayload.put("type", "room_dissolved");
                    dissolvePayload.put("roomId", roomId);
                    gameSession.endGameAndCleanupLock(roomId, null);
                    gameSession.clearReady(roomId);
                    registry.removeRoom(roomId);
                    broadcast.sendToRoom(roomId, dissolvePayload);
                    return;
                }
            }

            var leavePayload = new java.util.HashMap<String, Object>();
            leavePayload.put("type", "player_left");
            leavePayload.put("playerId", playerId);
            if (newOwnerId != null) {
                leavePayload.put("newOwnerId", newOwnerId);
            }
            broadcast.sendToRoom(roomId, leavePayload);
            broadcast.sendToRoom(roomId, roomToResponse(room));

            // Update ready status when player leaves during WAITING phase
            if (room.getStatus() == com.first.poker.model.enums.RoomStatus.WAITING) {
                gameSession.removeReady(roomId, playerId);
                var activePlayers = room.getPlayers().stream()
                    .filter(p -> p.getStatus() == com.first.poker.model.enums.PlayerStatus.ACTIVE && p.getChips() > 0)
                    .map(p -> p.getPlayerId())
                    .toList();
                if (!activePlayers.isEmpty()) {
                    var readyPayload = new java.util.HashMap<String, Object>();
                    readyPayload.put("type", "ready_status");
                    readyPayload.put("readyPlayers", gameSession.getReadyPlayers(roomId).stream().toList());
                    readyPayload.put("totalActive", activePlayers.size());
                    readyPayload.put("allReady", gameSession.allReady(roomId, activePlayers));
                    broadcast.sendToRoom(roomId, readyPayload);
                }
            }
        });
    }

    @MessageMapping("/room/{roomId}/dissolve")
    public void handleDissolve(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        log.info("[DISSOLVE] {} requested by {}", roomId, playerId);

        gameSession.executeWithLock(roomId, () -> {
            var room = roomService.findRoom(roomId);
            if (room == null) return;

            // Verify owner
            if (room.getOwner() == null || !room.getOwner().getPlayerId().equals(playerId)) {
                var err = new java.util.HashMap<String, Object>();
                err.put("error", "Only room owner can dissolve");
                broadcast.sendToPlayer(playerId, err);
                return;
            }

            var dissolvePayload = new java.util.HashMap<String, Object>();
            dissolvePayload.put("type", "room_dissolved");
            dissolvePayload.put("roomId", roomId);
            gameSession.endGameAndCleanupLock(roomId, null);
            gameSession.clearReady(roomId);
            registry.removeRoom(roomId);
            broadcast.sendToRoom(roomId, dissolvePayload);
        });
    }

    private java.util.Map<String, Object> roomToResponse(Room room) {
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("roomId", room.getRoomId());
        res.put("name", room.getName());
        res.put("status", room.getStatus().name());
        res.put("players", room.getPlayers().stream().map(p -> {
            java.util.Map<String, Object> pm = new java.util.HashMap<>();
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

    private void autoPlayBots(String roomId) {
        helper.autoPlayBots(roomId);
    }

    private void broadcastGameState(String roomId, com.first.poker.engine.GameState state) {
        helper.broadcastGameState(roomId, state);
    }

    private void broadcastWinners(String roomId, GameEngine.ActionResult result) {
        helper.broadcastWinners(roomId, result);
    }

    private void syncRoomChips(String roomId, com.first.poker.engine.GameState resolvedState) {
        helper.syncRoomChips(roomId, resolvedState);
    }

    private void broadcastBustChoice(String roomId, String playerId, String nickname) {
        helper.broadcastBustChoice(roomId, playerId, nickname);
    }

    private boolean checkGameOver(String roomId, GameEngine.ActionResult result) {
        return helper.checkGameOver(roomId, result);
    }

    private void checkAndApplyBonuses(String roomId, Room room,
            com.first.poker.engine.GameState state, GameEngine.ActionResult result) {
        helper.checkAndApplyBonuses(roomId, room, state, result);
    }

    private void broadcastGameOver(String roomId, com.first.poker.model.Room room, GameEngine.ActionResult result) {
        helper.broadcastGameOver(roomId, room, result);
    }
}
