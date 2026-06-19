package com.first.poker.engine;

import com.first.poker.model.RoomConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameEngine {

    public record ActionResult(GameState state, List<String> events, boolean handComplete,
                                List<WinnerInfo> winners) {}

    public record WinnerInfo(String playerId, String nickname, String handName, int amount) {}

    public static ActionResult startHand(List<GamePlayerState> players, int dealerIndex, RoomConfig config) {
        GameState state = PhaseTransition.startHand(players, dealerIndex, config);
        List<String> events = new ArrayList<>();
        events.add("BLINDS_POSTED");
        events.add("CARDS_DEALT");
        return new ActionResult(state, events, false, List.of());
    }

    public static ActionResult processAction(GameState state, GameAction action, int amount) {
        List<String> events = new ArrayList<>();

        if (state.phase() == GamePhase.SHOWDOWN || state.phase() == GamePhase.HAND_OVER) {
            throw new IllegalArgumentException("Hand is already over");
        }

        ActionValidator.validate(state, action, amount);

        GameState newState = BettingRoundManager.applyAction(
            state, action, amount, state.actedMask(), state.lastAggressorIndex());

        String event = switch (action) {
            case FOLD -> "PLAYER_FOLDED";
            case CHECK -> "PLAYER_CHECKED";
            case CALL -> "PLAYER_CALLED";
            case BET -> "PLAYER_BET";
            case RAISE -> "PLAYER_RAISED";
        };
        events.add(event);

        if (BettingRoundManager.isRoundComplete(newState, newState.actedMask(), newState.lastAggressorIndex())) {
            newState = PhaseTransition.advancePhase(newState);
            events.add("PHASE_ADVANCED");

            if (newState.phase() == GamePhase.SHOWDOWN) {
                var hands = HandResolver.resolveHands(newState.players(), newState.communityCards());
                var pots = HandResolver.distributePots(newState.players(), hands);

                // Build winner info and distribute chips
                List<WinnerInfo> winners = new ArrayList<>();
                Map<String, Integer> chipIncreases = new HashMap<>();

                for (var pot : pots) {
                    String winnerId = pot.winnerId();
                    var p = newState.players().stream()
                        .filter(pl -> pl.playerId().equals(winnerId)).findFirst().orElse(null);
                    if (p != null) {
                        var hand = hands.get(winnerId);
                        winners.add(new WinnerInfo(winnerId, p.nickname(),
                            hand != null ? hand.name() : "Unknown", pot.amount()));
                        chipIncreases.merge(winnerId, pot.amount(), Integer::sum);
                    }
                }

                // Apply chip increases to game state
                var mutablePlayers = new ArrayList<>(newState.players());
                for (int i = 0; i < mutablePlayers.size(); i++) {
                    var p = mutablePlayers.get(i);
                    Integer increase = chipIncreases.get(p.playerId());
                    if (increase != null) {
                        mutablePlayers.set(i,
                            new GamePlayerState(p.playerId(), p.nickname(), p.seatIndex(),
                                p.chips() + increase, p.totalBet(), p.roundBet(),
                                p.folded(), p.allIn(), p.holeCards()));
                    }
                }

                var resolvedState = newState.withPlayers(List.copyOf(mutablePlayers));

                events.add("HAND_RESOLVED");
                return new ActionResult(resolvedState, events, true, winners);
            }
        }

        return new ActionResult(newState, events, false, List.of());
    }
}
