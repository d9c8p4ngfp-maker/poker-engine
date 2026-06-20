package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

/**
 * Integration tests derived from @idealic/poker-engine fixtures (3000+ test suite).
 * Each test verifies a specific poker scenario that the engine must handle correctly.
 */
class PokerEngineFixtureTest {

    private GamePlayerState player(String id, int chips) {
        return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, List.of());
    }

    private RoomConfig config(int sb, int bb) {
        RoomConfig c = RoomConfig.withDefaults();
        c.setSmallBlind(sb);
        // bigBlind auto = sb * 2
        return c;
    }

    /// ── Side Pot & Folded Player Contributions ──
    /// Mirrors @idealic/poker-engine stacks.test.ts "winners_folded"

    @Test @DisplayName("Folded player's bet should contribute to pot won by non-folded player")
    void sidePotShouldIncludeFoldedPlayerStakes() {
        // A bets 100 and folds, B bets 200 and stays
        // A's 100 should still be part of the pot, winnable by B
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 100, true),   // folded
            new SidePotCalculator.PlayerStake("B", 200, false)   // active
        );
        var handRanks = Map.of("B", 100); // B has the only hand rank

        var pots = SidePotCalculator.calculate(stakes, handRanks);

        // Total pot should be 300 (A's 100 + B's 200)
        int totalAmount = pots.stream().mapToInt(SidePotCalculator.PotResult::amount).sum();
        assertEquals(300, totalAmount,
            "Folded player A contributed 100, should be included in total pot");

        // B should win everything
        String winner = pots.stream()
            .filter(p -> p.winnerId().equals("B"))
            .findFirst().map(SidePotCalculator.PotResult::winnerId).orElse(null);
        assertNotNull(winner, "B should win at least one pot");
    }

    @Test @DisplayName("Multi-level all-in: 4 players with different stack depths")
    void multiLevelAllInShouldCreateCorrectSidePots() {
        // mirrors stacks.test.ts "multiway_winner_takes_all"
        // p0: totalBet=200, not folded, best hand → should win all
        // p1: totalBet=150, allIn
        // p2: totalBet=200, not folded
        // p3: totalBet=100, allIn
        // p4: totalBet=50, folded → contributes but can't win
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("p0", 200, false),
            new SidePotCalculator.PlayerStake("p1", 150, false),
            new SidePotCalculator.PlayerStake("p2", 200, false),
            new SidePotCalculator.PlayerStake("p3", 100, false),
            new SidePotCalculator.PlayerStake("p4", 50, true)  // folded
        );
        var handRanks = Map.of("p0", 100, "p1", 80, "p2", 60, "p3", 40);
        // p4 is folded so no hand rank

        var pots = SidePotCalculator.calculate(stakes, handRanks);

        int totalAmount = pots.stream().mapToInt(SidePotCalculator.PotResult::amount).sum();
        assertEquals(700, totalAmount,
            "Total: 200+150+200+100+50 = 700. Folded p4's 50 included.");
    }

    @Test @DisplayName("Uncalled bet: two all-in at 100, one player bets 1000")
    void uncalledBetReturn() {
        // p0 bets 1000, p1 and p2 call only 100 (all-in)
        // The called portion = 300 (3 × 100). Uncalled 900 goes back to p0.
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("p0", 1000, false),
            new SidePotCalculator.PlayerStake("p1", 100, false),
            new SidePotCalculator.PlayerStake("p2", 100, false)
        );
        var handRanks = Map.of("p0", 100, "p1", 80, "p2", 60);

        var pots = SidePotCalculator.calculate(stakes, handRanks);

        int totalAmount = pots.stream().mapToInt(SidePotCalculator.PotResult::amount).sum();
        // All 1200 goes to p0 (300 called + 900 uncalled returned)
        assertEquals(1200, totalAmount,
            "Total pot should be 1200: called portion (300) + uncalled return (900)");
        
        // p0 should win all pots
        boolean p0WinsAll = pots.stream().allMatch(p -> p.winnerId().equals("p0"));
        assertTrue(p0WinsAll, "p0 should win all pots since p1/p2 have worse hands");
    }

    @Test @DisplayName("Split pot: two players tie, share equally")
    void splitPotEvenlyBetweenTwoWinners() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("p0", 100, false),
            new SidePotCalculator.PlayerStake("p1", 100, false),
            new SidePotCalculator.PlayerStake("p2", 100, false)
        );
        var handRanks = Map.of("p0", 100, "p1", 100, "p2", 50);
        // p0 and p1 tie with rank 100; p2 has rank 50

        var pots = SidePotCalculator.calculate(stakes, handRanks);

        int totalAmount = pots.stream().mapToInt(SidePotCalculator.PotResult::amount).sum();
        assertEquals(300, totalAmount);

        // Should have at least 1 pot where both p0 and p1 are eligible
        boolean hasSplit = pots.stream().anyMatch(p -> {
            var e = p.eligiblePlayerIds();
            return e.contains("p0") && e.contains("p1");
        });
        assertTrue(hasSplit, "p0 and p1 should split at least one pot level");
    }


    /// ── Full Engine Integration Tests ──

    @Test @DisplayName("All-in preflop → auto-deal all streets → showdown")
    void allInPreflopShouldAutoDealAllStreets() {
        // mirrors croupier/03-all-in.test.ts
        // 3 players: BTN=pA(1000), SB=pB(1000), BB=pC(1000)
        // pA all-in for 1000, pB calls (also all-in), pC folds
        var players = List.of(player("pA", 1000), player("pB", 1000), player("pC", 1000));
        var cfg = config(10, 20);
        var start = GameEngine.startHand(players, 0, cfg);
        var state = start.state();

        // pA is UTG (button=0, SB=1, BB=2, UTG=0)
        // Wait - in startHand, utg = (bbIdx+1) % n = (2+1)%3 = 0 = pA
        // currentBet starts at bigBlind=20
        // pA has roundBet=0, must CALL 20
        assertEquals("pA", state.currentPlayer().playerId());

        // pA goes all-in: BET 1000 (actually BET with bigBlind=20, must call first)
        // In NL, pA can RAISE. Let's use BET for simplicity since currentBet=20>0
        var r1 = GameEngine.processAction(state, GameAction.RAISE, 1000);
        state = r1.state();
        assertFalse(r1.handComplete());
        // pB should be next (SB, already posted 10)
        assertEquals("pB", state.currentPlayer().playerId());
        // Verify pA is all-in with chips=0, totalBet=1000
        assertTrue(state.players().get(0).allIn(), "pA should be all-in");
        assertEquals(0, state.players().get(0).chips());

        // pB calls: CALL (toCall=1000-currentBet... actually pB has roundBet=10, toCall=990)
        var r2 = GameEngine.processAction(state, GameAction.CALL, 0);
        state = r2.state();
        assertTrue(state.players().get(1).allIn(), "pB should be all-in after calling");

        // pC should fold
        assertEquals("pC", state.currentPlayer().playerId());
        var r3 = GameEngine.processAction(state, GameAction.FOLD, 0);
        state = r3.state();

        // After everyone folds/all-in, engine should fast-forward to SHOWDOWN
        assertEquals(GamePhase.SHOWDOWN, state.phase(),
            "All active players are all-in/folded, should fast-forward to showdown");
        assertTrue(r3.handComplete(), "Hand should be complete at showdown");
        assertFalse(r3.winners().isEmpty(), "Should have at least one winner");

        // Verify total chips preserved: 1000+1000+1000 = 3000
        int totalChips = state.players().stream().mapToInt(GamePlayerState::chips).sum();
        assertEquals(3000, totalChips, "Total chips should be conserved");
    }

    @Test @DisplayName("Multi-way all-in with different stack depths")
    void multiWayAllInDifferentStacks() {
        // mirrors croupier/04-multi-way-all-in.test.ts
        // pA=50, pB=100, pC=200. All go all-in preflop
        var players = List.of(player("pA", 50), player("pB", 100), player("pC", 200));
        var cfg = config(10, 20);
        var start = GameEngine.startHand(players, 0, cfg);
        var state = start.state();

        // pA all-in (BET 50)
        assertEquals("pA", state.currentPlayer().playerId());
        state = GameEngine.processAction(state, GameAction.RAISE, 50).state();

        // pB all-in (RAISE to 100)
        assertEquals("pB", state.currentPlayer().playerId());
        state = GameEngine.processAction(state, GameAction.RAISE, 100).state();

        // pC calls 100 (BB already posted 20 → toCall=80)
        assertEquals("pC", state.currentPlayer().playerId());
        var result = GameEngine.processAction(state, GameAction.CALL, 0);
        state = result.state();

        // All all-in/folded → should fast-forward to showdown
        assertEquals(GamePhase.SHOWDOWN, state.phase());
        assertTrue(result.handComplete());

        // Total chips: 50+100+200 = 350
        int totalChips = state.players().stream().mapToInt(GamePlayerState::chips).sum();
        assertEquals(350, totalChips);
    }

    @Test @DisplayName("All players check down to showdown")
    void allPlayersCheckToShowdown() {
        var players = List.of(player("pA", 1000), player("pB", 1000), player("pC", 1000));
        var cfg = config(10, 20);
        var state = GameEngine.startHand(players, 0, cfg).state();

        // Preflop: all call/check
        state = GameEngine.processAction(state, GameAction.CALL, 0).state(); // pA calls 20
        state = GameEngine.processAction(state, GameAction.CALL, 0).state(); // pB calls 10 more
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state(); // pC checks
        assertEquals(GamePhase.FLOP, state.phase());

        // Flop: all check (pB first after dealer=0)
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.TURN, state.phase());

        // Turn: all check
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.RIVER, state.phase());

        // River: all check
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        var finalResult = GameEngine.processAction(state, GameAction.CHECK, 0);

        assertEquals(GamePhase.SHOWDOWN, finalResult.state().phase());
        assertTrue(finalResult.handComplete());
        assertFalse(finalResult.winners().isEmpty());

        int totalChips = finalResult.state().players().stream().mapToInt(GamePlayerState::chips).sum();
        assertEquals(3000, totalChips, "Total chips must be conserved at 3000");
    }

    @Test @DisplayName("Player fold then round continues for remaining players")
    void foldDoesNotEndHandWhenOthersRemain() {
        var players = List.of(player("pA", 1000), player("pB", 1000), player("pC", 1000));
        var cfg = config(10, 20);
        var state = GameEngine.startHand(players, 0, cfg).state();

        // pA folds
        state = GameEngine.processAction(state, GameAction.FOLD, 0).state();
        assertTrue(state.players().get(0).folded());

        // Game should continue with pB and pC
        assertNotNull(state.currentPlayer());
        assertFalse(state.currentPlayer().playerId().equals("pA"));
    }

    @Test @DisplayName("Should not distribute pot multiple times for same hand")
    void potDistributedOnlyOncePerHand() {
        var players = List.of(player("pA", 1000), player("pB", 1000));
        var cfg = config(10, 20);
        var state = GameEngine.startHand(players, 0, cfg).state();

        // Heads-up: pA calls, pB checks → FLOP
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        var toFlop = GameEngine.processAction(state, GameAction.CHECK, 0);
        state = toFlop.state();
        assertEquals(GamePhase.FLOP, state.phase());
        assertFalse(toFlop.handComplete());

        // Flop: check, check → TURN
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.TURN, state.phase());

        // Turn: check, check → RIVER
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        assertEquals(GamePhase.RIVER, state.phase());

        // River: check, check → SHOWDOWN
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        var finalResult = GameEngine.processAction(state, GameAction.CHECK, 0);

        assertTrue(finalResult.handComplete());
        assertEquals(GamePhase.SHOWDOWN, finalResult.state().phase());

        // Total chips after one pot distribution should still be 2000
        int totalChips = finalResult.state().players().stream().mapToInt(GamePlayerState::chips).sum();
        assertEquals(2000, totalChips, "Chips should not be created or destroyed");
    }

    @Test @DisplayName("allIn flag set correctly when chips reach 0")
    void allInFlagWhenChipsReachZero() {
        var players = List.of(player("pA", 100), player("pB", 1000));
        var cfg = config(10, 20);
        var state = GameEngine.startHand(players, 0, cfg).state();

        // Heads-up with dealer=0: pB is SB/dealer (index 1), pA is BB (index 0)
        // Preflop: pB (SB/button) acts first
        assertEquals("pB", state.currentPlayer().playerId());
        // pB raises to 100 (pA has 80 left after BB 20, will be all-in if calls)
        state = GameEngine.processAction(state, GameAction.RAISE, 100).state();

        // pA (BB) now acts with chips=80, calls 80 → all-in
        assertEquals("pA", state.currentPlayer().playerId());
        var result = GameEngine.processAction(state, GameAction.CALL, 0);

        var pA = result.state().players().get(0);
        assertTrue(pA.allIn(), "Player who called with all remaining chips should be marked allIn");
        // After pA goes all-in, only pB remains active → round completes, hand auto-resolves
        assertTrue(result.handComplete(), "Heads-up all-in should complete the hand");
    }
}
