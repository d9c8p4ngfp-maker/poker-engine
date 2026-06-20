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
                int totalPot = newState.players().stream().mapToInt(GamePlayerState::totalBet).sum();
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
                System.err.println("[SD-START] entering showdown resolution");
                System.err.flush();
                try {
                System.err.println("[SD-1] " + newState.players().size() + " players");
                System.err.flush();
                for (var p : newState.players()) {
                    System.err.println("[SD-P] " + p.playerId() + " chips=" + p.chips() + " totalBet=" + p.totalBet() + " folded=" + p.folded() + " allIn=" + p.allIn() + " cards=" + (p.holeCards() != null ? p.holeCards().size() : 0));
                }
                System.err.println("[SD-2] communityCards=" + newState.communityCards().size());
                System.err.flush();
                var hands = HandResolver.resolveHands(newState.players(), newState.communityCards());
                System.err.println("[SD-3] hands=" + hands.size());
                System.err.flush();
                var pots = HandResolver.distributePots(newState.players(), hands);
                System.err.println("[SD-4] pots=" + pots.size());
                System.err.flush();

                // Build winner info and distribute chips
                System.err.println("[SD-5] building winners...");
                System.err.flush();
                List<WinnerInfo> winners = new ArrayList<>();
                Map<String, Integer> chipIncreases = new HashMap<>();

                for (var pot : pots) {
                    System.err.println("[SD-6] processing pot winner=" + pot.winnerId() + " amount=" + pot.amount());
                    System.err.flush();
                    String winnerId = pot.winnerId();
                    var p = newState.players().stream()
                        .filter(pl -> pl.playerId().equals(winnerId)).findFirst().orElse(null);
                    if (p != null) {
                        var hand = hands.get(winnerId);
                        System.err.println("[SD-7] " + winnerId + " hand=" + (hand != null ? hand.name() : "null"));
                        System.err.flush();
                        winners.add(new WinnerInfo(winnerId, p.nickname(),
                            hand != null ? hand.name() : "Unknown", pot.amount()));
                        chipIncreases.merge(winnerId, pot.amount(), Integer::sum);
                        System.err.println("[SD-8] chipIncreases " + winnerId + "=" + chipIncreases.get(winnerId));
                        System.err.flush();
                    }
                }

                // Apply chip increases to game state
                System.err.println("[SD-9] applying chip increases...");
                System.err.flush();
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

                System.err.println("[SD-10] creating resolved state...");
                System.err.flush();
                var resolvedState = newState.withPlayers(List.copyOf(mutablePlayers));

                System.err.println("[SD-11] returning result...");
                System.err.flush();
                events.add("HAND_RESOLVED");
                return new ActionResult(resolvedState, events, true, winners);
                } catch (Throwable e) {
                    System.err.println("[SHOWDOWN-CRASH] " + e.getClass().getName() + ": " + e.getMessage());
                    e.printStackTrace(System.err);
                    System.err.flush();
                    throw new RuntimeException("Showdown failed", e);
                }
            }
        }

        return new ActionResult(newState, events, false, List.of());
    }
}
