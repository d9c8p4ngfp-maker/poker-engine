package com.first.poker.service;

import com.first.poker.dto.CreateRoomRequest;
import com.first.poker.dto.JoinRoomRequest;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class RoomService {
    private final RoomRegistry registry;
    private final GameSessionService gameSessionService;
    private final GameDisconnectHandler disconnectHandler;

    public RoomService(RoomRegistry registry, GameSessionService gameSessionService,
                        @Lazy GameDisconnectHandler disconnectHandler) {
        this.registry = registry;
        this.gameSessionService = gameSessionService;
        this.disconnectHandler = disconnectHandler;
    }

    public Room createRoom(CreateRoomRequest req) {
        RoomConfig config = RoomConfig.withDefaults();
        applyConfig(config, req);
        String roomName = req.getRoomName() != null ? req.getRoomName()
            : (req.getName() != null ? req.getName() : "默认牌局");
        Room room = registry.createRoom(roomName, config);
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            room.setPassword(req.getPassword());
        }

        // Auto-add owner as first player
        if (req.getOwnerId() != null) {
            String nickname = req.getOwnerNickname() != null ? req.getOwnerNickname() : req.getOwnerId();
            Player owner = new Player(req.getOwnerId(), nickname, -1, config.getInitialChips());
            owner.setOwner(true);
            room.addPlayer(owner);
            room.setOwner(owner);
            disconnectHandler.registerPlayer(room.getRoomId(), req.getOwnerId());
        }
        return room;
    }

    public Room joinRoom(String roomId, JoinRoomRequest req) {
        Room room = registry.findById(roomId);
        if (room == null) return null;

        // ── Password check ──
        if (room.getPassword() != null && !room.getPassword().isEmpty()
                && !room.getPassword().equals(req.getPassword())) {
            System.out.println("[WARN] " + req.getPlayerId() + " wrong password for room " + roomId);
            return null;
        }

        // ── Reconnect: player already in room → restore state ──
        var existing = room.getPlayers().stream()
            .filter(p -> p.getPlayerId().equals(req.getPlayerId()))
            .findFirst().orElse(null);
        if (existing != null) {
            disconnectHandler.onReconnect(req.getPlayerId());
            disconnectHandler.registerPlayer(roomId, req.getPlayerId());
            room.setLastActivity(System.currentTimeMillis());
            System.out.println("[RECONNECT] " + req.getPlayerId() + " rejoined room " + roomId);
            return room;
        }

        // ── New player: create and add ──
        boolean isPlaying = gameSessionService.hasActiveSession(roomId);
        Player player = new Player(req.getPlayerId(), req.getNickname(),
                -1, room.getConfig().getInitialChips());
        player.setStatus(isPlaying
            ? com.first.poker.model.enums.PlayerStatus.QUEUED
            : com.first.poker.model.enums.PlayerStatus.ACTIVE);
        if (!room.addPlayer(player)) return null;

        // ── Common: every player entering the room (new or reconnect)
        //          must be registered for disconnect tracking ──
        room.setLastActivity(System.currentTimeMillis());
        disconnectHandler.registerPlayer(roomId, req.getPlayerId());
        return room;
    }

    public Room findRoom(String roomId) {
        return registry.findById(roomId);
    }

    public List<Player> addBots(String roomId, int count) {
        Room room = registry.findById(roomId);
        if (room == null) return null;
        var bots = new ArrayList<Player>();
        String[] botNames = {"🤖小Q", "🤖老K", "🤖Ace", "🤖大盲", "🤖金刚", "🤖顺子", "🤖同花"};
        int existingBots = (int) room.getPlayers().stream().filter(p -> p.getPlayerId().startsWith("bot-")).count();
        for (int i = 0; i < count; i++) {
            int botNum = existingBots + i + 1;
            String botId = "bot-" + roomId + "-" + botNum;
            String name = botNum <= botNames.length ? botNames[botNum - 1] : ("🤖机器人" + botNum);
            Player bot = new Player(botId, name, -1, room.getConfig().getInitialChips());
            if (room.addPlayer(bot)) {
                bots.add(bot);
            }
        }
        return bots;
    }

    public boolean leaveRoom(String roomId, String playerId) {
        Room room = registry.findById(roomId);
        if (room == null) return false;
        return room.removePlayer(playerId);
    }

    public Player transferOwnership(Room room, String leavingPlayerId) {
        // Find next human ACTIVE player, fall back to QUEUED
        Player newOwner = room.getPlayers().stream()
            .filter(p -> !p.getPlayerId().startsWith("bot-"))
            .filter(p -> !p.getPlayerId().equals(leavingPlayerId))
            .filter(p -> p.getStatus() == com.first.poker.model.enums.PlayerStatus.ACTIVE
                      || p.getStatus() == com.first.poker.model.enums.PlayerStatus.QUEUED)
            .findFirst().orElse(null);

        if (newOwner != null) {
            // Clear old owner flag
            room.getPlayers().stream()
                .filter(Player::isOwner)
                .findFirst().ifPresent(o -> o.setOwner(false));
            newOwner.setOwner(true);
            room.setOwner(newOwner);
        } else {
            room.setOwner(null);
        }
        return newOwner;
    }

    public boolean hasHumanPlayers(String roomId) {
        Room room = registry.findById(roomId);
        if (room == null) return false;
        return room.getPlayers().stream().anyMatch(p -> !p.getPlayerId().startsWith("bot-"));
    }

    public void seatQueuedPlayers(String roomId, BroadcastService broadcast) {
        Room room = registry.findById(roomId);
        if (room == null) return;

        // Count occupied seats
        int activeCount = (int) room.getPlayers().stream()
            .filter(p -> p.getStatus() == com.first.poker.model.enums.PlayerStatus.ACTIVE)
            .count();
        int maxSeats = room.getConfig().getMaxSeats();

        for (var p : room.getPlayers()) {
            if (p.getStatus() != com.first.poker.model.enums.PlayerStatus.QUEUED) continue;
            if (activeCount >= maxSeats) break;

            // Send queue_prompt to the player
            var prompt = new java.util.HashMap<String, Object>();
            prompt.put("type", "queue_prompt");
            prompt.put("roomId", roomId);
            prompt.put("timeoutSec", 10);
            broadcast.sendToPlayer(p.getPlayerId(), prompt);

            // Auto-accept after 10s (player can also click to accept immediately)
            // The actual accept will come via a STOMP message from the frontend
            // For now, we just auto-seat them on next game start
            // This is handled by the GameSessionService which filters by ACTIVE status
        }
    }

    private void applyConfig(RoomConfig config, CreateRoomRequest req) {
        if (req.getMaxSeats() != null) config.setMaxSeats(req.getMaxSeats());
        if (req.getMinPlayers() != null) config.setMinPlayers(req.getMinPlayers());
        if (req.getInitialChips() != null) config.setInitialChips(req.getInitialChips());
        if (req.getSmallBlind() != null) config.setSmallBlind(req.getSmallBlind());
        if (req.getActionTimeoutSec() != null) config.setActionTimeoutSec(req.getActionTimeoutSec());
        if (req.getBustEndsGame() != null) config.setBustEndsGame(req.getBustEndsGame());
    }
}
