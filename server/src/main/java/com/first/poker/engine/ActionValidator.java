package com.first.poker.engine;

import java.util.ArrayList;
import java.util.List;

public class ActionValidator {

    public static List<GameAction> legalActions(GameState state) {
        List<GameAction> actions = new ArrayList<>();
        GamePlayerState player = state.currentPlayer();
        if (!isActive(player)) return actions;

        int toCall = state.currentBet() - player.roundBet();

        actions.add(GameAction.FOLD);

        if (toCall <= 0) {
            actions.add(GameAction.CHECK);
        } else {
            actions.add(GameAction.CALL);
        }

        if (state.currentBet() == 0) {
            if (player.chips() >= state.minRaise()) {
                actions.add(GameAction.BET);
            }
        } else {
            if (player.chips() > toCall) {
                actions.add(GameAction.RAISE);
            }
        }

        return actions;
    }

    public static void validate(GameState state, GameAction action, int amount) {
        GamePlayerState player = state.currentPlayer();
        if (!isActive(player)) {
            throw new IllegalArgumentException("Player is not active: " + player.playerId());
        }

        switch (action) {
            case FOLD -> { /* always valid */ }
            case CHECK -> {
                if (state.currentBet() > player.roundBet()) {
                    throw new IllegalArgumentException("Cannot check — must call " +
                        (state.currentBet() - player.roundBet()));
                }
            }
            case CALL -> {
                int chipsNeeded = state.currentBet() - player.roundBet();
                if (chipsNeeded <= 0) throw new IllegalArgumentException("Nothing to call");
                if (chipsNeeded > player.chips()) {
                    throw new IllegalArgumentException("Not enough chips to call");
                }
            }
            case BET -> {
                if (state.currentBet() > 0) throw new IllegalArgumentException("Must raise, not bet");
                if (amount < state.minRaise()) throw new IllegalArgumentException(
                    "Minimum bet is " + state.minRaise());
                if (amount > player.chips()) throw new IllegalArgumentException(
                    "Cannot bet more than remaining chips");
            }
            case RAISE -> {
                if (state.currentBet() == 0) throw new IllegalArgumentException("Must bet, not raise");
                int minTotal = state.currentBet() + state.minRaise();
                if (amount < minTotal) throw new IllegalArgumentException(
                    "Raise must be at least " + minTotal);
                if (amount > player.chips()) throw new IllegalArgumentException(
                    "Cannot raise more than remaining chips");
            }
        }
    }

    private static boolean isActive(GamePlayerState p) {
        return !p.folded() && !p.allIn();
    }
}
