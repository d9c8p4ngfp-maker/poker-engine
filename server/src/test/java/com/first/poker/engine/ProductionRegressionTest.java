package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ProductionRegressionTest {

    private GamePlayerState player(String id, int chips) {
        return new GamePlayerState(id, id, 0, chips, 0, 0, false, false, List.of());
    }

    private GamePlayerState playerAllIn(String id) {
        return new GamePlayerState(id, id, 0, 0, 0, 0, false, true, List.of());
    }

    private RoomConfig config(int sb) {
        RoomConfig c = RoomConfig.withDefaults();
        c.setSmallBlind(sb);
        return c;
    }

    @Test @DisplayName("Pot increases during betting round (regression)")
    void potVisibleDuringBettingRound() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        assertTrue(state.pot() > 0, "Pot should be non-zero after blinds posted");
    }

    @Test @DisplayName("Folded player's chips stay in pot")
    void foldedPlayerChipsStayInPot() {
        var stakes = List.of(
            new SidePotCalculator.PlayerStake("A", 100, true),
            new SidePotCalculator.PlayerStake("B", 200, false)
        );
        var handRanks = java.util.Map.of("B", 100);
        var pots = SidePotCalculator.calculate(stakes, handRanks);
        int total = pots.stream().mapToInt(SidePotCalculator.PotResult::amount).sum();
        assertEquals(300, total, "Folded A's 100 must be in total pot");
    }

    @Test @DisplayName("All-in preflop does not freeze")
    void allInPreflopDoesNotFreeze() {
        var players = List.of(player("A", 100), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // A goes all-in
        state = GameEngine.processAction(state, GameAction.RAISE, 100).state();
        assertTrue(state.players().get(0).allIn(), "A should be all-in");
    }

    @Test @DisplayName("Showdown reveals non-folded holeCards in public snapshot")
    void showdownRevealsNonFoldedHoleCards() {
        var players = List.of(player("A", 1000), player("B", 1000), player("C", 1000));
        var state = GameEngine.startHand(players, 0, config(10)).state();
        // Preflop: all call/check
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CALL, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        // Flop: all check
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        // Turn: all check
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        // River: all check → showdown
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        state = GameEngine.processAction(state, GameAction.CHECK, 0).state();
        var finalResult = GameEngine.processAction(state, GameAction.CHECK, 0);
        assertTrue(finalResult.handComplete());

        var snapshot = GameStateSnapshot.buildPublic(finalResult.state(), null);
        @SuppressWarnings("unchecked")
        var playerList = (List<java.util.Map<String, Object>>) snapshot.get("players");
        for (var pm : playerList) {
            if (!(boolean) pm.get("folded")) {
                assertNotNull(pm.get("holeCards"), "Non-folded player must show holeCards");
            }
        }
    }

    @Test @DisplayName("Zero-chips player cannot bet")
    void zeroChipsPlayerCannotBet() {
        // Create a state where current player has chips=0 and is allIn
        var activePlayer = player("A", 1000);
        var zeroPlayer = playerAllIn("Z");
        var players = List.of(zeroPlayer, activePlayer);
        var deck = new Deck(42L);
        var state = GameState.create(players, 0, 10, 20, deck);

        // Zero-chip allIn player has no legal actions
        var actions = ActionValidator.legalActions(state);
        assertTrue(actions.isEmpty(), "All-in player with 0 chips should have no actions");
    }
}
