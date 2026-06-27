package com.first.poker.service;

import com.first.poker.engine.*;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GameSessionService {

    private static final Logger log = LoggerFactory.getLogger(GameSessionService.class);

    private final ConcurrentHashMap<String, GameState> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.Set<String>> readyPlayers = new ConcurrentHashMap<>();

    public void markReady(String roomId, String playerId) {
        readyPlayers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public java.util.Set<String> getReadyPlayers(String roomId) {
        return readyPlayers.getOrDefault(roomId, java.util.Set.of());
    }

    public void clearReady(String roomId) {
        readyPlayers.remove(roomId);
    }

    public void removeReady(String roomId, String playerId) {
        var set = readyPlayers.get(roomId);
        if (set != null) set.remove(playerId);
    }

    public boolean allReady(String roomId, java.util.List<String> activePlayers) {
        var ready = getReadyPlayers(roomId);
        return activePlayers.stream().allMatch(ready::contains);
    }

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
            clearReady(roomId);
            room.cleanupLeftPlayers();
            // Only owner can start
            if (room.getOwner() == null || !room.getOwner().getPlayerId().equals(requesterId)) {
                throw new IllegalArgumentException("Only room owner can start the game");
            }

            // Must not already be playing.
            // If auto-continue already started the game, treat manual start as success
            // (owner's intent was fulfilled) rather than throwing an error.
            if (sessions.containsKey(roomId)) {
                log.info("[START-GAME] {} already in progress — treating as idempotent success", roomId);
                return null;
            }

            // Convert to GamePlayerState — only ACTIVE players with chips participate.
            // Players with 0 chips (busted, not yet borrowed) are excluded
            // without changing their PlayerStatus, which is owned by the room layer.
            List<GamePlayerState> players = room.getPlayers().stream()
                .filter(p -> p.getStatus() == com.first.poker.model.enums.PlayerStatus.ACTIVE
                          && p.getChips() > 0)
                .map(GamePlayerState::fromPlayer)
                .toList();

            if (players.size() < 2) {
                throw new IllegalArgumentException(
                    "Need at least 2 players with chips to start; only " + players.size()
                    + " has chips. Please borrow chips for 0-chip players first."
                );
            }

            room.setStatus(com.first.poker.model.enums.RoomStatus.PLAYING);

            // On first hand, dealerPlayerId is null. Set it to the first eligible player.
            if (room.getDealerPlayerId() == null) {
                room.advanceDealer();
            }

            // Map dealerPlayerId to index in the filtered participants list
            int dealerIdx = 0;
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i).playerId().equals(room.getDealerPlayerId())) {
                    dealerIdx = i;
                    break;
                }
            }

            var result = GameEngine.startHand(players, dealerIdx, room.getConfig());
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

    public GameEngine.ActionResult applyActionNoLock(String roomId, String playerId, GameAction action, int amount) {
        GameState state = sessions.get(roomId);
        if (state == null) {
            throw new IllegalStateException("No active game in room " + roomId);
        }
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
                log.info("[END-GAME] {} session removed, remaining sessions: {}", roomId, sessions.size());
            } finally {
                lock.unlock();
            }
        } else {
            if (beforeUnlock != null) {
                beforeUnlock.run();
            }
            sessions.remove(roomId);
            log.info("[END-GAME] {} session removed (no lock), remaining: {}", roomId, sessions.size());
        }
    }

    /** Backward-compatible overload for callers that don't need a pre-unlock task. */
    public void endGame(String roomId) {
        endGame(roomId, null);
    }

    /**
     * End game and clean up the per-room lock. Use this only when the room
     * is fully dissolving (removeRoom follows). For normal hand-complete
     * endGame, use the plain endGame overload instead.
     */
    public void endGameAndCleanupLock(String roomId, Runnable beforeUnlock) {
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
            roomLocks.remove(roomId);
        } else {
            if (beforeUnlock != null) {
                beforeUnlock.run();
            }
            sessions.remove(roomId);
        }
    }
}
