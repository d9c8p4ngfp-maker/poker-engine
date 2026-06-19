package com.first.poker.controller;

import com.first.poker.dto.CreateRoomRequest;
import com.first.poker.dto.JoinRoomRequest;
import com.first.poker.model.Room;
import com.first.poker.service.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
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
        return ResponseEntity.ok(Map.of(
            "roomId", room.getRoomId(),
            "name", room.getName(),
            "status", room.getStatus().name(),
            "players", room.getPlayers().stream().map(p -> Map.of(
                "playerId", p.getPlayerId(),
                "nickname", p.getNickname(),
                "seatIndex", p.getSeatIndex(),
                "chips", p.getChips(),
                "connected", p.isConnected()
            )).toList(),
            "smallBlind", room.getConfig().getSmallBlind(),
            "bigBlind", room.getConfig().getBigBlind(),
            "dealerIndex", room.getDealerIndex()
        ));
    }
}
