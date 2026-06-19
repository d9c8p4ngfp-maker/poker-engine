package com.first.poker.service;

import com.first.poker.engine.*;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GameSessionService {

    private final ConcurrentHashMap<String, GameState> sessions = new ConcurrentHashMap<>();

    public GameState startGame(Room room, String requesterId) {
        String roomId = room.getRoomId();

        // Only owner can start
        if (room.getPlayers().isEmpty() || !room.getPlayers().get(0).getPlayerId().equals(requesterId)) {
            throw new IllegalArgumentException("Only room owner can start the game");
        }

        // Must not already be playing
        if (sessions.containsKey(roomId)) {
            throw new IllegalStateException("Game already in progress for room " + roomId);
        }

        // Convert to GamePlayerState
        List<GamePlayerState> players = room.getPlayers().stream()
            .map(GamePlayerState::fromPlayer)
            .toList();

        var result = GameEngine.startHand(players, room.getDealerIndex(), room.getConfig());
        sessions.put(roomId, result.state());
        return result.state();
    }

    public GameEngine.ActionResult applyAction(String roomId, String playerId, GameAction action, int amount) {
        GameState state = sessions.get(roomId);
        if (state == null) {
            throw new IllegalStateException("No active game in room " + roomId);
        }

        // Verify it's this player's turn
        var currentPlayer = state.currentPlayer();
        if (!currentPlayer.playerId().equals(playerId)) {
            throw new IllegalArgumentException("Not your turn: expected " + currentPlayer.playerId());
        }

        var result = GameEngine.processAction(state, action, amount);
        sessions.put(roomId, result.state());
        return result;
    }

    public GameState getState(String roomId) {
        return sessions.get(roomId);
    }

    public void endGame(String roomId) {
        sessions.remove(roomId);
    }
}
