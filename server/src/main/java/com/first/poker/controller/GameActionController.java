package com.first.poker.controller;

import com.first.poker.dto.JoinRoomRequest;
import com.first.poker.model.Room;
import com.first.poker.service.BroadcastService;
import com.first.poker.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
public class GameActionController {

    private final RoomService roomService;
    private final BroadcastService broadcast;

    public GameActionController(RoomService roomService, BroadcastService broadcast) {
        this.roomService = roomService;
        this.broadcast = broadcast;
    }

    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@DestinationVariable String roomId, @Payload JoinRoomRequest req) {
        Room room = roomService.joinRoom(roomId, req);
        if (room != null) {
            broadcast.sendToRoom(roomId, Map.of(
                "type", "system",
                "text", req.getNickname() + " 加入了房间"
            ));
        }
    }
}
