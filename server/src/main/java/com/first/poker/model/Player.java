package com.first.poker.model;

import com.first.poker.model.enums.PlayerStatus;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Player {
    private String playerId;
    private String nickname;
    private int seatIndex;
    private int chips;
    private int betInRound;
    private boolean folded;
    private boolean allIn;
    private boolean connected;
    private String lastAction;
    private List<String> holeCards;
    private int borrowCount;
    private boolean owner;
    private PlayerStatus status;

    public Player(String playerId, String nickname, int seatIndex, int initialChips) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.seatIndex = seatIndex;
        this.chips = initialChips;
        this.betInRound = 0;
        this.folded = false;
        this.allIn = false;
        this.connected = true;
        this.lastAction = null;
        this.holeCards = new ArrayList<>();
        this.borrowCount = 0;
        this.owner = false;
        this.status = PlayerStatus.ACTIVE;
    }

    public void borrow(int amount) {
        this.chips += amount;
        this.borrowCount++;
    }

    public void placeBet(int amount) {
        int actual = Math.min(amount, this.chips);
        this.chips -= actual;
        this.betInRound += actual;
        if (this.chips == 0) {
            this.allIn = true;
        }
    }
}
