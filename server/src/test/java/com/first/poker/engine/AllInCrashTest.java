package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AllInCrashTest {

    @Test
    void showdownWithAllPlayersAllInShouldNotCrash() {
        // Reproduce exact crash scenario: 8 players, all all-in, going to showdown
        Deck deck = new Deck();
        deck.shuffle();

        List<GamePlayerState> players = new ArrayList<>();
        String[] names = {"Human", "Bot1", "Bot2", "Bot3", "Bot4", "Bot5", "Bot6", "Bot7"};
        for (int i = 0; i < 8; i++) {
            var holeCards = List.of(deck.deal(), deck.deal());
            // All players are all-in with 0 chips remaining (simulates post-all-in state)
            players.add(new GamePlayerState(
                "p" + i, names[i], i,
                0,     // chips = 0 (all-in)
                1000,  // totalBet
                1000,  // roundBet  
                false, // not folded
                true,  // allIn = true
                holeCards
            ));
        }

        // Deal 5 community cards
        List<Card> communityCards = new ArrayList<>();
        for (int i = 0; i < 5; i++) communityCards.add(deck.deal());

        System.out.println("=== Testing showdown with " + players.size() + " all-in players ===");
        for (var p : players) {
            System.out.println("  " + p.playerId() + " chips=" + p.chips() + " totalBet=" + p.totalBet() +
                " folded=" + p.folded() + " allIn=" + p.allIn() + " cards=" + p.holeCards().size());
        }
        System.out.println("  Community cards: " + communityCards.size());

        // Step 1: Resolve hands
        System.out.println("Step 1: resolveHands...");
        var hands = HandResolver.resolveHands(players, communityCards);
        System.out.println("  hands=" + hands.size());
        for (var e : hands.entrySet()) {
            System.out.println("    " + e.getKey() + " -> " + e.getValue().name() + " rank=" + e.getValue().rank());
        }

        // Step 2: Distribute pots
        System.out.println("Step 2: distributePots...");
        var pots = HandResolver.distributePots(players, hands, 0);
        System.out.println("  pots=" + pots.size());
        for (var pot : pots) {
            System.out.println("    winners=" + pot.winnerIds() + " amount=" + pot.amount() +
                " eligible=" + pot.eligiblePlayerIds());
        }

        // Step 3: Build winner info (the crash point)
        System.out.println("Step 3: build winner info...");
        List<Map<String, Object>> winners = new ArrayList<>();
        Map<String, Integer> chipIncreases = new HashMap<>();
        for (var pot : pots) {
            List<String> winnerIds = pot.winnerIds();
            int share = pot.amount() / winnerIds.size();
            int remainder = pot.amount() % winnerIds.size();
            for (int w = 0; w < winnerIds.size(); w++) {
                String wid = winnerIds.get(w);
                int award = share + (w < remainder ? 1 : 0);
                var hand = hands.get(wid);
                winners.add(Map.of("playerId", wid, "handName", hand != null ? hand.name() : "Unknown", "amount", award));
                chipIncreases.merge(wid, award, Integer::sum);
            }
        }
        System.out.println("  winners=" + winners);
        System.out.println("  chipIncreases=" + chipIncreases);

        // Step 4: Apply chip distribution
        System.out.println("Step 4: apply chip distribution...");
        var mutablePlayers = new ArrayList<>(players);
        for (int i = 0; i < mutablePlayers.size(); i++) {
            var p = mutablePlayers.get(i);
            Integer increase = chipIncreases.get(p.playerId());
            if (increase != null) {
                mutablePlayers.set(i,
                    new GamePlayerState(p.playerId(), p.nickname(), p.seatIndex(),
                        p.chips() + increase, p.totalBet(), p.roundBet(),
                        p.folded(), p.allIn(), p.holeCards()));
            }
        }
        System.out.println("  Done! " + mutablePlayers.stream().mapToInt(GamePlayerState::chips).sum() + " total chips");

        assertFalse(pots.isEmpty(), "Should have at least one pot");
        assertFalse(winners.isEmpty(), "Should have a winner");
        System.out.println("=== PASSED ===");
    }
}
