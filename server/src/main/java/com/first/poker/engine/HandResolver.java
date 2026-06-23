package com.first.poker.engine;

import java.util.*;
import java.util.stream.Collectors;

public class HandResolver {

    public static HandEvaluator.HandResult evaluatePlayerHand(GamePlayerState player, List<Card> communityCards) {
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(player.holeCards());
        allCards.addAll(communityCards);
        return HandEvaluator.evaluate(allCards);
    }

    public static Map<String, HandEvaluator.HandResult> resolveHands(
            List<GamePlayerState> players, List<Card> communityCards) {
        Map<String, HandEvaluator.HandResult> results = new LinkedHashMap<>();
        for (var p : players) {
            if (!p.folded()) {
                results.put(p.playerId(), evaluatePlayerHand(p, communityCards));
            }
        }
        return results;
    }

    public static List<SidePotCalculator.PotResult> distributePots(
            List<GamePlayerState> players,
            Map<String, HandEvaluator.HandResult> hands,
            int dealerIndex) {

        // Build stakes
        List<SidePotCalculator.PlayerStake> stakes = new ArrayList<>();
        for (var p : players) {
            stakes.add(new SidePotCalculator.PlayerStake(
                p.playerId(), p.totalBet(), p.folded()));
        }

        // Build rank map (higher = better)
        Map<String, Integer> rankMap = new HashMap<>();
        for (var entry : hands.entrySet()) {
            rankMap.put(entry.getKey(), entry.getValue().rank());
        }

        return SidePotCalculator.calculate(stakes, rankMap, dealerIndex);
    }
}
