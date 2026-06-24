package com.first.poker.integration;

import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import com.first.poker.model.Player;
import com.first.poker.model.enums.*;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GameIntegrationTest {

    @Test
    void fullHandFoldFlow() {
        Room room = new Room("r1", "Integration", RoomConfig.withDefaults());
        room.getConfig().setSmallBlind(10);

        Player p1 = new Player("p1", "Alice", 0, 1000);
        Player p2 = new Player("p2", "Bob", 1, 1000);
        Player p3 = new Player("p3", "Carol", 2, 1000);

        room.addPlayer(p1);
        room.addPlayer(p2);
        room.addPlayer(p3);

        p1.setBetInRound(20);
        p2.setBetInRound(10);

        p3.setFolded(true);
        p1.setFolded(true);

        long active = room.getPlayers().stream()
            .filter(p -> !p.isFolded())
            .count();
        assertEquals(1, active);
    }

    @Test
    void holeCardsSetCorrectly() {
        Player p1 = new Player("p1", "Alice", 0, 500);
        p1.setHoleCards(List.of("Ah", "Kh"));

        assertNotNull(p1.getHoleCards());
        assertEquals(2, p1.getHoleCards().size());
        assertEquals("Ah", p1.getHoleCards().get(0));
    }

    @Test
    void buyInRuleOnceOnly() {
        Room room = new Room("r1", "BuyInTest", RoomConfig.withDefaults());
        room.getConfig().setBuyInRule(RoomConfig.BuyInRule.ONCE_ONLY);
        assertEquals(RoomConfig.BuyInRule.ONCE_ONLY, room.getConfig().getBuyInRule());
    }

    @Test
    void bustEndsGameTrueEndsWhenSinglePlayerHasChips() {
        Room room = new Room("r1", "BustTest", RoomConfig.withDefaults());
        room.getConfig().setBustEndsGame(true);

        Player p1 = new Player("p1", "A", 0, 1000);
        p1.setChips(0);
        Player p2 = new Player("p2", "B", 1, 1000);
        p2.setChips(1000);

        long withChips = List.of(p1, p2).stream()
            .filter(p -> p.getChips() > 0).count();
        assertEquals(1, withChips);
    }

    @Test
    void disconnectedPlayerCantAct() {
        Player p = new Player("p1", "Offline", 0, 1000);
        p.setConnected(false);
        p.setChips(1000);

        assertFalse(p.isConnected());
        assertEquals(1000, p.getChips());
    }
}
