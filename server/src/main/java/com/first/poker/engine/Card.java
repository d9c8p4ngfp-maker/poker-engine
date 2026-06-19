package com.first.poker.engine;

public record Card(Suit suit, Rank rank) implements Comparable<Card> {

    public enum Suit { SPADES, HEARTS, DIAMONDS, CLUBS }
    public enum Rank { TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING, ACE;

        public int numericValue() { return this.ordinal(); }
    }

    @Override
    public String toString() {
        char r = switch (rank) {
            case ACE -> 'A'; case KING -> 'K'; case QUEEN -> 'Q';
            case JACK -> 'J'; case TEN -> 'T'; case NINE -> '9';
            case EIGHT -> '8'; case SEVEN -> '7'; case SIX -> '6';
            case FIVE -> '5'; case FOUR -> '4'; case THREE -> '3'; case TWO -> '2';
        };
        char s = switch (suit) {
            case HEARTS -> 'h'; case DIAMONDS -> 'd';
            case CLUBS -> 'c'; case SPADES -> 's';
        };
        return "" + r + s;
    }

    public static Card fromString(String s) {
        if (s == null || s.length() != 2) throw new IllegalArgumentException("Card must be 2 chars");
        Rank rank = switch (s.charAt(0)) {
            case 'A' -> Rank.ACE; case 'K' -> Rank.KING; case 'Q' -> Rank.QUEEN;
            case 'J' -> Rank.JACK; case 'T' -> Rank.TEN; case '9' -> Rank.NINE;
            case '8' -> Rank.EIGHT; case '7' -> Rank.SEVEN; case '6' -> Rank.SIX;
            case '5' -> Rank.FIVE; case '4' -> Rank.FOUR; case '3' -> Rank.THREE; case '2' -> Rank.TWO;
            default -> throw new IllegalArgumentException("Invalid rank: " + s.charAt(0));
        };
        Suit suit = switch (s.charAt(1)) {
            case 'h' -> Suit.HEARTS; case 'd' -> Suit.DIAMONDS;
            case 'c' -> Suit.CLUBS; case 's' -> Suit.SPADES;
            default -> throw new IllegalArgumentException("Invalid suit: " + s.charAt(1));
        };
        return new Card(suit, rank);
    }

    @Override
    public int compareTo(Card other) {
        int rankCmp = this.rank.compareTo(other.rank);
        return rankCmp != 0 ? rankCmp : this.suit.compareTo(other.suit);
    }
}
