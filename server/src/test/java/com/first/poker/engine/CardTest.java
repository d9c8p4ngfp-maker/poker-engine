package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardTest {

    @Test
    void shouldCreateCard() {
        Card c = new Card(Card.Suit.HEARTS, Card.Rank.ACE);
        assertEquals(Card.Suit.HEARTS, c.suit());
        assertEquals(Card.Rank.ACE, c.rank());
    }

    @Test
    void shouldConvertToString() {
        assertEquals("Ah", new Card(Card.Suit.HEARTS, Card.Rank.ACE).toString());
        assertEquals("Kd", new Card(Card.Suit.DIAMONDS, Card.Rank.KING).toString());
        assertEquals("Qs", new Card(Card.Suit.SPADES, Card.Rank.QUEEN).toString());
        assertEquals("Jc", new Card(Card.Suit.CLUBS, Card.Rank.JACK).toString());
        assertEquals("Th", new Card(Card.Suit.HEARTS, Card.Rank.TEN).toString());
        assertEquals("9d", new Card(Card.Suit.DIAMONDS, Card.Rank.NINE).toString());
        assertEquals("2s", new Card(Card.Suit.SPADES, Card.Rank.TWO).toString());
    }

    @Test
    void shouldParseFromString() {
        assertEquals(new Card(Card.Suit.HEARTS, Card.Rank.ACE), Card.fromString("Ah"));
        assertEquals(new Card(Card.Suit.DIAMONDS, Card.Rank.TEN), Card.fromString("Td"));
        assertEquals(new Card(Card.Suit.CLUBS, Card.Rank.TWO), Card.fromString("2c"));
    }

    @Test
    void shouldRejectInvalidString() {
        assertThrows(IllegalArgumentException.class, () -> Card.fromString(""));
        assertThrows(IllegalArgumentException.class, () -> Card.fromString("A"));
        assertThrows(IllegalArgumentException.class, () -> Card.fromString("Xh"));
        assertThrows(IllegalArgumentException.class, () -> Card.fromString("Ax"));
    }

    @Test
    void shouldOrderByRankThenSuit() {
        Card ace = new Card(Card.Suit.HEARTS, Card.Rank.ACE);
        Card king = new Card(Card.Suit.HEARTS, Card.Rank.KING);
        assertTrue(ace.compareTo(king) > 0);
    }
}
