package com.first.poker.engine;

import java.util.ArrayList;
import java.util.List;

public class ActionValidator {

    public record LegalAction(GameAction type, int minAmount, int maxAmount) {}

    public static List<LegalAction> legalActions(GameState state) {
        List<LegalAction> actions = new ArrayList<>();
        GamePlayerState player = state.currentPlayer();
        if (!isActive(player)) return actions;

        int toCall = state.currentBet() - player.roundBet();

        actions.add(new LegalAction(GameAction.FOLD, 0, 0));

        if (toCall <= 0) {
            actions.add(new LegalAction(GameAction.CHECK, 0, 0));
        } else {
            actions.add(new LegalAction(GameAction.CALL, toCall, toCall));
        }

        if (state.currentBet() == 0) {
            if (player.chips() >= state.minRaise()) {
                actions.add(new LegalAction(GameAction.BET, state.minRaise(), player.chips()));
            }
        } else {
            if (player.chips() > toCall) {
                int minTotal = state.currentBet() + state.minRaise();
                actions.add(new LegalAction(GameAction.RAISE, minTotal, player.chips() + player.roundBet()));
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
                // All-in via call: capping is handled in BettingRoundManager
            }
            case BET -> {
                if (state.currentBet() > 0) throw new IllegalArgumentException("Must raise, not bet");
                if (amount < state.minRaise() && amount < player.chips()) throw new IllegalArgumentException(
                    "Minimum bet is " + state.minRaise());
            }
            case RAISE -> {
                if (state.currentBet() == 0) throw new IllegalArgumentException("Must bet, not raise");
                int minTotal = state.currentBet() + state.minRaise();
                if (amount < minTotal && amount < player.chips()) throw new IllegalArgumentException(
                    "Raise must be at least " + minTotal);
            }
        }
    }

    private static boolean isActive(GamePlayerState p) {
        return !p.folded() && !p.allIn();
    }
}
