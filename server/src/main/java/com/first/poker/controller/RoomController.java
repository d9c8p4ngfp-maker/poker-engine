package com.first.poker.controller;

import com.first.poker.dto.CreateRoomRequest;
import com.first.poker.dto.JoinRoomRequest;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import com.first.poker.model.enums.PlayerStatus;
import com.first.poker.service.RoomService;
import com.first.poker.service.GameSessionService;
import com.first.poker.service.BroadcastService;
import com.first.poker.service.GameBroadcastHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private static final Logger log = LoggerFactory.getLogger(RoomController.class);

    private final RoomService roomService;
    private final GameSessionService gameSessionService;
    private final BroadcastService broadcastService;
    private final GameBroadcastHelper helper;

    public RoomController(RoomService roomService, GameSessionService gameSessionService,
                          BroadcastService broadcastService, GameBroadcastHelper helper) {
        this.roomService = roomService;
        this.gameSessionService = gameSessionService;
        this.broadcastService = broadcastService;
        this.helper = helper;
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
        res.put("minPlayers", room.getConfig().getMinPlayers());
        res.put("initialChips", room.getConfig().getInitialChips());
        return res;
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

    @DeleteMapping("/{roomId}/bots/{botPlayerId}")
    public ResponseEntity<?> removeBot(@PathVariable String roomId, @PathVariable String botPlayerId) {
        var room = roomService.findRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        boolean removed = roomService.removeBot(roomId, botPlayerId);
        if (!removed) {
            return ResponseEntity.badRequest().body(Map.of("error", "移除失败"));
        }
        broadcastService.sendToRoom(roomId, roomToResponse(room));
        return ResponseEntity.ok(Map.of("roomId", roomId, "removed", botPlayerId, "playerCount", room.getPlayers().size()));
    }

    @PostMapping("/{roomId}/start")
    public ResponseEntity<?> startGame(@PathVariable String roomId) {
        var room = roomService.findRoom(roomId);
        if (room == null || room.getOwner() == null) return ResponseEntity.notFound().build();
        String ownerId = room.getOwner().getPlayerId();
        var state = gameSessionService.startGame(room, ownerId);
        room.setLastActivity(System.currentTimeMillis());
        helper.broadcastGameState(roomId, state);
        // Auto-play bot turns
        helper.autoPlayBots(roomId);
        return ResponseEntity.ok(Map.of(
            "status", "PLAYING",
            "phase", state.phase().name(),
            "players", state.players().size()
        ));
    }

    @PostMapping("/{roomId}/borrow")
    public ResponseEntity<?> borrowChips(@PathVariable String roomId, @RequestBody Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        if (playerId == null || playerId.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "missing playerId"));

        final int[] borrowAmount = {0};
        // Read config first (read-only, safe outside lock)
        var roomForConfig = roomService.findRoom(roomId);
        if (roomForConfig == null) return ResponseEntity.notFound().build();
        borrowAmount[0] = roomForConfig.getConfig().getInitialChips();
        if (body.containsKey("amount")) {
            borrowAmount[0] = ((Number) body.get("amount")).intValue();
        }

        gameSessionService.executeWithLock(roomId, () -> {
            var room = roomService.findRoom(roomId);
            if (room == null) return;
            var player = room.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst().orElse(null);
            if (player == null) return;
            if (room.getConfig().getBuyInRule() == RoomConfig.BuyInRule.ONCE_ONLY
                && player.getBorrowCount() > 0
                && player.getChips() > 0) {
                log.info("[BORROW] {} {} blocked — ONCE_ONLY and already borrowed (borrowCount={} chips={})",
                    roomId, playerId, player.getBorrowCount(), player.getChips());
                return;
            }
            player.borrow(borrowAmount[0]);
            player.setStatus(PlayerStatus.ACTIVE);
            room.setLastActivity(System.currentTimeMillis());
            log.info("[BORROW] {} {} borrowed #{} amount={}, chips={}", roomId, playerId, player.getBorrowCount(), borrowAmount[0], player.getChips());
        });

        // Broadcast outside lock (broadcast is thread-safe)
        var room = roomService.findRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        broadcastService.sendToRoom(roomId, roomToResponse(room));
        var p = room.getPlayers().stream().filter(pl -> pl.getPlayerId().equals(playerId)).findFirst().orElse(null);
        return ResponseEntity.ok(Map.of("playerId", playerId,
            "chips", p != null ? p.getChips() : 0,
            "borrowCount", p != null ? p.getBorrowCount() : 0));
    }

}
