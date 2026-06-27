package com.first.poker.dto;

import lombok.Data;

@Data
public class CreateRoomRequest {
    private String roomName;
    private String name;
    private String password;
    private String ownerId;
    private String ownerNickname;
    private Integer maxSeats;
    private Integer minPlayers;
    private Integer initialChips;
    private Integer smallBlind;
    private Integer actionTimeoutSec;
    private Boolean bustEndsGame;
    private Boolean bonus27Enabled;
    private Integer bonus27Amount;
    private Boolean bonusStraightFlushEnabled;
    private Integer bonusStraightFlushAmount;
    private Boolean bonusRoyalFlushDouble;
    private Boolean autoContinue;
}
