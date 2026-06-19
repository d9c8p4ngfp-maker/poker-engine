package com.first.poker.service;

import com.first.poker.engine.*;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class GameSessionService {

    private final ConcurrentHashMap<String, GameState> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();

    public boolean hasActiveSession(String roomId) {
        return sessions.containsKey(roomId);
    }

    /**
     * Execute a runnable under the per-room lock.
     * Creates the lock if it doesn't exist yet for this room.
     */
    public void executeWithLock(String roomId, Runnable task) {
        ReentrantLock lock = roomLocks.computeIfAbsent(roomId, k -> new ReentrantLock());
        lock.lock();
        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }

    public GameState startGame(Room room, String requesterId) {
        String roomId = room.getRoomId();
        ReentrantLock lock = roomLocks.computeIfAbsent(roomId, k -> new ReentrantLock());
        lock.lock();
        try {
            // Only owner can start
            if (room.getOwner() == null || !room.getOwner().getPlayerId().equals(requesterId)) {
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
        } finally {
            lock.unlock();
        }
    }

    public GameEngine.ActionResult applyAction(String roomId, String playerId, GameAction action, int amount) {
        ReentrantLock lock = roomLocks.computeIfAbsent(roomId, k -> new ReentrantLock());
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public GameState getState(String roomId) {
        return sessions.get(roomId);
    }

    /**
     * End the game session. The beforeUnlock task (e.g. syncRoomChips)
     * executes inside the per-room lock before the session is removed.
     */
    public void endGame(String roomId, Runnable beforeUnlock) {
        ReentrantLock lock = roomLocks.get(roomId);
        if (lock != null) {
            lock.lock();
            try {
                if (beforeUnlock != null) {
                    beforeUnlock.run();
                }
                sessions.remove(roomId);
            } finally {
                lock.unlock();
            }
        } else {
            if (beforeUnlock != null) {
                beforeUnlock.run();
            }
            sessions.remove(roomId);
        }
    }

    /** Backward-compatible overload for callers that don't need a pre-unlock task. */
    public void endGame(String roomId) {
        endGame(roomId, null);
    }
}
