package com.first.poker.service;

import com.first.poker.dto.CreateRoomRequest;
import com.first.poker.dto.JoinRoomRequest;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class RoomService {
    private final RoomRegistry registry;
    private final GameSessionService gameSessionService;

    public RoomService(RoomRegistry registry, GameSessionService gameSessionService) {
        this.registry = registry;
        this.gameSessionService = gameSessionService;
    }

    public Room createRoom(CreateRoomRequest req) {
        RoomConfig config = RoomConfig.withDefaults();
        applyConfig(config, req);
        String roomName = req.getRoomName() != null ? req.getRoomName()
            : (req.getName() != null ? req.getName() : "默认牌局");
        Room room = registry.createRoom(roomName, config);

        // Auto-add owner as first player
        if (req.getOwnerId() != null) {
            String nickname = req.getOwnerNickname() != null ? req.getOwnerNickname() : req.getOwnerId();
            Player owner = new Player(req.getOwnerId(), nickname, 0, config.getInitialChips());
            owner.setOwner(true);
            room.addPlayer(owner);
            room.setOwner(owner);
        }
        return room;
    }

    public Room joinRoom(String roomId, JoinRoomRequest req) {
        Room room = registry.findById(roomId);
        if (room == null) return null;
        Player player = new Player(req.getPlayerId(), req.getNickname(),
                room.getPlayers().size(), room.getConfig().getInitialChips());
        if (!room.addPlayer(player)) return null;
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
            Player bot = new Player(botId, name, room.getPlayers().size(), room.getConfig().getInitialChips());
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

    private void applyConfig(RoomConfig config, CreateRoomRequest req) {
        if (req.getMaxSeats() != null) config.setMaxSeats(req.getMaxSeats());
        if (req.getMinPlayers() != null) config.setMinPlayers(req.getMinPlayers());
        if (req.getInitialChips() != null) config.setInitialChips(req.getInitialChips());
        if (req.getSmallBlind() != null) config.setSmallBlind(req.getSmallBlind());
        if (req.getActionTimeoutSec() != null) config.setActionTimeoutSec(req.getActionTimeoutSec());
        if (req.getBustEndsGame() != null) config.setBustEndsGame(req.getBustEndsGame());
    }
}
