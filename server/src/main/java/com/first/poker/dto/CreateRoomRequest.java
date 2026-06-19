package com.first.poker.dto;

import lombok.Data;

@Data
public class CreateRoomRequest {
    private String name = "默认牌局";
    private String password;
    private Integer maxSeats;
    private Integer minPlayers;
    private Integer initialChips;
    private Integer smallBlind;
    private Integer actionTimeoutSec;
}
