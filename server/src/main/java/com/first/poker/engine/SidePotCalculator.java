package com.first.poker.engine;

import java.util.*;

public class SidePotCalculator {

    public record PlayerStake(String playerId, int totalBet, boolean folded) {}
    public record PotResult(int amount, Set<String> eligiblePlayerIds, List<String> winnerIds) {}

    public static List<PotResult> calculate(List<PlayerStake> stakes, Map<String, Integer> handRanks, int dealerIndex) {
        List<PotResult> pots = new ArrayList<>();
        if (stakes.isEmpty()) return pots;

        // Collect ALL bet levels, including folded players
        // A folded player's chips are still in the pot and winnable by non-folded players
        TreeSet<Integer> levels = new TreeSet<>();
        for (PlayerStake s : stakes) {
            levels.add(s.totalBet());
        }
        // Ensure 0 is included as baseline
        levels.add(0);

        int prev = 0;
        for (int level : levels) {
            if (level <= prev) continue;
            int layerAmount = level - prev;
            Set<String> eligible = new LinkedHashSet<>();
            int potTotal = 0;
            int contributorCount = 0;
            String soleNonFoldedContributor = null;
            for (PlayerStake s : stakes) {
                if (s.totalBet() >= level) {
                    potTotal += layerAmount;
                    contributorCount++;
                    if (!s.folded()) {
                        soleNonFoldedContributor = s.playerId();
                    }
                }
            }
            // Build eligible in dealer order (clockwise from dealer's left)
            for (int i = 1; i <= stakes.size(); i++) {
                int idx = (dealerIndex + i) % stakes.size();
                PlayerStake s = stakes.get(idx);
                if (s.totalBet() >= level && !s.folded()) {
                    eligible.add(s.playerId());
                }
            }
            if (!eligible.isEmpty()) {
                // Uncalled bet return: only one non-folded contributor → return to them
                if (eligible.size() == 1 && contributorCount > 1) {
                    // All other contributors folded → uncalled bet, return to sole player
                    // Don't add to pots; the uncalled portion auto-returns
                    // (handled by: only the sole non-folded player gets this layer back)
                    // Actually, in our simplified model, we add it as a pot the sole player wins
                    int amount = potTotal;
                    String winner = soleNonFoldedContributor;
                    pots.add(new PotResult(amount, eligible, List.of(winner)));
                } else {
                    int amount = potTotal;
                    var topRank = eligible.stream()
                        .mapToInt(id -> handRanks.getOrDefault(id, -1))
                        .max();
                    if (topRank.isPresent()) {
                        int bestRank = topRank.getAsInt();
                        List<String> winners = eligible.stream()
                            .filter(id -> handRanks.getOrDefault(id, -1) == bestRank)
                            .toList();
                        pots.add(new PotResult(amount, eligible, winners));
                    }
                }
            } else if (contributorCount == 1 && soleNonFoldedContributor != null) {
                // Sole contributor didn't fold: uncalled bet, return to them
                pots.add(new PotResult(potTotal, Set.of(soleNonFoldedContributor), List.of(soleNonFoldedContributor)));
            }
            prev = level;
        }
        return pots;
    }
}
