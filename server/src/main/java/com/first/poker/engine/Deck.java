package com.first.poker.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Deck {
    private final List<Card> cards;

    public Deck() {
        cards = new ArrayList<>(52);
        for (Card.Suit suit : Card.Suit.values()) {
            for (Card.Rank rank : Card.Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    public Deck(long seed) {
        this();
        Collections.shuffle(cards, new Random(seed));
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card deal() {
        if (cards.isEmpty()) throw new IllegalStateException("Deck is empty");
        return cards.remove(cards.size() - 1);
    }

    public int size() {
        return cards.size();
    }
}
