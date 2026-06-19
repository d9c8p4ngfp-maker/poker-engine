package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;

class DeckDeterminismTest {

    @Test
    void fixedSeedProducesDeterministicDeal() {
        Deck d1 = new Deck(42L);
        Deck d2 = new Deck(42L);

        for (int i = 0; i < 10; i++) {
            assertEquals(d1.deal(), d2.deal(), "Card " + i + " should be identical with same seed");
        }
    }

    @Test
    void differentSeedsProduceDifferentDeals() {
        Deck d1 = new Deck(42L);
        Deck d2 = new Deck(99L);

        boolean anyDifferent = false;
        for (int i = 0; i < 10; i++) {
            if (!d1.deal().equals(d2.deal())) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "Different seeds should produce different card sequences");
    }

    @Test
    void seedDeckDoesNotNeedExplicitShuffle() {
        Deck d = new Deck(12345L);
        assertEquals(52, d.size(), "Seed deck should have 52 cards ready to deal");
        Card c = d.deal();
        assertNotNull(c);
    }
}
