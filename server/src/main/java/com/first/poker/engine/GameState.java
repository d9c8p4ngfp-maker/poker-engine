package com.first.poker.engine;

import java.util.List;

public record GameState(
    GamePhase phase,
    List<GamePlayerState> players,
    List<Card> communityCards,
    int pot,
    int currentBet,
    int minRaise,
    int currentPlayerIndex,
    int dealerIndex,
    int smallBlindAmount,
    int bigBlindAmount,
    Deck deck
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
            deck
        );
    }

    public GamePlayerState currentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public GameState withPot(int newPot) {
        return new GameState(phase, players, communityCards, newPot, currentBet, minRaise,
            currentPlayerIndex, dealerIndex, smallBlindAmount, bigBlindAmount, deck);
    }

    public GameState withNextPlayer() {
        int next = nextActiveIndex(currentPlayerIndex);
        return new GameState(phase, players, communityCards, pot, currentBet, minRaise,
            next, dealerIndex, smallBlindAmount, bigBlindAmount, deck);
    }

    private int nextActiveIndex(int from) {
        int size = players.size();
        for (int i = 1; i <= size; i++) {
            int idx = (from + i) % size;
            if (isActive(players.get(idx))) return idx;
        }
        return from; // fallback: stay put if no one active
    }

    private boolean isActive(GamePlayerState p) {
        return !p.folded() && !p.allIn();
    }

    public int activeCount() {
        return (int) players.stream().filter(this::isActive).count();
    }
}
