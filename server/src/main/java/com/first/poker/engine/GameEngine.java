package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import java.util.ArrayList;
import java.util.List;

public class GameEngine {

    public record ActionResult(GameState state, List<String> events, boolean handComplete) {}

    public static ActionResult startHand(List<GamePlayerState> players, int dealerIndex, RoomConfig config) {
        GameState state = PhaseTransition.startHand(players, dealerIndex, config);
        List<String> events = new ArrayList<>();
        events.add("BLINDS_POSTED");
        events.add("CARDS_DEALT");
        return new ActionResult(state, events, false);
    }

    public static ActionResult processAction(GameState state, GameAction action, int amount) {
        List<String> events = new ArrayList<>();

        // If already at showdown, reject action
        if (state.phase() == GamePhase.SHOWDOWN || state.phase() == GamePhase.HAND_OVER) {
            throw new IllegalArgumentException("Hand is already over");
        }

        // Validate
        ActionValidator.validate(state, action, amount);

        // Apply action
        GameState newState = BettingRoundManager.applyAction(
            state, action, amount, state.actedMask(), state.lastAggressorIndex());

        // Record event
        String event = switch (action) {
            case FOLD -> "PLAYER_FOLDED";
            case CHECK -> "PLAYER_CHECKED";
            case CALL -> "PLAYER_CALLED";
            case BET -> "PLAYER_BET";
            case RAISE -> "PLAYER_RAISED";
        };
        events.add(event);

        // Check if betting round is complete
        if (BettingRoundManager.isRoundComplete(newState, newState.actedMask(), newState.lastAggressorIndex())) {
            newState = PhaseTransition.advancePhase(newState);
            events.add("PHASE_ADVANCED");

            // If showdown, resolve
            if (newState.phase() == GamePhase.SHOWDOWN) {
                var hands = HandResolver.resolveHands(newState.players(), newState.communityCards());
                var pots = HandResolver.distributePots(newState.players(), hands);
                events.add("HAND_RESOLVED");
                return new ActionResult(newState, events, true);
            }
        }

        return new ActionResult(newState, events, false);
    }
}
