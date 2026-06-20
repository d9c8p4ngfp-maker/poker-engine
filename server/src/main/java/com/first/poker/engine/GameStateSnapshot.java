package com.first.poker.engine;

import com.first.poker.model.Player;
import com.first.poker.model.Room;

import java.util.*;
import java.util.stream.Collectors;

public class GameStateSnapshot {

    public static Map<String, Object> buildPublic(GameState state, Room room) {
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
        map.put("minRaise", state.minRaise());
        boolean isShowdown = state.phase() == GamePhase.SHOWDOWN;

        Map<String, Player> roomPlayerMap = room != null
            ? room.getPlayers().stream().collect(Collectors.toMap(Player::getPlayerId, p -> p, (a, b) -> a))
            : Collections.emptyMap();

        map.put("players", state.players().stream().map(p -> {
            Player rp = roomPlayerMap.get(p.playerId());
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("playerId", p.playerId());
            pm.put("nickname", p.nickname());
            pm.put("seatIndex", p.seatIndex());
            pm.put("chips", p.chips());
            pm.put("betInRound", p.roundBet());
            pm.put("folded", p.folded());
            pm.put("allIn", p.allIn());
            pm.put("holeCards", isShowdown && !p.folded() ? p.holeCards().stream().map(Card::toString).toList() : null);
            pm.put("lastAction", null);
            pm.put("connected", rp != null ? rp.isConnected() : true);
            pm.put("owner", rp != null ? rp.isOwner() : false);
            pm.put("borrowCount", rp != null ? rp.getBorrowCount() : 0);
            return pm;
        }).toList());
        return map;
    }

    public static Map<String, Object> buildForPlayer(GameState state, String playerId, Room room) {
        Map<String, Object> map = buildPublic(state, room);
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
