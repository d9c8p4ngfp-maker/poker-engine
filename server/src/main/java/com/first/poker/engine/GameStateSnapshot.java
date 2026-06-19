package com.first.poker.engine;

import java.util.*;

public class GameStateSnapshot {

    public static Map<String, Object> buildPublic(GameState state) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("phase", phaseString(state.phase()));
        map.put("bettingRound", phaseString(state.phase()));
        map.put("status", state.phase() == GamePhase.SHOWDOWN ? "FINISHED" : "PLAYING");
        map.put("communityCards", state.communityCards().stream().map(Card::toString).toList());
        map.put("pot", state.pot());
        map.put("currentBet", state.currentBet());
        map.put("currentPlayerIndex", state.currentPlayerIndex());
        map.put("smallBlind", state.smallBlindAmount());
        map.put("bigBlind", state.bigBlindAmount());
        map.put("dealerIndex", state.dealerIndex());
        map.put("timeLeftSec", 30);
        map.put("players", state.players().stream().map(p -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("playerId", p.playerId());
            pm.put("nickname", p.nickname());
            pm.put("seatIndex", p.seatIndex());
            pm.put("chips", p.chips());
            pm.put("betInRound", p.roundBet());
            pm.put("folded", p.folded());
            pm.put("allIn", p.allIn());
            pm.put("holeCards", null); // never leak in public
            pm.put("lastAction", null);
            pm.put("connected", true);
            return pm;
        }).toList());
        return map;
    }

    public static Map<String, Object> buildForPlayer(GameState state, String playerId) {
        Map<String, Object> map = buildPublic(state);
        var myPlayer = state.players().stream()
            .filter(p -> p.playerId().equals(playerId)).findFirst().orElse(null);
        if (myPlayer != null) {
            map.put("myHoleCards", myPlayer.holeCards().stream().map(Card::toString).toList());
        }
        return map;
    }

    private static String phaseString(GamePhase phase) {
        return switch (phase) {
            case PRE_FLOP -> "PREFLOP";
            case FLOP -> "FLOP";
            case TURN -> "TURN";
            case RIVER -> "RIVER";
            case SHOWDOWN -> "SHOWDOWN";
            case HAND_OVER -> "SHOWDOWN";
        };
    }
}
