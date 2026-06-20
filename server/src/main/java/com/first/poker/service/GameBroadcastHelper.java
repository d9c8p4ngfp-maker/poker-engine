package com.first.poker.service;

import com.first.poker.engine.GameAction;
import com.first.poker.engine.GameEngine;
import com.first.poker.engine.GameState;
import com.first.poker.engine.GameStateSnapshot;
import com.first.poker.model.Room;
import org.springframework.context.annotation.Lazy;
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
                                BroadcastService broadcast, @Lazy GameTimeoutScheduler timeoutScheduler) {
        this.roomService = roomService;
        this.gameSession = gameSession;
        this.broadcast = broadcast;
        this.timeoutScheduler = timeoutScheduler;
    }

    public void broadcastGameState(String roomId, GameState state) {
        var room = roomService.findRoom(roomId);
        broadcast.sendToRoom(roomId, "game", GameStateSnapshot.buildPublic(state, room));
        for (var p : state.players()) {
            broadcast.sendToPlayer(p.playerId(), GameStateSnapshot.buildForPlayer(state, p.playerId(), room));
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

        boolean anyBusted = room.getPlayers().stream()
            .filter(p -> p.getStatus() != com.first.poker.model.enums.PlayerStatus.LEFT)
            .anyMatch(p -> p.getChips() <= 0);
        if (!anyBusted) return false;

        if (room.getConfig().isBustEndsGame()) {
            System.out.println("[GAME-OVER] " + roomId + " bust-ends-game mode, first bust detected");
            broadcastGameOver(roomId, room, result);
            return true;
        }

        long activePlayers = room.getPlayers().stream()
            .filter(p -> p.getStatus() != com.first.poker.model.enums.PlayerStatus.LEFT)
            .filter(p -> p.getChips() > 0).count();
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

    public void handleTimeout(String roomId, String playerId) {
        System.out.println("[TIMEOUT] " + roomId + " player=" + playerId);
        gameSession.executeWithLock(roomId, () -> {
            try {
                GameEngine.ActionResult result = gameSession.applyAction(roomId, playerId, GameAction.FOLD, 0);
                broadcastGameState(roomId, result.state());

                if (result.handComplete()) {
                    timeoutScheduler.cancelTimeout(roomId);
                    com.first.poker.engine.GameState finalState = result.state();
                    gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
                    if (!checkGameOver(roomId, result) && !result.winners().isEmpty()) {
                        broadcastWinners(roomId, result);
                    }
                    return;
                }

                autoPlayBots(roomId);

                // Schedule timeout for the next human player
                var state = gameSession.getState(roomId);
                if (state != null) scheduleNextTimeout(roomId, state);
            } catch (Exception e) {
                System.err.println("[TIMEOUT-ERROR] " + roomId + "/" + playerId + ": " + e.getMessage());
                // Try to keep game moving even if timeout fold fails
                autoPlayBots(roomId);
            }
        });
    }

    public void scheduleNextTimeout(String roomId, GameState state) {
        var cp = state.currentPlayer();
        if (cp != null && !cp.playerId().startsWith("bot-") && !cp.folded() && !cp.allIn()) {
            System.out.println("[TIMEOUT-SCHEDULE] " + roomId + " for " + cp.playerId() + " (30s)");
            timeoutScheduler.scheduleTimeout(roomId, cp.playerId(), 30);
        }
    }

    public void cancelTimeout(String roomId) {
        timeoutScheduler.cancelTimeout(roomId);
    }
    public void autoPlayBots(String roomId) {
        System.out.println("[AUTOBOTS-START] " + roomId);
        int safety = 0;
        while (safety++ < 30) {
            var state = gameSession.getState(roomId);
            if (state == null || state.currentPlayerIndex() < 0) break;
            var cp = state.currentPlayer();
            if (cp.folded() || cp.allIn()) break;

            boolean isBot = cp.playerId().startsWith("bot-");
            boolean isZeroChipHuman = !isBot && cp.chips() <= 0;

            if (!isBot && !isZeroChipHuman) break; // Human with chips — stop

            GameAction autoAction;
            int amount = 0;
            if (isBot) {
                int toCall = state.currentBet() - cp.roundBet();
                autoAction = toCall <= 0 ? GameAction.CHECK : GameAction.CALL;
            } else {
                autoAction = GameAction.FOLD; // 0-chip human always folds
            }

            System.out.println("[AUTOPLAY] " + roomId + " " + cp.playerId()
                + " action=" + autoAction + " chips=" + cp.chips());
            System.out.flush();

            try {
                var result = gameSession.applyAction(roomId, cp.playerId(), autoAction, amount);
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
                System.out.println("[autoPlayBot] " + cp.playerId() + " turn already processed, continuing");
                continue;
            } catch (Throwable e) {
                System.err.println("[autoPlayBot] " + cp.playerId() + ": " + e.getClass().getName() + " - " + e.getMessage());
                continue;
            }
        }
        // Bots finished — schedule timeout for the next human player
        var state = gameSession.getState(roomId);
        if (state != null) scheduleNextTimeout(roomId, state);
    }
}
