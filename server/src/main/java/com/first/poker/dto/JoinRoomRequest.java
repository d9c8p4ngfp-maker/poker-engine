package com.first.poker.dto;

import lombok.Data;

@Data
public class JoinRoomRequest {
    private String playerId;
    private String nickname;
    private String password;
}
