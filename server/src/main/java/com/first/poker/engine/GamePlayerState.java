package com.first.poker.engine;

import com.first.poker.model.Player;
import java.util.List;

public record GamePlayerState(
    String playerId,
    String nickname,
    int seatIndex,
    int chips,
    int totalBet,
    boolean folded,
    boolean allIn,
    List<Card> holeCards
) {
    public static GamePlayerState fromPlayer(Player p) {
        return new GamePlayerState(
            p.getPlayerId(), p.getNickname(), p.getSeatIndex(),
            p.getChips(), p.getBetInRound(), p.isFolded(), p.isAllIn(),
            p.getHoleCards().stream().map(Card::fromString).toList()
        );
    }

    public GamePlayerState withChipsDeducted(int amount) {
        int actual = Math.min(amount, this.chips);
        boolean nowAllIn = this.allIn || (actual >= this.chips);
        return new GamePlayerState(
            playerId, nickname, seatIndex,
            chips - actual, totalBet + actual,
            folded, nowAllIn, holeCards
        );
    }

    public GamePlayerState withFolded() {
        return new GamePlayerState(playerId, nickname, seatIndex, chips, totalBet, true, allIn, holeCards);
    }

    public GamePlayerState withHoleCards(List<Card> cards) {
        return new GamePlayerState(playerId, nickname, seatIndex, chips, totalBet, folded, allIn, cards);
    }
}
