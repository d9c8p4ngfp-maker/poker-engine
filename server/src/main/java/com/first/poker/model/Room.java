package com.first.poker.model;

import com.first.poker.model.enums.RoomStatus;
import lombok.Data;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Data
public class Room {
    private String roomId;
    private String name;
    private RoomStatus status;
    private RoomConfig config;
    private List<Player> players;
    private int dealerIndex;
    private long createdAt;
    private int handCount;

    public Room(String roomId, String name, RoomConfig config) {
        this.roomId = roomId;
        this.name = name;
        this.config = config;
        this.status = RoomStatus.WAITING;
        this.players = new ArrayList<>();
        this.dealerIndex = 0;
        this.createdAt = System.currentTimeMillis();
        this.handCount = 0;
    }

    public boolean addPlayer(Player player) {
        if (status == RoomStatus.PLAYING) return false;
        if (players.size() >= config.getMaxSeats()) return false;
        if (players.stream().anyMatch(p -> p.getPlayerId().equals(player.getPlayerId()))) {
            return false;
        }
        players.add(player);
        return true;
    }

    public boolean removePlayer(String playerId) {
        return players.removeIf(p -> p.getPlayerId().equals(playerId));
    }

    public static String generateRoomId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
