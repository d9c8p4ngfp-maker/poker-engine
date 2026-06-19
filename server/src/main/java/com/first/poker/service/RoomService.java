package com.first.poker.service;

import com.first.poker.dto.CreateRoomRequest;
import com.first.poker.dto.JoinRoomRequest;
import com.first.poker.model.Player;
import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import org.springframework.stereotype.Service;

@Service
public class RoomService {
    private final RoomRegistry registry;

    public RoomService(RoomRegistry registry) {
        this.registry = registry;
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
            room.addPlayer(owner);
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

    private void applyConfig(RoomConfig config, CreateRoomRequest req) {
        if (req.getMaxSeats() != null) config.setMaxSeats(req.getMaxSeats());
        if (req.getMinPlayers() != null) config.setMinPlayers(req.getMinPlayers());
        if (req.getInitialChips() != null) config.setInitialChips(req.getInitialChips());
        if (req.getSmallBlind() != null) config.setSmallBlind(req.getSmallBlind());
        if (req.getActionTimeoutSec() != null) config.setActionTimeoutSec(req.getActionTimeoutSec());
    }
}
