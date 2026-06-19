package com.first.poker.controller;

import com.first.poker.dto.GameActionRequest;
import com.first.poker.engine.GameAction;
import com.first.poker.engine.GameEngine;
import com.first.poker.engine.GameStateSnapshot;
import com.first.poker.service.BroadcastService;
import com.first.poker.service.GameDisconnectHandler;
import com.first.poker.service.GameSessionService;
import com.first.poker.service.GameTimeoutScheduler;
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

    public GameMessageController(RoomService roomService, GameSessionService gameSession,
                                  BroadcastService broadcast, GameTimeoutScheduler timeoutScheduler,
                                  GameDisconnectHandler disconnectHandler) {
        this.roomService = roomService;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
        this.timeoutScheduler = timeoutScheduler;
        this.disconnectHandler = disconnectHandler;
    }

    @MessageMapping("/game/{roomId}/start")
    public void startGame(@DestinationVariable String roomId, @Payload GameActionRequest req) {
        var room = roomService.findRoom(roomId);
        if (room == null) throw new IllegalArgumentException("Room not found: " + roomId);

        var state = gameSession.startGame(room, req.getPlayerId());

        // Register all players for disconnect tracking
        for (var p : state.players()) {
            disconnectHandler.registerPlayer(roomId, p.playerId());
        }

        broadcastGameState(roomId, state);

        // Schedule timeout for first player
        var currentPlayer = state.currentPlayer();
        timeoutScheduler.scheduleTimeout(roomId, currentPlayer.playerId(), 30);
    }

    @MessageMapping("/game/{roomId}/action")
    public void processAction(@DestinationVariable String roomId, @Payload GameActionRequest req) {
        GameAction action = GameAction.valueOf(req.getAction().toUpperCase());
        var result = gameSession.applyAction(roomId, req.getPlayerId(), action, req.getAmount());

        var state = result.state();

        broadcastGameState(roomId, state);

        // Handle hand complete
        if (result.handComplete()) {
            timeoutScheduler.cancelTimeout(roomId);
            if (!result.winners().isEmpty()) {
                broadcastWinners(roomId, result);
            }
            return;
        }

        // Schedule timeout for next player
        var currentPlayer = state.currentPlayer();
        timeoutScheduler.scheduleTimeout(roomId, currentPlayer.playerId(), 30);
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
}
