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
                int toCall = Math.min(s.currentBet() - player.roundBet(), player.chips());
                var updated = player.withChipsDeducted(toCall);
                int newMask = s.actedMask() | (1 << playerIdx);
                yield advanceToNextActive(s
                    .withUpdatedPlayer(playerIdx, updated)
                    .withPot(s.pot() + toCall)
                    .withActedMask(newMask));
            }
            case BET -> {
                int delta = amount - player.roundBet();
                if (delta < 0) delta = 0;
                int actual = Math.min(delta, player.chips());
                boolean allIn = player.allIn() || (amount >= player.chips());
                var updated = new GamePlayerState(
                    player.playerId(), player.nickname(), player.seatIndex(),
                    player.chips() - actual,
                    player.totalBet() + actual,
                    player.roundBet() + actual,
                    player.folded(),
                    allIn,
                    player.holeCards()
                );
                yield advanceToNextActive(s
                    .withUpdatedPlayer(playerIdx, updated)
                    .withPot(s.pot() + actual)
                    .withCurrentBet(updated.roundBet())
                    .withActedMask(1 << playerIdx)
                    .withLastAggressorIndex(playerIdx));
            }
            case RAISE -> {
                int delta = amount - player.roundBet();
                if (delta < 0) delta = 0;
                int actual = Math.min(delta, player.chips());
                boolean allIn = player.allIn() || (amount >= player.chips());
                var updated = new GamePlayerState(
                    player.playerId(), player.nickname(), player.seatIndex(),
                    player.chips() - actual,
                    player.totalBet() + actual,
                    player.roundBet() + actual,
                    player.folded(),
                    allIn,
                    player.holeCards()
                );
                yield advanceToNextActive(s
                    .withUpdatedPlayer(playerIdx, updated)
                    .withPot(s.pot() + actual)
                    .withCurrentBet(updated.roundBet())
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

        for (int i = 0; i < playerCount; i++) {
            var p = state.players().get(i);
            if (!p.folded() && !p.allIn()) {
                allActiveMask |= (1 << i);
            }
        }

        // Only 0 or 1 non-all-in active player remains — no more betting possible.
        // Just verify that the active player has already acted.
        // (The roundBet-vs-currentBet equality check is skipped because all-in
        //  calls can leave the remaining player's roundBet mismatched with
        //  currentBet when BB/ante is included.)
        if (Integer.bitCount(allActiveMask) <= 1) {
            return (actedMask & allActiveMask) == allActiveMask;
        }

        // Multiple active players: all must have matched the current bet
        int currentBet = state.currentBet();
        for (int i = 0; i < playerCount; i++) {
            var p = state.players().get(i);
            if (!p.folded() && !p.allIn()) {
                if (p.roundBet() != currentBet) return false;
            }
        }

        // All active players must have acted since the last raise
        return (actedMask & allActiveMask) == allActiveMask;
    }
}
