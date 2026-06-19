package com.first.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

class SidePotCalculatorTest {

    @Test
    void shouldAssignSinglePotWhenNoAllIn() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 100, false),
            new SidePotCalculator.PlayerStake("B", 100, false)
        );
        var handRanks = Map.of("A", 100, "B", 50);
        var pots = SidePotCalculator.calculate(stakes, handRanks);
        assertEquals(1, pots.size());
        assertEquals(200, pots.get(0).amount());
    }

    @Test
    void shouldCreateSidePotWhenOnePlayerAllIn() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 50, false),
            new SidePotCalculator.PlayerStake("B", 100, false)
        );
        var handRanks = Map.of("A", 50, "B", 100);
        var pots = SidePotCalculator.calculate(stakes, handRanks);
        assertEquals(2, pots.size());
        assertEquals(100, pots.get(0).amount());
        assertEquals(50, pots.get(1).amount());
    }

    @Test
    void shouldHandleThreePlayersDifferentAllIn() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 30, false),
            new SidePotCalculator.PlayerStake("B", 60, false),
            new SidePotCalculator.PlayerStake("C", 100, false)
        );
        var handRanks = Map.of("A", 30, "B", 60, "C", 100);
        var pots = SidePotCalculator.calculate(stakes, handRanks);
        int totalAmount = pots.stream().mapToInt(p -> p.amount()).sum();
        assertEquals(190, totalAmount);
    }

    @Test
    void shouldExcludeFoldedPlayers() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 100, true),
            new SidePotCalculator.PlayerStake("B", 100, false)
        );
        var handRanks = Map.of("A", 50, "B", 100);
        var pots = SidePotCalculator.calculate(stakes, handRanks);
        assertEquals(1, pots.size());
        assertEquals(200, pots.get(0).amount());
        assertTrue(pots.get(0).eligiblePlayerIds().contains("B"));
        assertFalse(pots.get(0).eligiblePlayerIds().contains("A"));
    }
}
