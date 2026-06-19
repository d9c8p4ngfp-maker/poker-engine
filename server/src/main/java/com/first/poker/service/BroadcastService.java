package com.first.poker.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class BroadcastService {
    private final SimpMessagingTemplate template;

    public BroadcastService(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void sendToRoom(String roomId, Object payload) {
        template.convertAndSend("/topic/room/" + roomId, payload);
    }

    public void sendToRoom(String roomId, String destination, Object payload) {
        template.convertAndSend("/topic/room/" + roomId + "/" + destination, payload);
    }

    public void sendToPlayer(String playerId, Object payload) {
        template.convertAndSendToUser(playerId, "/queue/game", payload);
    }
}
