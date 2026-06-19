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
        return registry.createRoom(req.getName(), config);
    }

    public Room joinRoom(String roomId, JoinRoomRequest req) {
        Room room = registry.findById(roomId);
        if (room == null) return null;
        Player player = new Player(req.getPlayerId(), req.getNickname(),
                room.getPlayers().size(), room.getConfig().getInitialChips());
        if (!room.addPlayer(player)) return null;
        return room;
    }

    private void applyConfig(RoomConfig config, CreateRoomRequest req) {
        if (req.getMaxSeats() != null) config.setMaxSeats(req.getMaxSeats());
        if (req.getMinPlayers() != null) config.setMinPlayers(req.getMinPlayers());
        if (req.getInitialChips() != null) config.setInitialChips(req.getInitialChips());
        if (req.getSmallBlind() != null) config.setSmallBlind(req.getSmallBlind());
        if (req.getActionTimeoutSec() != null) config.setActionTimeoutSec(req.getActionTimeoutSec());
    }
}
