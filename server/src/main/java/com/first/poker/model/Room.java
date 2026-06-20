package com.first.poker.model;

import com.first.poker.model.enums.RoomStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private volatile long lastActivity;
    private int handCount;
    private volatile Player owner;
    private String password;

    // Manual getter prevents password from being serialized to API responses
    @JsonIgnore
    public String getPassword() { return password; }

    public Room(String roomId, String name, RoomConfig config) {
        this.roomId = roomId;
        this.name = name;
        this.config = config;
        this.status = RoomStatus.WAITING;
        this.players = new CopyOnWriteArrayList<>();
        this.dealerIndex = 0;
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = this.createdAt;
        this.handCount = 0;
        this.owner = null;
    }

    public synchronized boolean addPlayer(Player player) {
        if (players.size() >= config.getMaxSeats()) return false;
        if (players.stream().anyMatch(p -> p.getPlayerId().equals(player.getPlayerId()))) {
            return false;
        }
        player.setSeatIndex(players.size());
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
