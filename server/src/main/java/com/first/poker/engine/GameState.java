package com.first.poker.engine;

import java.util.List;

public record GameState(
    GamePhase phase,
    /**
     * Players who are actively participating in this hand. This list ONLY contains
     * players who were included by {@code GameSessionService.startGame} at the
     * start of the hand — typically ACTIVE players with chips &gt; 0. Room players
     * who are spectating, disconnected, or have 0 chips are NOT in this list.
     * For the complete room roster, use {@code Room.getPlayers()}.
     */
    List<GamePlayerState> players,
    List<Card> communityCards,
    int pot,
    int currentBet,
    int minRaise,
    int currentPlayerIndex,
    int dealerIndex,
    int smallBlindAmount,
    int bigBlindAmount,
    Deck deck,
    int actedMask,
    int lastAggressorIndex
) {
    public static GameState create(
            List<GamePlayerState> players,
            int dealerIndex,
            int smallBlind,
            int bigBlind,
            Deck deck) {
        return new GameState(
            GamePhase.PRE_FLOP,
            List.copyOf(players),
            List.of(),
            0,
            0,
            bigBlind,
            0,
            dealerIndex,
            smallBlind,
            bigBlind,
            deck,
            0,
            -1
        );
    }

    public GamePlayerState currentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public GameState withPot(int newPot) {
        return new GameState(phase, players, communityCards, newPot, currentBet, minRaise,
            currentPlayerIndex, dealerIndex, smallBlindAmount, bigBlindAmount, deck,
            actedMask, lastAggressorIndex);
    }

    public GameState withCurrentBet(int bet) {
        return new GameState(phase, players, communityCards, pot, bet, minRaise,
            currentPlayerIndex, dealerIndex, smallBlindAmount, bigBlindAmount, deck,
            actedMask, lastAggressorIndex);
    }

    public GameState withActedMask(int mask) {
        return new GameState(phase, players, communityCards, pot, currentBet, minRaise,
            currentPlayerIndex, dealerIndex, smallBlindAmount, bigBlindAmount, deck,
            mask, lastAggressorIndex);
    }

    public GameState withLastAggressorIndex(int idx) {
        return new GameState(phase, players, communityCards, pot, currentBet, minRaise,
            currentPlayerIndex, dealerIndex, smallBlindAmount, bigBlindAmount, deck,
            actedMask, idx);
    }

    public GameState withNextPlayer() {
        int next = nextActiveIndex(currentPlayerIndex);
        return new GameState(phase, players, communityCards, pot, currentBet, minRaise,
            next, dealerIndex, smallBlindAmount, bigBlindAmount, deck,
            actedMask, lastAggressorIndex);
    }

    public GameState withPlayers(List<GamePlayerState> newPlayers) {
        return new GameState(phase, newPlayers, communityCards, pot, currentBet, minRaise,
            currentPlayerIndex, dealerIndex, smallBlindAmount, bigBlindAmount, deck,
            actedMask, lastAggressorIndex);
    }

    public GameState withPhase(GamePhase newPhase) {
        return new GameState(newPhase, players, communityCards, pot, currentBet, minRaise,
            currentPlayerIndex, dealerIndex, smallBlindAmount, bigBlindAmount, deck,
            actedMask, lastAggressorIndex);
    }

    public GameState withCommunityCards(List<Card> cards) {
        return new GameState(phase, players, cards, pot, currentBet, minRaise,
            currentPlayerIndex, dealerIndex, smallBlindAmount, bigBlindAmount, deck,
            actedMask, lastAggressorIndex);
    }

    public GameState withUpdatedPlayer(int index, GamePlayerState updated) {
        var mutable = new java.util.ArrayList<>(players);
        mutable.set(index, updated);
        return withPlayers(List.copyOf(mutable));
    }

    private int nextActiveIndex(int from) {
        int size = players.size();
        for (int i = 1; i <= size; i++) {
            int idx = (from + i) % size;
            if (isActive(players.get(idx))) return idx;
        }
        return from;
    }

    private boolean isActive(GamePlayerState p) {
        return !p.folded() && !p.allIn();
    }

    public int activeCount() {
        return (int) players.stream().filter(this::isActive).count();
    }
}
