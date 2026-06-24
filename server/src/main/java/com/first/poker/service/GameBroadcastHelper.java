package com.first.poker.service;

import com.first.poker.engine.BonusResolver;
import com.first.poker.engine.GameAction;
import com.first.poker.engine.GameEngine;
import com.first.poker.engine.GameState;
import com.first.poker.engine.GameStateSnapshot;
import com.first.poker.engine.HandEvaluator;
import com.first.poker.engine.HandResolver;
import com.first.poker.model.Room;
import com.first.poker.model.enums.PlayerStatus;
import com.first.poker.model.enums.RoomStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared game broadcast logic used by both GameMessageController (STOMP) and
 * RoomController (REST). Eliminates the 5-method duplication previously in
 * RoomController (syncChips/autoBots/checkGameOver/broadcastGameOver/broadcastBustChoice).
 */
@Service
public class GameBroadcastHelper {

    private static final Logger log = LoggerFactory.getLogger(GameBroadcastHelper.class);

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
        // Send personal snapshot to ALL room players (including spectators with 0 chips),
        // not just hand participants. This ensures every player receives per-seat state.
        if (room != null) {
            for (var rp : room.getPlayers()) {
                broadcast.sendToPlayer(rp.getPlayerId(), GameStateSnapshot.buildForPlayer(state, rp.getPlayerId(), room));
            }
        } else {
            for (var p : state.players()) {
                broadcast.sendToPlayer(p.playerId(), GameStateSnapshot.buildForPlayer(state, p.playerId(), null));
            }
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

    public void checkAndApplyBonuses(String roomId, Room room, GameState resolvedState, GameEngine.ActionResult result) {
        var config = room.getConfig();
        if (!config.isBonus27Enabled() && !config.isBonusStraightFlushEnabled()) return;

        // Collect participant chips from the room (pre-transfer)
        Map<String, Integer> participantChips = new LinkedHashMap<>();
        for (var rp : room.getPlayers()) {
            participantChips.put(rp.getPlayerId(), rp.getChips());
        }

        // Evaluate hand types for all non-folded players
        Map<String, HandEvaluator.HandResult> handResults = HandResolver.resolveHands(
            resolvedState.players(), resolvedState.communityCards());

        for (var winner : result.winners()) {
            var winnerPlayer = resolvedState.players().stream()
                .filter(p -> p.playerId().equals(winner.playerId())).findFirst().orElse(null);
            if (winnerPlayer == null) continue;

            // 2-7 Game check
            if (config.isBonus27Enabled()) {
                int amount = config.getBonus27Amount() > 0
                    ? config.getBonus27Amount()
                    : config.getBigBlind() * 5;
                BonusResolver.check27Game(winner.playerId(), winnerPlayer.holeCards(),
                    participantChips, amount).ifPresent(bonus -> {
                        applyBonus(room, bonus);
                        broadcastBonus(roomId, bonus);
                    });
            }

            // Straight Flush bonus check
            if (config.isBonusStraightFlushEnabled()) {
                var hand = handResults.get(winner.playerId());
                if (hand != null) {
                    int amount = config.getBonusStraightFlushAmount() > 0
                        ? config.getBonusStraightFlushAmount()
                        : config.getBigBlind() * 10;
                    BonusResolver.checkStraightFlushBonus(winner.playerId(), hand,
                        participantChips, amount, config.isBonusRoyalFlushDouble())
                        .ifPresent(bonus -> {
                            applyBonus(room, bonus);
                            broadcastBonus(roomId, bonus);
                        });
                }
            }
        }
    }

    private void applyBonus(Room room, BonusResolver.BonusResult bonus) {
        for (var entry : bonus.transfers().entrySet()) {
            String pid = entry.getKey();
            int amount = entry.getValue();
            room.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(pid))
                .findFirst()
                .ifPresent(p -> p.setChips(p.getChips() - amount));
        }
    }

    private void broadcastBonus(String roomId, BonusResolver.BonusResult bonus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "bonus");
        payload.put("bonusType", bonus.type());
        payload.put("winnerId", bonus.winnerId());
        payload.put("bonusPerPlayer", bonus.bonusPerPlayer());
        payload.put("transfers", bonus.transfers());
        var room = roomService.findRoom(roomId);
        if (room != null) {
            for (var rp : room.getPlayers()) {
                broadcast.sendToPlayer(rp.getPlayerId(), payload);
            }
        }
    }

    public void syncRoomChips(String roomId, GameState resolvedState) {
        var room = roomService.findRoom(roomId);
        log.info("[SYNC-CHIPS] {} room={} resolving chips for {} players", roomId, room, resolvedState.players().size());
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
                        rp.setStatus(PlayerStatus.SPECTATING);
                        broadcastBustChoice(roomId, rp.getPlayerId(), rp.getNickname());
                    }
                });
        }
        room.advanceDealer();
        room.setHandCount(room.getHandCount() + 1);
        // Only set WAITING and broadcast when bustEndsGame is off.
        // For bustEndsGame=true, checkGameOver will set FINISHED and broadcast
        // GameOver instead — sending WAITING here would cause a conflicting
        // lobby→game flicker on the frontend.
        if (!room.getConfig().isBustEndsGame()) {
            room.setStatus(com.first.poker.model.enums.RoomStatus.WAITING);
            // If only 1 player has chips left, the game can't continue —
            // wait to broadcast WAITING until AFTER winners are shown (handled
            // by checkGameOver). Broadcasting here would cause the frontend to
            // switch to lobby before it even sees who won the hand.
            long activePlayers = room.getPlayers().stream()
                .filter(p -> p.getStatus() != com.first.poker.model.enums.PlayerStatus.LEFT)
                .filter(p -> p.getChips() > 0).count();
            log.info("[SYNC-CHIPS] {} activePlayers={} bustEndsGame=false", roomId, activePlayers);
            if (activePlayers < 2) {
                log.info("[SYNC-CHIPS] {} activePlayers<2, deferring WAITING broadcast to checkGameOver", roomId);
                return;
            }
            // Broadcast room status change so frontend switches to lobby
            var roomPayload = new HashMap<String, Object>();
            roomPayload.put("roomId", room.getRoomId());
            roomPayload.put("name", room.getName());
            roomPayload.put("status", room.getStatus().name());
            roomPayload.put("players", room.getPlayers().stream().map(p -> {
                var pm = new HashMap<String, Object>();
                pm.put("playerId", p.getPlayerId());
                pm.put("nickname", p.getNickname());
                pm.put("seatIndex", p.getSeatIndex());
                pm.put("chips", p.getChips());
                pm.put("borrowCount", p.getBorrowCount());
                pm.put("connected", p.isConnected());
                pm.put("owner", p.isOwner());
                return pm;
            }).toList());
            roomPayload.put("smallBlind", room.getConfig().getSmallBlind());
            roomPayload.put("bigBlind", room.getConfig().getBigBlind());
            roomPayload.put("maxSeats", room.getConfig().getMaxSeats());
            roomPayload.put("initialChips", room.getConfig().getInitialChips());
            log.info("[SYNC-CHIPS] {} broadcasting status={} playerCount={}", roomId, room.getStatus().name(), room.getPlayers().size());
            broadcast.sendToRoom(roomId, roomPayload);
        } else {
            log.info("[SYNC-CHIPS] {} bustEndsGame=true, skipping WAITING broadcast", roomId);
        }
    }

    public void broadcastBustChoice(String roomId, String playerId, String nickname) {
        var payload = new HashMap<String, Object>();
        payload.put("type", "bust_choice");
        payload.put("playerId", playerId);
        payload.put("nickname", nickname);
        broadcast.sendToPlayer(playerId, payload);
    }

    public boolean checkGameOver(String roomId, GameEngine.ActionResult result) {
        var room = roomService.findRoom(roomId);
        if (room == null) {
            log.warn("[CHECK-GAME-OVER] {} room not found", roomId);
            return false;
        }

        boolean anyBusted = room.getPlayers().stream()
            .filter(p -> p.getStatus() != com.first.poker.model.enums.PlayerStatus.LEFT)
            .anyMatch(p -> p.getChips() <= 0);
        log.info("[CHECK-GAME-OVER] {} anyBusted={} bustEndsGame={}", roomId, anyBusted, room.getConfig().isBustEndsGame());
        if (!anyBusted) return false;

        if (room.getConfig().isBustEndsGame()) {
            log.info("[CHECK-GAME-OVER] {} bustEndsGame=true, setting FINISHED", roomId);
            room.setStatus(RoomStatus.FINISHED);
            broadcastGameOver(roomId, room, result);
            // Reset to WAITING after broadcast so the room is playable again
            room.setStatus(RoomStatus.WAITING);
            // Broadcast WAITING so frontend knows it's back to lobby
            var roomPayload = new HashMap<String, Object>();
            roomPayload.put("roomId", room.getRoomId());
            roomPayload.put("name", room.getName());
            roomPayload.put("status", room.getStatus().name());
            roomPayload.put("players", room.getPlayers().stream().map(p -> {
                var pm = new HashMap<String, Object>();
                pm.put("playerId", p.getPlayerId());
                pm.put("nickname", p.getNickname());
                pm.put("seatIndex", p.getSeatIndex());
                pm.put("chips", p.getChips());
                pm.put("borrowCount", p.getBorrowCount());
                pm.put("connected", p.isConnected());
                pm.put("owner", p.isOwner());
                return pm;
            }).toList());
            roomPayload.put("smallBlind", room.getConfig().getSmallBlind());
            roomPayload.put("bigBlind", room.getConfig().getBigBlind());
            roomPayload.put("maxSeats", room.getConfig().getMaxSeats());
            roomPayload.put("initialChips", room.getConfig().getInitialChips());
            broadcast.sendToRoom(roomId, roomPayload);
            log.info("[CHECK-GAME-OVER] {} reset to WAITING and broadcast", roomId);
            return true;
        }

        long activePlayers = room.getPlayers().stream()
            .filter(p -> p.getStatus() != com.first.poker.model.enums.PlayerStatus.LEFT)
            .filter(p -> p.getChips() > 0).count();
        log.info("[CHECK-GAME-OVER] {} activePlayers={} totalPlayers={}", roomId, activePlayers, room.getPlayers().size());
        if (activePlayers <= 1) {
            log.info("[CHECK-GAME-OVER] {} <=1 active, broadcasting WAITING (bustEndsGame off)", roomId);
            // Broadcast WAITING so frontend switches to lobby AFTER winners
            var roomPayload = new HashMap<String, Object>();
            roomPayload.put("roomId", room.getRoomId());
            roomPayload.put("name", room.getName());
            roomPayload.put("status", room.getStatus().name());
            roomPayload.put("players", room.getPlayers().stream().map(p -> {
                var pm = new HashMap<String, Object>();
                pm.put("playerId", p.getPlayerId());
                pm.put("nickname", p.getNickname());
                pm.put("seatIndex", p.getSeatIndex());
                pm.put("chips", p.getChips());
                pm.put("borrowCount", p.getBorrowCount());
                pm.put("connected", p.isConnected());
                pm.put("owner", p.isOwner());
                return pm;
            }).toList());
            roomPayload.put("smallBlind", room.getConfig().getSmallBlind());
            roomPayload.put("bigBlind", room.getConfig().getBigBlind());
            roomPayload.put("maxSeats", room.getConfig().getMaxSeats());
            roomPayload.put("initialChips", room.getConfig().getInitialChips());
            broadcast.sendToRoom(roomId, roomPayload);
            return true;
        }

        return false;
    }

    public void broadcastGameOver(String roomId, Room room, GameEngine.ActionResult result) {
        log.info("[BROADCAST-GAME-OVER] {} status={} winnerCount={} bustedCount={}",
            roomId, room.getStatus().name(),
            result.winners().size(),
            room.getPlayers().stream().filter(p -> p.getChips() <= 0).count());
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

        // Also broadcast room status FINISHED so frontend knows game is over
        var roomPayload = new HashMap<String, Object>();
        roomPayload.put("roomId", room.getRoomId());
        roomPayload.put("name", room.getName());
        roomPayload.put("status", room.getStatus().name());
        roomPayload.put("players", room.getPlayers().stream().map(p -> {
            var pm = new HashMap<String, Object>();
            pm.put("playerId", p.getPlayerId());
            pm.put("nickname", p.getNickname());
            pm.put("seatIndex", p.getSeatIndex());
            pm.put("chips", p.getChips());
            pm.put("borrowCount", p.getBorrowCount());
            pm.put("connected", p.isConnected());
            pm.put("owner", p.isOwner());
            return pm;
        }).toList());
        broadcast.sendToRoom(roomId, roomPayload);
    }

    public void handleTimeout(String roomId, String playerId) {
        gameSession.executeWithLock(roomId, () -> {
            try {
                GameEngine.ActionResult result = gameSession.applyAction(roomId, playerId, GameAction.FOLD, 0);

                if (result.handComplete()) {
                    timeoutScheduler.cancelTimeout(roomId);
                    com.first.poker.engine.GameState finalState = result.state();
                    gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
                    var room = roomService.findRoom(roomId);
                    if (room != null) checkAndApplyBonuses(roomId, room, finalState, result);
                    if (!result.winners().isEmpty()) {
                        broadcastWinners(roomId, result);
                    }
                    checkGameOver(roomId, result);
                    return;
                }

                broadcastGameState(roomId, result.state());

                autoPlayBots(roomId);

                // Schedule timeout for the next human player
                var state = gameSession.getState(roomId);
                if (state != null) scheduleNextTimeout(roomId, state);
            } catch (Exception e) {
                log.error("[TIMEOUT-ERROR] room={} player={}: {}", roomId, playerId, e.getMessage(), e);
                // Try to keep game moving even if timeout fold fails
                autoPlayBots(roomId);
                var retryState = gameSession.getState(roomId);
                if (retryState != null) scheduleNextTimeout(roomId, retryState);
            }
        });
    }

    public void scheduleNextTimeout(String roomId, GameState state) {
        var cp = state.currentPlayer();
        if (cp != null && !cp.playerId().startsWith("bot-") && !cp.folded() && !cp.allIn()) {
            timeoutScheduler.scheduleTimeout(roomId, cp.playerId(), 30);
        }
    }

    public void cancelTimeout(String roomId) {
        timeoutScheduler.cancelTimeout(roomId);
    }
    public void autoPlayBots(String roomId) {
        int safety = 0;
        while (safety++ < 30) {
            var state = gameSession.getState(roomId);
            if (state == null || state.currentPlayerIndex() < 0) {
                log.info("[AUTOBOT] {} stop: state={} curIdx={}", roomId, state, state != null ? state.currentPlayerIndex() : -1);
                break;
            }
            var cp = state.currentPlayer();
            if (cp.folded() || cp.allIn()) {
                log.info("[AUTOBOT] {} stop: {} folded={} allIn={}", roomId, cp.playerId(), cp.folded(), cp.allIn());
                break;
            }

            boolean isBot = cp.playerId().startsWith("bot-");
            // NOTE: isZeroChipHuman can only trigger for a player who went all-in
            // mid-hand and lost (chip count dropped to 0 within the game engine).
            // Players excluded from the hand by startGame (0 chips at game start)
            // are NOT in state.players() and will never reach this branch.
            boolean isZeroChipHuman = !isBot && cp.chips() <= 0;

            if (!isBot && !isZeroChipHuman) {
                log.info("[AUTOBOT] {} stop: {} human chips={}", roomId, cp.playerId(), cp.chips());
                break; // Human with chips — stop
            }

            GameAction autoAction;
            int amount = 0;
            if (isBot) {
                int toCall = state.currentBet() - cp.roundBet();
                autoAction = toCall <= 0 ? GameAction.CHECK : GameAction.CALL;
                log.info("[AUTOBOT] {} bot={} action={} toCall={} curBet={} roundBet={} chips={}",
                    roomId, cp.playerId(), autoAction, toCall, state.currentBet(), cp.roundBet(), cp.chips());
            } else {
                autoAction = GameAction.FOLD; // 0-chip human always folds
                log.info("[AUTOBOT] {} zeroChipHuman={} FOLD", roomId, cp.playerId());
            }

            try {
                var result = gameSession.applyAction(roomId, cp.playerId(), autoAction, amount);
                log.info("[AUTOBOT] {} {} OK handComplete={} next={}",
                    roomId, cp.playerId(), result.handComplete(), result.state().currentPlayer().playerId());

                if (result.handComplete()) {
                    timeoutScheduler.cancelTimeout(roomId);
                    com.first.poker.engine.GameState finalState = result.state();
                    gameSession.endGame(roomId, () -> syncRoomChips(roomId, finalState));
                    var room = roomService.findRoom(roomId);
                    if (room != null) checkAndApplyBonuses(roomId, room, finalState, result);
                    if (!result.winners().isEmpty()) {
                        broadcastWinners(roomId, result);
                    }
                    if (checkGameOver(roomId, result)) return;
                    return;
                }

                broadcastGameState(roomId, result.state());
            } catch (IllegalArgumentException e) {
                log.warn("[BOT-ACTION] {} skip: {}", cp.playerId(), e.getMessage());
                continue;
            } catch (Throwable e) {
                log.error("[BOT-ACTION] {} unexpected error", cp.playerId(), e);
                continue;
            }
        }
        // Bots finished — schedule timeout for the next human player
        var state = gameSession.getState(roomId);
        if (state != null) scheduleNextTimeout(roomId, state);
    }
}
