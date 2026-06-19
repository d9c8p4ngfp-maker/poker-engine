package com.first.poker.controller;

import com.first.poker.dto.GameActionRequest;
import com.first.poker.engine.GameAction;
import com.first.poker.engine.GameStateSnapshot;
import com.first.poker.service.BroadcastService;
import com.first.poker.service.GameSessionService;
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

    public GameMessageController(RoomService roomService, GameSessionService gameSession,
                                  BroadcastService broadcast) {
        this.roomService = roomService;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
    }

    @MessageMapping("/game/{roomId}/start")
    public void startGame(@DestinationVariable String roomId, @Payload GameActionRequest req) {
        var room = roomService.findRoom(roomId);
        if (room == null) throw new IllegalArgumentException("Room not found: " + roomId);

        var state = gameSession.startGame(room, req.getPlayerId());

        // Broadcast public snapshot to all
        broadcast.sendToRoom(roomId, "game", GameStateSnapshot.buildPublic(state));

        // Send private snapshot to each player
        for (var p : state.players()) {
            var privateSnapshot = GameStateSnapshot.buildForPlayer(state, p.playerId());
            broadcast.sendToPlayer(p.playerId(), privateSnapshot);
        }
    }

    @MessageMapping("/game/{roomId}/action")
    public void processAction(@DestinationVariable String roomId, @Payload GameActionRequest req) {
        GameAction action = GameAction.valueOf(req.getAction().toUpperCase());
        var result = gameSession.applyAction(roomId, req.getPlayerId(), action, req.getAmount());

        var state = result.state();

        // Broadcast public snapshot
        broadcast.sendToRoom(roomId, "game", GameStateSnapshot.buildPublic(state));

        // Send private snapshots
        for (var p : state.players()) {
            var privateSnapshot = GameStateSnapshot.buildForPlayer(state, p.playerId());
            broadcast.sendToPlayer(p.playerId(), privateSnapshot);
        }

        // If hand complete, broadcast winners
        if (result.handComplete() && !result.winners().isEmpty()) {
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
    }
}
