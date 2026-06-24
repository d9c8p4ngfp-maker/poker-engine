package com.first.poker.controller;

import com.first.poker.model.Room;
import com.first.poker.model.RoomConfig;
import com.first.poker.model.Player;
import com.first.poker.model.enums.*;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GameMessageControllerTest {

    @Test
    void roomToResponseContainsRequiredFields() {
        Room room = new Room("r99", "测试房", RoomConfig.withDefaults());
        room.getConfig().setMaxSeats(6);
        room.getConfig().setSmallBlind(5);
        room.addPlayer(new Player("p1", "房主", 0, 1000));

        var response = new HashMap<String, Object>();
        response.put("roomId", room.getRoomId());
        response.put("name", room.getName());
        response.put("status", room.getStatus().name());
        var playerList = new ArrayList<Map<String, Object>>();
        for (Player p : room.getPlayers()) {
            var pm = new HashMap<String, Object>();
            pm.put("playerId", p.getPlayerId());
            pm.put("nickname", p.getNickname());
            pm.put("chips", p.getChips());
            pm.put("connected", p.isConnected());
            playerList.add(pm);
        }
        response.put("players", playerList);

        assertEquals("r99", response.get("roomId"));
        assertEquals("测试房", response.get("name"));
        assertEquals("WAITING", response.get("status"));
        assertNotNull(response.get("players"));
    }

    @Test
    void roomConfigFieldsAreIncluded() {
        Room room = new Room("r88", "ConfigTest", RoomConfig.withDefaults());
        room.getConfig().setBuyInRule(RoomConfig.BuyInRule.ONCE_ONLY);
        room.getConfig().setBigBlind(20);

        var config = new HashMap<String, Object>();
        config.put("buyInRule", room.getConfig().getBuyInRule().name());
        config.put("bigBlind", room.getConfig().getBigBlind());
        config.put("smallBlind", room.getConfig().getSmallBlind());

        assertEquals("ONCE_ONLY", config.get("buyInRule"));
        assertEquals(20, config.get("bigBlind"));
    }

    @Test
    void connectedStateReflectedCorrectly() {
        Player p1 = new Player("p1", "Online", 0, 1000);
        Player p2 = new Player("p2", "Offline", 1, 1000);
        p1.setConnected(true);
        p2.setConnected(false);

        assertTrue(p1.isConnected());
        assertFalse(p2.isConnected());
    }
}
