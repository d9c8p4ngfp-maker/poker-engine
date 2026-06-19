package com.first.poker.controller;

import com.first.poker.dto.CreateRoomRequest;
import com.first.poker.dto.JoinRoomRequest;
import com.first.poker.model.Room;
import com.first.poker.service.RoomService;
import com.first.poker.service.GameSessionService;
import com.first.poker.service.BroadcastService;
import com.first.poker.engine.GameState;
import com.first.poker.engine.GameStateSnapshot;
import com.first.poker.engine.GameAction;
import com.first.poker.engine.GameEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final GameSessionService gameSessionService;
    private final BroadcastService broadcastService;

    public RoomController(RoomService roomService, GameSessionService gameSessionService,
                          BroadcastService broadcastService) {
        this.roomService = roomService;
        this.gameSessionService = gameSessionService;
        this.broadcastService = broadcastService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRoom(@RequestBody CreateRoomRequest req) {
        Room room = roomService.createRoom(req);
        return ResponseEntity.ok(Map.of(
            "roomId", room.getRoomId(),
            "name", room.getName(),
            "status", room.getStatus().name(),
            "config", room.getConfig()
        ));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, @RequestBody JoinRoomRequest req) {
        Room room = roomService.joinRoom(roomId, req);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(roomToResponse(room));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoom(@PathVariable String roomId) {
        Room room = roomService.findRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(roomToResponse(room));
    }

    private Map<String, Object> roomToResponse(Room room) {
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
                "connected", p.isConnected()
            )).toList(),
            "smallBlind", room.getConfig().getSmallBlind(),
            "bigBlind", room.getConfig().getBigBlind(),
            "maxSeats", room.getConfig().getMaxSeats(),
            "dealerIndex", room.getDealerIndex()
        );
    }

    @PostMapping("/{roomId}/bots")
    public ResponseEntity<?> addBots(@PathVariable String roomId, @RequestParam(defaultValue = "3") int count) {
        var bots = roomService.addBots(roomId, count);
        if (bots == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
            "roomId", roomId,
            "bots", bots.stream().map(b -> Map.of(
                "playerId", b.getPlayerId(),
                "nickname", b.getNickname()
            )).toList(),
            "playerCount", roomService.findRoom(roomId).getPlayers().size()
        ));
    }

    @PostMapping("/{roomId}/start")
    public ResponseEntity<?> startGame(@PathVariable String roomId) {
        var room = roomService.findRoom(roomId);
        if (room == null || room.getOwner() == null) return ResponseEntity.notFound().build();
        String ownerId = room.getOwner().getPlayerId();
        var state = gameSessionService.startGame(room, ownerId);
        // Broadcast to all subscribers
        broadcastService.sendToRoom(roomId, "game", GameStateSnapshot.buildPublic(state));
        for (var p : state.players()) {
            broadcastService.sendToPlayer(p.playerId(), GameStateSnapshot.buildForPlayer(state, p.playerId()));
        }
        // Auto-play bot turns
        autoBots(roomId);
        return ResponseEntity.ok(Map.of(
            "status", "PLAYING",
            "phase", state.phase().name(),
            "players", state.players().size()
        ));
    }

    @PostMapping("/{roomId}/borrow")
    public ResponseEntity<?> borrowChips(@PathVariable String roomId, @RequestBody Map<String, Object> body) {
        var room = roomService.findRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        String playerId = (String) body.get("playerId");
        if (playerId == null || playerId.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "missing playerId"));
        var player = room.getPlayers().stream().filter(p -> p.getPlayerId().equals(playerId)).findFirst().orElse(null);
        if (player == null) return ResponseEntity.badRequest().body(Map.of("error", "player not found"));

        int borrowAmount = room.getConfig().getInitialChips();
        if (body.containsKey("amount")) {
            borrowAmount = ((Number) body.get("amount")).intValue();
        }
        player.borrow(borrowAmount);
        System.out.println("[BORROW] " + roomId + " " + playerId + " borrowed #" + player.getBorrowCount() + " amount=" + borrowAmount + ", chips=" + player.getChips());
        broadcastService.sendToRoom(roomId, "room", roomToResponse(room));
        return ResponseEntity.ok(Map.of("playerId", playerId, "chips", player.getChips(), "borrowCount", player.getBorrowCount()));
    }

    private void autoBots(String roomId) {
        int safety = 0;
        while (safety++ < 30) {
            var state = gameSessionService.getState(roomId);
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

            try {
                var result = gameSessionService.applyAction(roomId, cp.playerId(), botAction, amount);
                broadcastService.sendToRoom(roomId, "game", GameStateSnapshot.buildPublic(result.state()));
                for (var p : result.state().players()) {
                    broadcastService.sendToPlayer(p.playerId(), GameStateSnapshot.buildForPlayer(result.state(), p.playerId()));
                }
                if (result.handComplete()) {
                    syncChips(roomId, result.state());
                    gameSessionService.endGame(roomId);
                    if (checkGameOver(roomId, result)) return;
                    if (!result.winners().isEmpty()) {
                        var winnerPayload = new java.util.HashMap<String, Object>();
                        winnerPayload.put("winners", result.winners().stream()
                            .map(w -> java.util.Map.of("playerId", w.playerId(), "nickname", w.nickname(), "handName", w.handName(), "amount", w.amount()))
                            .toList());
                        broadcastService.sendToRoom(roomId, "game", winnerPayload);
                    }
                    return;
                }
            } catch (Exception e) {
                System.err.println("[autoBots] " + cp.playerId() + ": " + e.getClass().getName() + " - " + e.getMessage());
                e.printStackTrace(System.err);
                continue; // skip this player, try next
            }
        }
    }

    private void syncChips(String roomId, GameState state) {
        var room = roomService.findRoom(roomId);
        if (room == null) return;
        for (var gsp : state.players()) {
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
                    rp.setHoleCards(new java.util.ArrayList<>());
                    if (oldChips > 0 && newChips <= 0) {
                        broadcastBustChoice(roomId, rp.getPlayerId(), rp.getNickname());
                    }
                });
        }
    }

    private void broadcastBustChoice(String roomId, String playerId, String nickname) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("type", "bust_choice");
        payload.put("playerId", playerId);
        payload.put("nickname", nickname);
        System.out.println("[BUST-CHOICE] sending to " + roomId + "/" + playerId);
        broadcastService.sendToPlayer(playerId, payload);
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

    private void broadcastGameOver(String roomId, Room room, GameEngine.ActionResult result) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("winners", result.winners().stream()
            .map(w -> java.util.Map.of(
                "playerId", w.playerId(), "nickname", w.nickname(),
                "handName", w.handName(), "amount", w.amount()))
            .toList());

        int borrowUnit = room.getConfig().getInitialChips();
        payload.put("leaderboard", room.getPlayers().stream()
            .sorted((a, b) -> Integer.compare(
                b.getChips() - b.getBorrowCount() * borrowUnit,
                a.getChips() - a.getBorrowCount() * borrowUnit))
            .map(p -> java.util.Map.of(
                "playerId", p.getPlayerId(), "nickname", p.getNickname(),
                "chips", p.getChips(), "borrowCount", p.getBorrowCount(),
                "borrowed", p.getBorrowCount() * borrowUnit,
                "netChips", p.getChips() - p.getBorrowCount() * borrowUnit))
            .toList());
        payload.put("bustedPlayerIds", room.getPlayers().stream()
            .filter(p -> p.getChips() <= 0).map(p -> p.getPlayerId()).toList());
        broadcastService.sendToRoom(roomId, "game", payload);
    }
}
