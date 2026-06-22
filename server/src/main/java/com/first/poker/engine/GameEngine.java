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

        // Cap bet/raise to total chips (roundBet + remaining chips) BEFORE validation
        amount = Math.min(amount, state.currentPlayer().roundBet() + state.currentPlayer().chips());

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

        // Sole survivor: after a FOLD, if only 1 non-folded player remains, they win
        if (action == GameAction.FOLD) {
            long nonFolded = newState.players().stream().filter(p -> !p.folded()).count();
            if (nonFolded == 1) {
                var survivor = newState.players().stream().filter(p -> !p.folded()).findFirst().orElseThrow();
                int totalPot = newState.pot();
                var updated = new GamePlayerState(survivor.playerId(), survivor.nickname(), survivor.seatIndex(),
                    survivor.chips() + totalPot, 0, 0, survivor.folded(), survivor.allIn(), survivor.holeCards());
                var mutablePlayers = new ArrayList<>(newState.players());
                for (int i = 0; i < mutablePlayers.size(); i++) {
                    if (mutablePlayers.get(i).playerId().equals(survivor.playerId())) {
                        mutablePlayers.set(i, updated);
                        break;
                    }
                }
                var finalState = newState.withPlayers(List.copyOf(mutablePlayers));
                events.add("SOLE_SURVIVOR");
                return new ActionResult(finalState, events, true, List.of(
                    new WinnerInfo(survivor.playerId(), survivor.nickname(), "Last Standing", totalPot)));
            }
        }

        if (BettingRoundManager.isRoundComplete(newState, newState.actedMask(), newState.lastAggressorIndex())) {
            newState = PhaseTransition.advancePhase(newState);
            events.add("PHASE_ADVANCED");

            // Fast-forward through phases when no more betting is possible
            // Condition: 0 or 1 non-all-in active players remain
            while (newState.phase() != GamePhase.SHOWDOWN && newState.phase() != GamePhase.HAND_OVER) {
                long nonAllInActive = newState.players().stream()
                    .filter(p -> !p.folded() && !p.allIn())
                    .count();
                if (nonAllInActive <= 1) {
                    newState = PhaseTransition.advancePhase(newState);
                    events.add("PHASE_ADVANCED");
                } else {
                    break;
                }
            }

            if (newState.phase() == GamePhase.SHOWDOWN) {
                try {
                var hands = HandResolver.resolveHands(newState.players(), newState.communityCards());
                var pots = HandResolver.distributePots(newState.players(), hands);

                List<WinnerInfo> winners = new ArrayList<>();
                Map<String, Integer> chipIncreases = new HashMap<>();

                for (var pot : pots) {
                    List<String> winnerIds = pot.winnerIds();
                    int share = pot.amount() / winnerIds.size();
                    int remainder = pot.amount() % winnerIds.size();
                    for (int w = 0; w < winnerIds.size(); w++) {
                        String wid = winnerIds.get(w);
                        int award = share + (w < remainder ? 1 : 0);
                        var p = newState.players().stream()
                            .filter(pl -> pl.playerId().equals(wid)).findFirst().orElse(null);
                        if (p != null) {
                            var hand = hands.get(wid);
                            winners.add(new WinnerInfo(wid, p.nickname(),
                                hand != null ? hand.name() : "Unknown", award));
                            chipIncreases.merge(wid, award, Integer::sum);
                        }
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
                } catch (Throwable e) {
                    throw new RuntimeException("Showdown failed", e);
                }
            }
        }

        return new ActionResult(newState, events, false, List.of());
    }
}
