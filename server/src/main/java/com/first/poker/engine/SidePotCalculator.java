package com.first.poker.engine;

import java.util.*;

public class SidePotCalculator {

    public record PlayerStake(String playerId, int totalBet, boolean folded) {}
    public record PotResult(int amount, Set<String> eligiblePlayerIds, String winnerId) {}

    public static List<PotResult> calculate(List<PlayerStake> stakes, Map<String, Integer> handRanks) {
        List<PotResult> pots = new ArrayList<>();
        if (stakes.isEmpty()) return pots;

        TreeSet<Integer> levels = new TreeSet<>();
        for (PlayerStake s : stakes) {
            if (!s.folded()) levels.add(s.totalBet());
        }

        int prev = 0;
        for (int level : levels) {
            int layerAmount = level - prev;
            Set<String> eligible = new HashSet<>();
            int potTotal = 0;
            for (PlayerStake s : stakes) {
                if (s.totalBet() >= level) {
                    potTotal += layerAmount;
                    if (!s.folded()) {
                        eligible.add(s.playerId());
                    }
                }
            }
            if (!eligible.isEmpty()) {
                int amount = potTotal;
                String winner = eligible.stream()
                    .max(Comparator.comparingInt(handRanks::get))
                    .orElse(eligible.iterator().next());
                pots.add(new PotResult(amount, eligible, winner));
            }
            prev = level;
        }
        return pots;
    }
}
