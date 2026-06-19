package com.first.poker.engine;

public class BettingRoundManager {

    public static GameState applyAction(GameState state, GameAction action, int amount,
                                         int actedMask, int lastAggressorIndex) {
        int playerIdx = state.currentPlayerIndex();
        var player = state.players().get(playerIdx);

        GameState s = state.withLastAggressorIndex(lastAggressorIndex).withActedMask(actedMask);

        return switch (action) {
            case FOLD -> {
                var updated = player.withFolded();
                int newMask = s.actedMask() | (1 << playerIdx);
                s = s.withUpdatedPlayer(playerIdx, updated)
                     .withActedMask(newMask);
                yield BettingRoundManager.advanceToNextActive(s);
            }
            case CHECK -> {
                int newMask = s.actedMask() | (1 << playerIdx);
                yield advanceToNextActive(s.withActedMask(newMask));
            }
            case CALL -> {
                int toCall = s.currentBet() - player.roundBet();
                var updated = player.withChipsDeducted(toCall);
                int newMask = s.actedMask() | (1 << playerIdx);
                yield advanceToNextActive(s.withUpdatedPlayer(playerIdx, updated)
                                           .withActedMask(newMask));
            }
            case BET -> {
                var updated = player.withChipsDeducted(amount);
                // Reset acted mask: only current player has acted since this bet
                yield advanceToNextActive(s
                    .withUpdatedPlayer(playerIdx, updated)
                    .withCurrentBet(amount)
                    .withActedMask(1 << playerIdx)
                    .withLastAggressorIndex(playerIdx));
            }
            case RAISE -> {
                var updated = player.withChipsDeducted(amount);
                yield advanceToNextActive(s
                    .withUpdatedPlayer(playerIdx, updated)
                    .withCurrentBet(amount)
                    .withActedMask(1 << playerIdx)
                    .withLastAggressorIndex(playerIdx));
            }
        };
    }

    private static GameState advanceToNextActive(GameState state) {
        int size = state.players().size();
        int next = state.currentPlayerIndex();
        for (int i = 1; i <= size; i++) {
            next = (next + 1) % size;
            var p = state.players().get(next);
            if (!p.folded() && !p.allIn()) {
                return new GameState(
                    state.phase(), state.players(), state.communityCards(),
                    state.pot(), state.currentBet(), state.minRaise(),
                    next, state.dealerIndex(),
                    state.smallBlindAmount(), state.bigBlindAmount(),
                    state.deck(), state.actedMask(), state.lastAggressorIndex()
                );
            }
        }
        return state; // no active players
    }

    public static boolean isRoundComplete(GameState state, int actedMask, int lastAggressorIndex) {
        int playerCount = state.players().size();
        int allActiveMask = 0;
        int currentBet = state.currentBet();

        for (int i = 0; i < playerCount; i++) {
            var p = state.players().get(i);
            if (!p.folded() && !p.allIn()) {
                allActiveMask |= (1 << i);
                if (p.roundBet() != currentBet) return false;
            }
        }

        // All active players must have acted since the last raise
        return (actedMask & allActiveMask) == allActiveMask;
    }
}
