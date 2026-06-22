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
        // currentPlayerId: the playerId of whoever should act next.
        // The frontend uses this for isMyTurn checks instead of indexing
        // into the players array (which may be in a different order).
        if (state.currentPlayerIndex() >= 0 && state.currentPlayerIndex() < state.players().size()) {
            map.put("currentPlayerId", state.currentPlayer().playerId());
        }
        map.put("smallBlind", state.smallBlindAmount());
        map.put("bigBlind", state.bigBlindAmount());
        map.put("dealerIndex", state.dealerIndex());
        if (state.dealerIndex() >= 0 && state.dealerIndex() < state.players().size()) {
            map.put("dealerPlayerId", state.players().get(state.dealerIndex()).playerId());
        }
        map.put("timeLeftSec", 30);
        map.put("minRaise", state.minRaise());
        boolean isShowdown = state.phase() == GamePhase.SHOWDOWN;

        Map<String, Player> roomPlayerMap = room != null
            ? room.getPlayers().stream().collect(Collectors.toMap(Player::getPlayerId, p -> p, (a, b) -> a))
            : null;

        // If we have room data, include ALL room players (even 0-chip excluded ones).
        // Otherwise fall back to iterating over game-state players only.
        if (roomPlayerMap != null) {
            map.put("players", roomPlayerMap.values().stream().map(rp -> {
                var sp = state.players().stream()
                    .filter(gp -> gp.playerId().equals(rp.getPlayerId()))
                    .findFirst().orElse(null);
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("playerId", rp.getPlayerId());
                pm.put("nickname", rp.getNickname());
                pm.put("seatIndex", rp.getSeatIndex());
                pm.put("chips", sp != null ? sp.chips() : rp.getChips());
                pm.put("betInRound", sp != null ? sp.roundBet() : 0);
                pm.put("folded", sp != null ? sp.folded() : false);
                pm.put("allIn", sp != null ? sp.allIn() : false);
                pm.put("holeCards", (isShowdown && sp != null && !sp.folded())
                    ? sp.holeCards().stream().map(Card::toString).toList() : null);
                pm.put("lastAction", null);
                pm.put("connected", rp.isConnected());
                pm.put("owner", rp.isOwner());
                pm.put("borrowCount", rp.getBorrowCount());
                if (sp == null) {
                    pm.put("inGame", false);
                }
                return pm;
            }).toList());
        } else {
            // No room data available — build from game state only (tests mainly)
            map.put("players", state.players().stream().map(p -> {
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
                pm.put("connected", true);
                pm.put("owner", false);
                pm.put("borrowCount", 0);
                return pm;
            }).toList());
        }
        return map;
    }

    public static Map<String, Object> buildForPlayer(GameState state, String playerId, Room room) {
        Map<String, Object> map = buildPublic(state, room);
        var myPlayer = state.players().stream()
            .filter(p -> p.playerId().equals(playerId)).findFirst().orElse(null);
        if (myPlayer != null) {
            map.put("myHoleCards", myPlayer.holeCards().stream().map(Card::toString).toList());
        }
        if (state.currentPlayerIndex() >= 0) {
            var actions = ActionValidator.legalActions(state);
            map.put("legalActions", actions.stream()
                .map(a -> Map.of("type", a.type().name(), "minAmount", a.minAmount(), "maxAmount", a.maxAmount()))
                .toList());
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
