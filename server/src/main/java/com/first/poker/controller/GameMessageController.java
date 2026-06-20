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

@Controller
@Validated
public class GameMessageController {

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
        System.out.println("[START-GAME] " + roomId + " requested by " + req.getPlayerId());
        try {
            var room = roomService.findRoom(roomId);
            if (room == null) throw new IllegalArgumentException("Room not found: " + roomId);

            var state = gameSession.startGame(room, req.getPlayerId());

            // Register ALL room players for disconnect tracking — not just hand
            // participants. A non-participating owner who disconnects mid-game
            // still needs grace-timer tracking so the room can be cleaned up.
            for (var rp : room.getPlayers()) {
                disconnectHandler.registerPlayer(roomId, rp.getPlayerId());
            }

            broadcastGameState(roomId, state);
            autoPlayBots(roomId);
            // Schedule timeout for the first human to act
            var initState = gameSession.getState(roomId);
            if (initState != null) helper.scheduleNextTimeout(roomId, initState);
        } catch (Throwable e) {
            System.err.println("[START-GAME-ERROR] " + roomId + ": " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace(System.err);
            var errorPayload = new java.util.HashMap<String, Object>();
            errorPayload.put("error", "无法开始游戏: " + e.getMessage());
            broadcast.sendToPlayer(req.getPlayerId(), errorPayload);
        }
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
        System.out.println("[ACTION] " + roomId + " " + req.getPlayerId() + " " + req.getAction() + " amount=" + req.getAmount());
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
            var result = gameSession.applyAction(roomId, req.getPlayerId(), action, req.getAmount());

            // Reset inactivity timer — any game action counts as activity
            // room is guaranteed to exist after applyAction succeeds (session exists => room exists)
            var room = roomService.findRoom(roomId);
            if (room != null) room.setLastActivity(System.currentTimeMillis());

            var state = result.state();
            System.out.println("[ACTION-RESULT] " + roomId + " curPlayer=" + state.currentPlayer().playerId() + " phase=" + state.phase() + " handComplete=" + result.handComplete());
            broadcastGameState(roomId, state);

            if (result.handComplete()) {
                System.out.println("[HAND-COMPLETE] " + roomId + " winners=" + result.winners());
                timeoutScheduler.cancelTimeout(roomId);
                com.first.poker.engine.GameState finalState = result.state();
                gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
                if (checkGameOver(roomId, result)) return;
                if (!result.winners().isEmpty()) {
                    broadcastWinners(roomId, result);
                }
                return;
            }

            autoPlayBots(roomId);
            // Schedule timeout for next human player (after bots finish)
            var currState = gameSession.getState(roomId);
            if (currState != null) helper.scheduleNextTimeout(roomId, currState);
        } catch (Throwable e) {
            System.err.println("[processAction] " + req.getPlayerId() + " " + req.getAction() + ": " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.flush();
            // Send error back to the player
            var errorPayload = new java.util.HashMap<String, Object>();
            errorPayload.put("error", e.getMessage());
            broadcast.sendToPlayer(req.getPlayerId(), errorPayload);
            // Try to auto-play bots in case the game should continue
            autoPlayBots(roomId);
            var retryState = gameSession.getState(roomId);
            if (retryState != null) helper.scheduleNextTimeout(roomId, retryState);
        }
    }

    @MessageMapping("/room/{roomId}/queue-accept")
    public void handleQueueAccept(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        System.out.println("[QUEUE-ACCEPT] " + roomId + " player=" + playerId);

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
                    broadcast.sendToRoom(roomId, "room", roomToResponse(room));
                });
        });
    }

    @MessageMapping("/room/{roomId}/leave")
    public void handleLeave(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        System.out.println("[LEAVE] " + roomId + " player=" + playerId);

        gameSession.executeWithLock(roomId, () -> {
            var room = roomService.findRoom(roomId);
            if (room == null) return;

            boolean wasOwner = room.getPlayers().stream()
                .anyMatch(p -> p.getPlayerId().equals(playerId) && p.isOwner());
            boolean isPlaying = gameSession.hasActiveSession(roomId);

            // Auto-fold if game is in progress
            if (isPlaying) {
                try {
                    gameSession.applyAction(roomId, playerId, GameAction.FOLD, 0);
                } catch (Exception e) {
                    System.out.println("[LEAVE-FOLD] " + playerId + " fold failed (may not be their turn): " + e.getMessage());
                }
            }

            disconnectHandler.cancelGraceTimer(playerId);
            roomService.leaveRoom(roomId, playerId);
            room.setLastActivity(System.currentTimeMillis());

            String newOwnerId = null;
            if (wasOwner) {
                if (roomService.hasHumanPlayers(roomId)) {
                    Player newOwner = roomService.transferOwnership(room, playerId);
                    newOwnerId = newOwner != null ? newOwner.getPlayerId() : null;
                } else {
                    // No human players left — dissolve room
                    System.out.println("[DISSOLVE] " + roomId + " all humans left");
                    var dissolvePayload = new java.util.HashMap<String, Object>();
                    dissolvePayload.put("type", "room_dissolved");
                    dissolvePayload.put("roomId", roomId);
                    gameSession.endGameAndCleanupLock(roomId, null);
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
            broadcast.sendToRoom(roomId, "room", roomToResponse(room));
        });
    }

    @MessageMapping("/room/{roomId}/dissolve")
    public void handleDissolve(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        System.out.println("[DISSOLVE] " + roomId + " requested by " + playerId);

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
            registry.removeRoom(roomId);
            broadcast.sendToRoom(roomId, dissolvePayload);
        });
    }

    private java.util.Map<String, Object> roomToResponse(Room room) {
        return java.util.Map.of(
            "roomId", room.getRoomId(),
            "name", room.getName(),
            "status", room.getStatus().name(),
            "players", room.getPlayers().stream().map(p -> java.util.Map.of(
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
            "dealerIndex", room.getDealerIndex(),
            "initialChips", room.getConfig().getInitialChips()
        );
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

    private void broadcastGameOver(String roomId, com.first.poker.model.Room room, GameEngine.ActionResult result) {
        helper.broadcastGameOver(roomId, room, result);
    }
}
