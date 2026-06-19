package com.first.poker.controller;

import java.util.ArrayList;

import com.first.poker.dto.GameActionRequest;
import com.first.poker.engine.GameAction;
import com.first.poker.engine.GameEngine;
import com.first.poker.engine.GameStateSnapshot;
import com.first.poker.model.Room;
import com.first.poker.model.Player;
import com.first.poker.service.BroadcastService;
import com.first.poker.service.GameDisconnectHandler;
import com.first.poker.service.GameSessionService;
import com.first.poker.service.GameTimeoutScheduler;
import com.first.poker.service.RoomRegistry;
import com.first.poker.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class GameMessageController {

    private final RoomService roomService;
    private final GameSessionService gameSession;
    private final BroadcastService broadcast;
    private final GameTimeoutScheduler timeoutScheduler;
    private final GameDisconnectHandler disconnectHandler;
    private final RoomRegistry registry;

    public GameMessageController(RoomService roomService, GameSessionService gameSession,
                                  BroadcastService broadcast, GameTimeoutScheduler timeoutScheduler,
                                  GameDisconnectHandler disconnectHandler, RoomRegistry registry) {
        this.roomService = roomService;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
        this.timeoutScheduler = timeoutScheduler;
        this.disconnectHandler = disconnectHandler;
        this.registry = registry;
    }

    @MessageMapping("/game/{roomId}/start")
    public void startGame(@DestinationVariable String roomId, @Payload GameActionRequest req) {
        System.out.println("[START-GAME] " + roomId + " requested by " + req.getPlayerId());
        try {
            var room = roomService.findRoom(roomId);
            if (room == null) throw new IllegalArgumentException("Room not found: " + roomId);

            var state = gameSession.startGame(room, req.getPlayerId());

            // Register all players for disconnect tracking
            for (var p : state.players()) {
                disconnectHandler.registerPlayer(roomId, p.playerId());
            }

            broadcastGameState(roomId, state);
            autoPlayBots(roomId);
        } catch (Throwable e) {
            System.err.println("[START-GAME-ERROR] " + roomId + ": " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace(System.err);
            var errorPayload = new java.util.HashMap<String, Object>();
            errorPayload.put("error", "无法开始游戏: " + e.getMessage());
            broadcast.sendToPlayer(req.getPlayerId(), errorPayload);
        }
    }

    @MessageMapping("/game/{roomId}/action")
    public void processAction(@DestinationVariable String roomId, @Payload GameActionRequest req) {
        System.out.println("[ACTION] " + roomId + " " + req.getPlayerId() + " " + req.getAction() + " amount=" + req.getAmount());
        try {
            GameAction action = GameAction.valueOf(req.getAction().toUpperCase());
            var result = gameSession.applyAction(roomId, req.getPlayerId(), action, req.getAmount());

            var state = result.state();
            System.out.println("[ACTION-RESULT] " + roomId + " curPlayer=" + state.currentPlayer().playerId() + " phase=" + state.phase() + " handComplete=" + result.handComplete());
            broadcastGameState(roomId, state);

            if (result.handComplete()) {
                System.out.println("[HAND-COMPLETE] " + roomId + " winners=" + result.winners());
                timeoutScheduler.cancelTimeout(roomId);
                syncRoomChips(roomId, result.state());
                gameSession.endGame(roomId);
                if (checkGameOver(roomId, result)) return;
                if (!result.winners().isEmpty()) {
                    broadcastWinners(roomId, result);
                }
                return;
            }

            autoPlayBots(roomId);
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
        }
    }

    @MessageMapping("/room/{roomId}/queue-accept")
    public void handleQueueAccept(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        System.out.println("[QUEUE-ACCEPT] " + roomId + " player=" + playerId);
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
    }

    @MessageMapping("/room/{roomId}/leave")
    public void handleLeave(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        System.out.println("[LEAVE] " + roomId + " player=" + playerId);
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
                broadcast.sendToRoom(roomId, dissolvePayload);
                gameSession.endGame(roomId);
                registry.removeRoom(roomId);
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
    }

    @MessageMapping("/room/{roomId}/dissolve")
    public void handleDissolve(@DestinationVariable String roomId, @Payload java.util.Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        System.out.println("[DISSOLVE] " + roomId + " requested by " + playerId);
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
        broadcast.sendToRoom(roomId, dissolvePayload);
        gameSession.endGame(roomId);
        registry.removeRoom(roomId);
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
            "dealerIndex", room.getDealerIndex()
        );
    }

    /**
     * Auto-play all pending bot turns. Bots always check when possible, otherwise call.
     */
    private void autoPlayBots(String roomId) {
        System.out.println("[AUTOBOTS-START] " + roomId);
        int safety = 0;
        while (safety++ < 30) {
            var state = gameSession.getState(roomId);
            if (state == null || state.currentPlayerIndex() < 0) break;
            var cp = state.currentPlayer();
            if (!cp.playerId().startsWith("bot-")) break;

            // Inactive bots (folded/all-in) cannot act; break as safety
            if (cp.folded() || cp.allIn()) {
                break;
            }

            int toCall = state.currentBet() - cp.roundBet();
            GameAction botAction = toCall <= 0 ? GameAction.CHECK : GameAction.CALL;
            int amount = 0;
            System.out.println("[AUTOBOT] " + roomId + " " + cp.playerId() + " action=" + botAction + " toCall=" + toCall + " chips=" + cp.chips());
            System.out.flush();

            try {
                var result = gameSession.applyAction(roomId, cp.playerId(), botAction, amount);
                broadcastGameState(roomId, result.state());

                if (result.handComplete()) {
                    timeoutScheduler.cancelTimeout(roomId);
                    syncRoomChips(roomId, result.state());
                    gameSession.endGame(roomId);
                    if (checkGameOver(roomId, result)) return;
                    if (!result.winners().isEmpty()) {
                        broadcastWinners(roomId, result);
                    }
                    return;
                }
            } catch (Throwable e) {
                System.err.println("[autoPlayBot] " + cp.playerId() + ": " + e.getClass().getName() + " - " + e.getMessage());
                continue;
            }
        }
    }

    private void broadcastGameState(String roomId, com.first.poker.engine.GameState state) {
        broadcast.sendToRoom(roomId, "game", GameStateSnapshot.buildPublic(state));
        for (var p : state.players()) {
            var privateSnapshot = GameStateSnapshot.buildForPlayer(state, p.playerId());
            broadcast.sendToPlayer(p.playerId(), privateSnapshot);
        }
    }

    private void broadcastWinners(String roomId, GameEngine.ActionResult result) {
        var winnerPayload = new java.util.HashMap<String, Object>();
        var winnersList = result.winners().stream()
            .map(w -> java.util.Map.of(
                "playerId", w.playerId(),
                "nickname", w.nickname(),
                "handName", w.handName(),
                "amount", w.amount()
            )).toList();
        winnerPayload.put("winners", winnersList);
        broadcast.sendToRoom(roomId, "game", winnerPayload);
    }

    private void syncRoomChips(String roomId, com.first.poker.engine.GameState resolvedState) {
        var room = roomService.findRoom(roomId);
        if (room == null) return;
        for (var gsp : resolvedState.players()) {
            room.getPlayers().stream()
                .filter(rp -> rp.getPlayerId().equals(gsp.playerId()))
                .findFirst()
                .ifPresent(rp -> {
                    int oldChips = rp.getChips();
                    int newChips = gsp.chips();
                    rp.setChips(newChips);
                    rp.setBetInRound(0);
                    rp.setFolded(false);
                    rp.setAllIn(false);
                    rp.setHoleCards(new ArrayList<>());
                    if (oldChips > 0 && newChips <= 0) {
                        broadcastBustChoice(roomId, rp.getPlayerId(), rp.getNickname());
                    }
                });
        }
        System.out.println("[SYNC-CHIPS] " + roomId + " updated player chips");
    }

    private void broadcastBustChoice(String roomId, String playerId, String nickname) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("type", "bust_choice");
        payload.put("playerId", playerId);
        payload.put("nickname", nickname);
        System.out.println("[BUST-CHOICE] sending to " + roomId + "/" + playerId);
        broadcast.sendToPlayer(playerId, payload);
    }

    private boolean checkGameOver(String roomId, GameEngine.ActionResult result) {
        var room = roomService.findRoom(roomId);
        if (room == null) return false;

        boolean anyBusted = room.getPlayers().stream().anyMatch(p -> p.getChips() <= 0);
        if (!anyBusted) return false;

        if (room.getConfig().isBustEndsGame()) {
            System.out.println("[GAME-OVER] " + roomId + " bust-ends-game mode, first bust detected");
            broadcastGameOver(roomId, room, result);
            return true;
        }

        // Not bustEndsGame: game ends when only 1 or 0 players have chips (last standing)
        long activePlayers = room.getPlayers().stream().filter(p -> p.getChips() > 0).count();
        if (activePlayers <= 1) {
            System.out.println("[GAME-OVER] " + roomId + " last standing (active=" + activePlayers + ")");
            broadcastGameOver(roomId, room, result);
            return true;
        }

        return false;
    }

    private void broadcastGameOver(String roomId, com.first.poker.model.Room room, GameEngine.ActionResult result) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("winners", result.winners().stream()
            .map(w -> java.util.Map.of(
                "playerId", w.playerId(),
                "nickname", w.nickname(),
                "handName", w.handName(),
                "amount", w.amount()
            )).toList());

        // Build leaderboard sorted by net chips descending (chips - borrowCount * initialChips)
        int borrowUnit = room.getConfig().getInitialChips();
        var leaderboard = room.getPlayers().stream()
            .sorted((a, b) -> Integer.compare(
                b.getChips() - b.getBorrowCount() * borrowUnit,
                a.getChips() - a.getBorrowCount() * borrowUnit))
            .map(p -> java.util.Map.of(
                "playerId", p.getPlayerId(),
                "nickname", p.getNickname(),
                "chips", p.getChips(),
                "borrowCount", p.getBorrowCount(),
                "borrowed", p.getBorrowCount() * borrowUnit,
                "netChips", p.getChips() - p.getBorrowCount() * borrowUnit
            )).toList();
        payload.put("leaderboard", leaderboard);
        payload.put("bustedPlayerIds", room.getPlayers().stream()
            .filter(p -> p.getChips() <= 0)
            .map(p -> p.getPlayerId()).toList());

        broadcast.sendToRoom(roomId, "game", payload);
    }
}
