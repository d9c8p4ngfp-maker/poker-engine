package com.first.poker.engine;

import java.util.*;

public class BonusResolver {

    public record BonusResult(
        String type,
        String winnerId,
        int bonusPerPlayer,
        Map<String, Integer> transfers
    ) {}

    public static Optional<BonusResult> check27Game(
            String winnerId,
            List<Card> holeCards,
            Map<String, Integer> participantChips,
            int bonusAmount) {

        boolean has2 = holeCards.stream().anyMatch(c -> c.rank() == Card.Rank.TWO);
        boolean has7 = holeCards.stream().anyMatch(c -> c.rank() == Card.Rank.SEVEN);
        if (!has2 || !has7) return Optional.empty();

        Map<String, Integer> transfers = new LinkedHashMap<>();
        int totalReceived = 0;
        for (var entry : participantChips.entrySet()) {
            if (entry.getKey().equals(winnerId)) continue;
            int pay = Math.min(bonusAmount, entry.getValue());
            transfers.put(entry.getKey(), pay);
            totalReceived += pay;
        }
        transfers.put(winnerId, -totalReceived);

        return Optional.of(new BonusResult("27_GAME", winnerId, bonusAmount, transfers));
    }

    public static Optional<BonusResult> checkStraightFlushBonus(
            String winnerId,
            HandEvaluator.HandResult handResult,
            Map<String, Integer> participantChips,
            int bonusAmount,
            boolean royalDouble) {

        if (handResult.rank() < 8_000_000) return Optional.empty();

        String type;
        int actualBonus;
        boolean isRoyal = handResult.rank() >= 9_000_000;
        if (isRoyal && royalDouble) {
            type = "ROYAL_FLUSH";
            actualBonus = bonusAmount * 2;
        } else {
            type = "STRAIGHT_FLUSH";
            actualBonus = bonusAmount;
        }

        Map<String, Integer> transfers = new LinkedHashMap<>();
        int totalReceived = 0;
        for (var entry : participantChips.entrySet()) {
            if (entry.getKey().equals(winnerId)) continue;
            int pay = Math.min(actualBonus, entry.getValue());
            transfers.put(entry.getKey(), pay);
            totalReceived += pay;
        }
        transfers.put(winnerId, -totalReceived);

        return Optional.of(new BonusResult(type, winnerId, actualBonus, transfers));
    }
}
