package com.first.poker.model;

import lombok.Data;

@Data
public class RoomConfig {

    public enum LeaveHandling { AUTO_FOLD, RESERVE_60S }
    public enum BuyInRule { ONCE_ONLY, ALLOW_REBUY }

    private String name;
    private String password;
    private int maxSeats;
    private int minPlayers;
    private int initialChips;
    private int smallBlind;
    private int bigBlind;
    private int actionTimeoutSec;
    private boolean allowSpectate;
    private LeaveHandling leaveHandling;
    private BuyInRule buyInRule;
    private boolean recordHistory;

    public static RoomConfig withDefaults() {
        RoomConfig c = new RoomConfig();
        c.name = "默认牌局";
        c.password = null;
        c.maxSeats = 8;
        c.minPlayers = 2;
        c.initialChips = 1000;
        c.smallBlind = 10;
        c.bigBlind = 20;
        c.actionTimeoutSec = 30;
        c.allowSpectate = false;
        c.leaveHandling = LeaveHandling.AUTO_FOLD;
        c.buyInRule = BuyInRule.ONCE_ONLY;
        c.recordHistory = true;
        return c;
    }

    public void setSmallBlind(int value) {
        if (value < 1) throw new IllegalArgumentException("小盲注必须 >= 1");
        this.smallBlind = value;
        this.bigBlind = value * 2;
    }

    public void setMaxSeats(int value) {
        if (value < 2 || value > 8) throw new IllegalArgumentException("座位数必须 2~8");
        this.maxSeats = value;
    }
}
