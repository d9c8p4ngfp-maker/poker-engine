package com.first.poker.dto;

public class GameActionRequest {
    private String playerId;
    private String action;
    private int amount;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
