package com.first.poker.dto;

import jakarta.validation.constraints.NotBlank;

public class GameActionRequest {
    @NotBlank(message = "playerId is required")
    private String playerId;
    @NotBlank(message = "action is required")
    private String action;
    private int amount;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
