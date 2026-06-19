package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import java.util.ArrayList;
import java.util.List;

public class PhaseTransition {

    public static GameState startHand(List<GamePlayerState> players, int dealerIndex, RoomConfig config) {
        Deck deck = new Deck();
        deck.shuffle();

        int n = players.size();
        int sbIdx = (dealerIndex + 1) % n;
        int bbIdx = (dealerIndex + 2) % n;

        // Deal 2 hole cards to each player
        var mutable = new ArrayList<>(players);
        for (int i = 0; i < n; i++) {
            var p = mutable.get(i);
            var cards = List.of(deck.deal(), deck.deal());
            mutable.set(i, p.withHoleCards(cards));
        }

        // Post blinds
        var sb = mutable.get(sbIdx);
        mutable.set(sbIdx, sb.withChipsDeducted(config.getSmallBlind()));

        var bb = mutable.get(bbIdx);
        mutable.set(bbIdx, bb.withChipsDeducted(config.getBigBlind()));

        var fixedPlayers = List.copyOf(mutable);

        // Preflop: first to act is UTG = left of big blind
        int utgIdx = (bbIdx + 1) % n;

        // Initial pot includes blind posts (use actual amounts deducted)
        int sbPost = config.getSmallBlind();
        int bbPost = config.getBigBlind();
        int initialPot = Math.min(sbPost, players.get(sbIdx).chips())
                       + Math.min(bbPost, players.get(bbIdx).chips());

        return new GameState(
            GamePhase.PRE_FLOP,
            fixedPlayers,
            List.of(),
            initialPot,
            config.getBigBlind(),
            config.getBigBlind(),
            utgIdx,
            dealerIndex,
            config.getSmallBlind(),
            config.getBigBlind(),
            deck,
            0,  // actedMask
            -1  // lastAggressorIndex
        );
    }

    public static GameState advancePhase(GameState state) {
        GamePhase current = state.phase();
        int n = state.players().size();

        // Round bets already tracked in pot during actions; just reset them
        var mutable = new ArrayList<>(state.players());
        for (int i = 0; i < n; i++) {
            var p = mutable.get(i);
            mutable.set(i, p.withRoundBetReset());
        }

        int newPot = state.pot();
        int dealerIdx = state.dealerIndex();

        return switch (current) {
            case PRE_FLOP -> {
                // Deal 3 community cards (flop)
                var community = List.of(state.deck().deal(), state.deck().deal(), state.deck().deal());
                // Postflop: first to act = left of dealer
                int first = firstActiveAfter(mutable, dealerIdx);
                yield new GameState(
                    GamePhase.FLOP, List.copyOf(mutable), community, newPot,
                    0, state.bigBlindAmount(),
                    first, dealerIdx,
                    state.smallBlindAmount(), state.bigBlindAmount(),
                    state.deck(),
                    0, -1
                );
            }
            case FLOP -> {
                var community = new ArrayList<>(state.communityCards());
                community.add(state.deck().deal());
                int first = firstActiveAfter(mutable, dealerIdx);
                yield new GameState(
                    GamePhase.TURN, List.copyOf(mutable), List.copyOf(community), newPot,
                    0, state.bigBlindAmount(),
                    first, dealerIdx,
                    state.smallBlindAmount(), state.bigBlindAmount(),
                    state.deck(),
                    0, -1
                );
            }
            case TURN -> {
                var community = new ArrayList<>(state.communityCards());
                community.add(state.deck().deal());
                int first = firstActiveAfter(mutable, dealerIdx);
                yield new GameState(
                    GamePhase.RIVER, List.copyOf(mutable), List.copyOf(community), newPot,
                    0, state.bigBlindAmount(),
                    first, dealerIdx,
                    state.smallBlindAmount(), state.bigBlindAmount(),
                    state.deck(),
                    0, -1
                );
            }
            case RIVER -> {
                yield new GameState(
                    GamePhase.SHOWDOWN, List.copyOf(mutable), state.communityCards(), newPot,
                    0, state.bigBlindAmount(),
                    0, dealerIdx,
                    state.smallBlindAmount(), state.bigBlindAmount(),
                    state.deck(),
                    0, -1
                );
            }
            default -> state;
        };
    }

    private static int firstActiveAfter(List<GamePlayerState> players, int from) {
        int n = players.size();
        for (int i = 1; i <= n; i++) {
            int idx = (from + i) % n;
            var p = players.get(idx);
            if (!p.folded() && !p.allIn()) return idx;
        }
        return (from + 1) % n;
    }
}
