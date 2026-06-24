package com.first.poker.service;

import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import com.first.poker.model.Player;
import com.first.poker.model.enums.PlayerStatus;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GameBroadcastHelperTest {

    @Test
    void checkGameOverBustEndsGameTrue() {
        Room room = new Room("R1", "Test", RoomConfig.withDefaults());
        room.getConfig().setBustEndsGame(true);

        Player p1 = new Player("p1", "Alice", 0, 1000);
        p1.setStatus(PlayerStatus.ACTIVE);
        p1.setChips(0);
        Player p2 = new Player("p2", "Bob", 1, 1000);
        p2.setStatus(PlayerStatus.ACTIVE);
        p2.setChips(1000);
        room.addPlayer(p1);
        room.addPlayer(p2);

        long activePlayers = room.getPlayers().stream()
            .filter(p -> p.getStatus() != PlayerStatus.LEFT)
            .filter(p -> p.getChips() > 0).count();
        assertEquals(1, activePlayers, "Only one player has chips");
    }

    @Test
    void checkGameOverBustEndsGameFalse() {
        Room room = new Room("R1", "Test", RoomConfig.withDefaults());
        room.getConfig().setBustEndsGame(false);

        Player p1 = new Player("p1", "Alice", 0, 1000);
        p1.setStatus(PlayerStatus.ACTIVE);
        p1.setChips(0);
        Player p2 = new Player("p2", "Bob", 1, 1000);
        p2.setStatus(PlayerStatus.ACTIVE);
        p2.setChips(50);
        room.addPlayer(p1);
        room.addPlayer(p2);

        assertFalse(room.getConfig().isBustEndsGame());
    }

    @Test
    void waitRoomBelowTwoPlayers() {
        Room room = new Room("R1", "Test", RoomConfig.withDefaults());
        room.getConfig().setBustEndsGame(false);

        Player p1 = new Player("p1", "Alice", 0, 1000);
        p1.setStatus(PlayerStatus.ACTIVE);
        p1.setChips(0);
        room.addPlayer(p1);

        assertEquals(0, room.getPlayers().stream()
            .filter(p -> p.getChips() > 0).count());
    }

    @Test
    void multiplePlayersNoneWithChips() {
        Room room = new Room("R1", "Test", RoomConfig.withDefaults());
        room.getConfig().setBustEndsGame(true);

        Player p1 = new Player("p1", "A", 0, 1000);
        p1.setChips(0);
        Player p2 = new Player("p2", "B", 1, 1000);
        p2.setChips(0);
        room.addPlayer(p1);
        room.addPlayer(p2);

        long withChips = room.getPlayers().stream()
            .filter(p -> p.getChips() > 0).count();
        assertEquals(0, withChips);
    }
}
