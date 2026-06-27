package com.first.poker.engine;

import java.util.*;

public class HandEvaluator {

    public record HandResult(int rank, String name, List<Card> bestFive) {}

    /**
     * Evaluate the best 5-card hand from a set of cards (typically 2 hole + up to 5 community).
     * Returns null if fewer than 5 cards are available (e.g. early timeout before all streets dealt).
     */
    public static HandResult evaluate(List<Card> cards) {
        if (cards == null || cards.size() < 5) return null;
        HandResult best = null;
        List<List<Card>> combos = combinations(cards, 5);
        for (List<Card> five : combos) {
            HandResult hr = evaluateFive(five);
            if (best == null || hr.rank() > best.rank()) best = hr;
        }
        return best;
    }

    private static HandResult evaluateFive(List<Card> cards) {
        int[] ranks = new int[5];
        for (int i = 0; i < 5; i++) ranks[i] = cards.get(i).rank().numericValue();

        Arrays.sort(ranks);
        boolean flush = cards.stream().map(Card::suit).distinct().count() == 1;
        boolean straight = false;
        int highCard = ranks[4];

        if (ranks[0] + 1 == ranks[1] && ranks[1] + 1 == ranks[2]
         && ranks[2] + 1 == ranks[3] && ranks[3] + 1 == ranks[4]) {
            straight = true;
        }
        // Wheel: A-2-3-4-5 (numericValues: 0=DEUCE, 1=TREY, 2=FOUR, 3=FIVE, 12=ACE)
        if (ranks[0] == 0 && ranks[1] == 1 && ranks[2] == 2 && ranks[3] == 3 && ranks[4] == 12) {
            straight = true;
            highCard = 3;
        }

        int[] count = new int[13];
        for (int r : ranks) count[r]++;

        int quads = -1, trips = -1, pair1 = -1, pair2 = -1;
        for (int r = 12; r >= 0; r--) {
            if (count[r] == 4) quads = r;
            else if (count[r] == 3) trips = r;
            else if (count[r] == 2) {
                if (pair1 == -1) pair1 = r;
                else pair2 = r;
            }
        }

        if (straight && flush && highCard == 12) return new HandResult(9_000_000, "Royal Flush", cards);
        if (straight && flush) return new HandResult(8_000_000 + highCard, "Straight Flush", cards);
        if (quads >= 0) return new HandResult(7_000_000 + quads * 13 + topKicker(ranks, count, quads), "Four of a Kind", cards);
        if (trips >= 0 && pair1 >= 0) return new HandResult(6_000_000 + trips * 13 + pair1, "Full House", cards);
        if (flush) return new HandResult(5_000_000 + encodeHighCards(ranks), "Flush", cards);
        if (straight) return new HandResult(4_000_000 + highCard, "Straight", cards);
        if (trips >= 0) return new HandResult(3_000_000 + trips * 169 + top2Remaining(ranks, count, trips), "Three of a Kind", cards);
        if (pair2 >= 0) return new HandResult(2_000_000 + pair1 * 169 + pair2 * 13 + topKicker(ranks, count, pair1, pair2), "Two Pair", cards);
        if (pair1 >= 0) return new HandResult(1_000_000 + pair1 * 2197 + top3Remaining(ranks, count, pair1), "One Pair", cards);
        return new HandResult(encodeHighCards(ranks), "High Card", cards);
    }

    private static int encodeHighCards(int[] ranks) {
        return ranks[4] * 28561 + ranks[3] * 2197 + ranks[2] * 169 + ranks[1] * 13 + ranks[0];
    }

    private static int topKicker(int[] ranks, int[] count, int exclude) {
        for (int r = 12; r >= 0; r--) {
            if (r != exclude && count[r] > 0) return r;
        }
        return 0;
    }

    private static int topKicker(int[] ranks, int[] count, int excl1, int excl2) {
        for (int r = 12; r >= 0; r--) {
            if (r != excl1 && r != excl2 && count[r] > 0) return r;
        }
        return 0;
    }

    private static int top3Remaining(int[] ranks, int[] count, int exclude) {
        int sum = 0;
        int mult = 169;
        for (int r = 12; r >= 0; r--) {
            if (r != exclude && count[r] > 0) {
                sum += r * mult;
                mult /= 13;
            }
        }
        return sum;
    }

    private static int top2Remaining(int[] ranks, int[] count, int exclude) {
        int sum = 0;
        int mult = 13;
        int found = 0;
        for (int r = 12; r >= 0 && found < 2; r--) {
            if (r != exclude && count[r] > 0) {
                sum += r * mult;
                mult /= 13;
                found++;
            }
        }
        return sum;
    }

    private static List<List<Card>> combinations(List<Card> list, int k) {
        List<List<Card>> result = new ArrayList<>();
        combine(list, k, 0, new ArrayList<>(), result);
        return result;
    }

    private static void combine(List<Card> list, int k, int start, List<Card> current, List<List<Card>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            combine(list, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
