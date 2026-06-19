package com.first.poker.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class GameDisconnectHandler {

    private final Map<String, String> playerRooms = new ConcurrentHashMap<>();
    private final BiConsumer<String, String> foldCallback;

    public GameDisconnectHandler(BiConsumer<String, String> foldCallback) {
        this.foldCallback = foldCallback;
    }

    public void registerPlayer(String roomId, String playerId) {
        playerRooms.put(playerId, roomId);
    }

    public void unregisterPlayer(String playerId) {
        playerRooms.remove(playerId);
    }

    public void handleDisconnect(String playerId) {
        var roomId = playerRooms.get(playerId);
        if (roomId != null) {
            foldCallback.accept(roomId, playerId);
        }
    }
}
