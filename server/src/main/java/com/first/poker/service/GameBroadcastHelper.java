package com.first.poker.service;

import com.first.poker.engine.GameEngine;
import com.first.poker.engine.GameState;
import com.first.poker.engine.GameStateSnapshot;
import com.first.poker.model.Room;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Shared game broadcast logic used by both GameMessageController (STOMP) and
 * RoomController (REST). Eliminates the 5-method duplication previously in
 * RoomController (syncChips/autoBots/checkGameOver/broadcastGameOver/broadcastBustChoice).
 */
@Service
public class GameBroadcastHelper {

    private final RoomService roomService;
    private final GameSessionService gameSession;
    private final BroadcastService broadcast;
    private final GameTimeoutScheduler timeoutScheduler;

    public GameBroadcastHelper(RoomService roomService, GameSessionService gameSession,
                                BroadcastService broadcast, GameTimeoutScheduler timeoutScheduler) {
        this.roomService = roomService;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
        this.timeoutScheduler = timeoutScheduler;
    }

    public void broadcastGameState(String roomId, GameState state) {
        broadcast.sendToRoom(roomId, "game", GameStateSnapshot.buildPublic(state));
        for (var p : state.players()) {
            broadcast.sendToPlayer(p.playerId(), GameStateSnapshot.buildForPlayer(state, p.playerId()));
        }
    }

    public void broadcastWinners(String roomId, GameEngine.ActionResult result) {
        var payload = new HashMap<String, Object>();
        payload.put("winners", result.winners().stream()
            .map(w -> java.util.Map.of("playerId", w.playerId(), "nickname", w.nickname(),
                "handName", w.handName(), "amount", w.amount()))
            .toList());
        broadcast.sendToRoom(roomId, "game", payload);
    }

    public void syncRoomChips(String roomId, GameState resolvedState) {
        var room = roomService.findRoom(roomId);
        if (room == null) return;
        for (var gsp : resolvedState.players()) {
            room.getPlayers().stream()
                .filter(rp -> rp.getPlayerId().equals(gsp.playerId()))
                .findFirst()
                .ifPresent(rp -> {
                    int oldChips = rp.getChips();
                    int newChips = gsp.chips();
                    rp.setChips(newChips);
                    rp.setBetInRound(0);
                    rp.setFolded(false);
                    rp.setAllIn(false);
                    rp.setHoleCards(new ArrayList<>());
                    if (oldChips > 0 && newChips <= 0) {
                        broadcastBustChoice(roomId, rp.getPlayerId(), rp.getNickname());
                    }
                });
        }
    }

    public void broadcastBustChoice(String roomId, String playerId, String nickname) {
        var payload = new HashMap<String, Object>();
        payload.put("type", "bust_choice");
        payload.put("playerId", playerId);
        payload.put("nickname", nickname);
        System.out.println("[BUST-CHOICE] sending to " + roomId + "/" + playerId);
        broadcast.sendToPlayer(playerId, payload);
    }

    public boolean checkGameOver(String roomId, GameEngine.ActionResult result) {
        var room = roomService.findRoom(roomId);
        if (room == null) return false;

        boolean anyBusted = room.getPlayers().stream().anyMatch(p -> p.getChips() <= 0);
        if (!anyBusted) return false;

        if (room.getConfig().isBustEndsGame()) {
            System.out.println("[GAME-OVER] " + roomId + " bust-ends-game mode, first bust detected");
            broadcastGameOver(roomId, room, result);
            return true;
        }

        long activePlayers = room.getPlayers().stream().filter(p -> p.getChips() > 0).count();
        if (activePlayers <= 1) {
            System.out.println("[GAME-OVER] " + roomId + " last standing (active=" + activePlayers + ")");
            broadcastGameOver(roomId, room, result);
            return true;
        }

        return false;
    }

    public void broadcastGameOver(String roomId, Room room, GameEngine.ActionResult result) {
        var payload = new HashMap<String, Object>();
        payload.put("winners", result.winners().stream()
            .map(w -> java.util.Map.of("playerId", w.playerId(), "nickname", w.nickname(),
                "handName", w.handName(), "amount", w.amount()))
            .toList());

        int borrowUnit = room.getConfig().getInitialChips();
        payload.put("leaderboard", room.getPlayers().stream()
            .sorted((a, b) -> Integer.compare(
                b.getChips() - b.getBorrowCount() * borrowUnit,
                a.getChips() - a.getBorrowCount() * borrowUnit))
            .map(p -> java.util.Map.of("playerId", p.getPlayerId(), "nickname", p.getNickname(),
                "chips", p.getChips(), "borrowCount", p.getBorrowCount(),
                "borrowed", p.getBorrowCount() * borrowUnit,
                "netChips", p.getChips() - p.getBorrowCount() * borrowUnit))
            .toList());
        payload.put("bustedPlayerIds", room.getPlayers().stream()
            .filter(p -> p.getChips() <= 0).map(com.first.poker.model.Player::getPlayerId).toList());

        broadcast.sendToRoom(roomId, "game", payload);
    }

    public void autoPlayBots(String roomId) {
        System.out.println("[AUTOBOTS-START] " + roomId);
        int safety = 0;
        while (safety++ < 30) {
            var state = gameSession.getState(roomId);
            if (state == null || state.currentPlayerIndex() < 0) break;
            var cp = state.currentPlayer();
            if (!cp.playerId().startsWith("bot-")) break;

            if (cp.folded() || cp.allIn()) {
                break;
            }

            int toCall = state.currentBet() - cp.roundBet();
            com.first.poker.engine.GameAction botAction = toCall <= 0
                ? com.first.poker.engine.GameAction.CHECK
                : com.first.poker.engine.GameAction.CALL;
            int amount = 0;
            System.out.println("[AUTOBOT] " + roomId + " " + cp.playerId() + " action=" + botAction + " toCall=" + toCall + " chips=" + cp.chips());
            System.out.flush();

            try {
                var result = gameSession.applyAction(roomId, cp.playerId(), botAction, amount);
                broadcastGameState(roomId, result.state());

                if (result.handComplete()) {
                    timeoutScheduler.cancelTimeout(roomId);
                    com.first.poker.engine.GameState finalState = result.state();
                    gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
                    if (checkGameOver(roomId, result)) return;
                    if (!result.winners().isEmpty()) {
                        broadcastWinners(roomId, result);
                    }
                    return;
                }
            } catch (IllegalArgumentException e) {
                // Not our turn anymore — another thread processed this bot's turn
                // This is expected in the lock-check pattern
                System.out.println("[autoPlayBot] " + cp.playerId() + " turn already processed, continuing");
                continue;
            } catch (Throwable e) {
                System.err.println("[autoPlayBot] " + cp.playerId() + ": " + e.getClass().getName() + " - " + e.getMessage());
                continue;
            }
        }
    }
}
