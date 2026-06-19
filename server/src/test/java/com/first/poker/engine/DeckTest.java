package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;

class DeckTest {

    @Test
    void shouldCreateDeckWith52Cards() {
        Deck deck = new Deck();
        assertEquals(52, deck.size());
    }

    @Test
    void shouldDealCardAndReduceSize() {
        Deck deck = new Deck();
        deck.shuffle();
        Card c = deck.deal();
        assertNotNull(c);
        assertEquals(51, deck.size());
    }

    @Test
    void shouldContainAll52UniqueCards() {
        Deck deck = new Deck();
        var seen = new HashSet<String>();
        while (deck.size() > 0) {
            Card c = deck.deal();
            assertTrue(seen.add(c.toString()), "Duplicate card: " + c);
        }
        assertEquals(52, seen.size());
    }

    @Test
    void shouldThrowWhenDealingFromEmptyDeck() {
        Deck deck = new Deck();
        for (int i = 0; i < 52; i++) deck.deal();
        assertThrows(IllegalStateException.class, deck::deal);
    }

    @Test
    void shouldReorderAfterShuffle() {
        Deck deck = new Deck();
        assertDoesNotThrow(deck::shuffle);
    }
}
