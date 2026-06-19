package com.first.poker.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomConfigTest {

    @Test
    void shouldCreateWithDefaultValues() {
        RoomConfig config = RoomConfig.withDefaults();

        assertEquals("默认牌局", config.getName());
        assertNull(config.getPassword());
        assertEquals(8, config.getMaxSeats());
        assertEquals(2, config.getMinPlayers());
        assertEquals(1000, config.getInitialChips());
        assertEquals(10, config.getSmallBlind());
        assertEquals(20, config.getBigBlind());
        assertEquals(30, config.getActionTimeoutSec());
        assertFalse(config.isAllowSpectate());
        assertEquals(RoomConfig.LeaveHandling.AUTO_FOLD, config.getLeaveHandling());
        assertEquals(RoomConfig.BuyInRule.ONCE_ONLY, config.getBuyInRule());
        assertTrue(config.isRecordHistory());
    }

    @Test
    void shouldAutoSetBigBlindAsDoubleSmallBlind() {
        RoomConfig config = RoomConfig.withDefaults();
        config.setSmallBlind(25);
        assertEquals(50, config.getBigBlind());
    }

    @Test
    void shouldRejectInvalidSmallBlind() {
        RoomConfig config = RoomConfig.withDefaults();
        assertThrows(IllegalArgumentException.class, () -> config.setSmallBlind(0));
    }

    @Test
    void shouldRejectInvalidMaxSeats() {
        RoomConfig config = RoomConfig.withDefaults();
        assertThrows(IllegalArgumentException.class, () -> config.setMaxSeats(1));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxSeats(9));
    }
}
