<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWebSocket, setWsLogger } from '../composables/useWebSocket'
import { useRoomStore } from '../stores/room'
import { useUserStore } from '../stores/user'
import { useLogger } from '../composables/useLogger'

const logger = useLogger()
setWsLogger({ logWsSend: logger.logWsSend, logWsRecv: logger.logWsRecv, logError: logger.logError })

import PokerTable from '../components/poker/PokerTable.vue'
import ActionPanel from '../components/poker/ActionPanel.vue'
import HandResult from '../components/poker/HandResult.vue'
import GameOver from '../components/poker/GameOver.vue'
import BustChoice from '../components/poker/BustChoice.vue'

const route = useRoute()
const router = useRouter()
const roomId = route.params.roomId as string
const userStore = useUserStore()
const { connect, disconnect, subscribe, send, connected } = useWebSocket(userStore.playerId)
const roomStore = useRoomStore()

// Watch status transitions for logging
let prevStatus = ''
watch(() => roomStore.status, (newStatus) => {
  if (newStatus !== prevStatus) {
    logger.logState('status_change', { from: prevStatus, to: newStatus, playerCount: roomStore.players.length })
    prevStatus = newStatus
  }
})

const joinError = ref('')
const joined = ref(false)
const joining = ref(false)
const addingBot = ref(false)
const starting = ref(false)
const startError = ref('')
const localCountdown = ref(0)
const showBustChoice = ref(false)
const showLeaderboard = ref(false)
let countdownTimer: ReturnType<typeof setInterval> | null = null

// Init on mount: if came from home page (already joined), auto-connect
onMounted(async () => {
  logger.logLifecycle('mounted')
  joining.value = true

  // Allow re-entry by room URL even without previous Pinia state
  if (!userStore.nickname) {
    userStore.currentRoomId = localStorage.getItem('poker_room_id') || ''
    const savedNick = localStorage.getItem('poker_nickname')
    if (savedNick) userStore.nickname = savedNick
    if (!userStore.nickname) {
      joinError.value = '未找到玩家信息，请从首页重新进入'
      joining.value = false
      return
    }
  }

  try { await connect() } catch (e) {
    logger.logError('ws_connect_failed', e)
    joinError.value = 'WebSocket 连接失败'; joining.value = false; return
  }

  subscribe(`/topic/room/${roomId}`, (msg) => {
    const data = JSON.parse(msg.body)

    if (data.type === 'room_dissolved') {
      logger.logState('room_dissolved', { roomId, initiator: data.playerId })
      alert('房间已解散')
      disconnect()
      roomStore.reset()
      router.push('/')
      return
    }

    if (data.type === 'player_left') {
      logger.logState('player_left', { playerId: data.playerId, newOwnerId: data.newOwnerId })
      roomStore.players = roomStore.players.filter(p => p.playerId !== data.playerId)
      if (data.newOwnerId && data.newOwnerId === userStore.playerId) {
        alert('你已成为新房主')
        refreshRoom()
      }
      return
    }

    if (data.type === 'player_joined') {
      logger.logState('player_joined', { playerId: data.playerId })
      refreshRoom()
      return
    }

    if (data.type === 'player_disconnected') {
      logger.logState('player_disconnected', { playerId: data.playerId })
      const p = roomStore.players.find(p => p.playerId === data.playerId)
      if (p) p.connected = false
      return
    }

    if (data.type === 'system') {
      roomStore.addSystemMessage(data.text)
    } else if (data.roomId) {
      roomStore.roomId = data.roomId
      roomStore.roomName = data.name || ''
      roomStore.status = data.status || 'WAITING'
      roomStore.players = (data.players || []).map((p: PlayerView) => ({
        playerId: p.playerId, nickname: p.nickname, seatIndex: p.seatIndex,
        chips: p.chips, betInRound: 0, folded: false, allIn: false,
        holeCards: null, lastAction: null, connected: p.connected,
        borrowCount: p.borrowCount || 0,
        owner: p.owner || false,
      }))
      roomStore.smallBlind = data.smallBlind || 10
      roomStore.bigBlind = data.bigBlind || 20
      roomStore.maxSeats = data.maxSeats || 8
      if (data.initialChips) roomStore.initialChips = data.initialChips
    }
  })

  subscribe(`/topic/room/${roomId}/game`, (msg) => {
    const data = JSON.parse(msg.body)
    // Game over: has both winners and leaderboard
    if (data.leaderboard) {
      logger.logState('game_over', { leaderboard: data.leaderboard.length, busted: data.bustedPlayerIds?.length })
      showBustChoice.value = false
      roomStore.setGameOver(data)
      return
    }
    // Server-side error broadcast
    if (data.error) {
      logger.logError('server_error_broadcast', data.error)
      console.error('[Game Error]', data.error)
      alert(data.error)
      stopCountdown()
      return
    }
    // Winners broadcast: only update winners
    if (data.winners) {
      logger.logState('hand_complete_winners', { count: data.winners.length })
      stopCountdown()
      roomStore.winners = data.winners
    } else {
      // New game state snapshot — clear previous game-over / leaderboard state
      roomStore.winners = null
      roomStore.gameOver = false
      roomStore.leaderboard = []
      roomStore.bustedPlayerIds = []
      roomStore.updateFromSnapshot(data, userStore.playerId)
    }
    // Start/stop countdown on state changes (purely visual)
    if (roomStore.status === 'PLAYING' && isMyTurn.value) startCountdown()
    else stopCountdown()
  })

  // Player-specific subscription: receives my hole cards
  subscribe(`/topic/player/${userStore.playerId}/game`, (msg) => {
    const data = JSON.parse(msg.body)
    if (data.type === 'bust_choice') {
      showBustChoice.value = true
      return
    }
    if (data.error) {
      console.error('[Game Error]', data.error)
      alert(data.error)
      stopCountdown()
      return
    }
    if (data.winners) return // winners handled by public channel
    roomStore.updateFromSnapshot(data, userStore.playerId)
    if (roomStore.status === 'PLAYING' && isMyTurn.value) startCountdown()
    else stopCountdown()
  })

  // Fetch room state (GET, idempotent)
  await refreshRoom()
  if (roomStore.roomId) {
    logger.logState('room_joined', { roomId, playerCount: roomStore.players.length })
    joined.value = true
  } else {
    logger.logError('room_not_found', { roomId })
    joinError.value = '该房间不存在或已过期（服务器重启后房间会被清空），请返回首页重新创建'
  }
  joining.value = false
})

// Reconnect detection: when WebSocket reconnects, re-join the room
// to trigger onReconnect on the server (restores ACTIVE status, cancels grace timer)
let wasConnected = false
watch(connected, async (now) => {
  if (now && !wasConnected && joined.value) {
    logger.logLifecycle('ws_reconnect');
    console.log('[RoomView] WebSocket reconnected, re-joining room')
    try {
      await fetch(`/api/rooms/${roomId}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId: userStore.playerId, nickname: userStore.nickname }),
      })
      await refreshRoom()
      console.log('[RoomView] Room re-joined after reconnect')
    } catch (e) {
      logger.logError('reconnect_rejoin_failed', e)
      console.error('[RoomView] Reconnect re-join failed:', e)
    }
  }
  wasConnected = now
})

interface PlayerView {
  playerId: string
  nickname: string
  seatIndex: number
  chips: number
  connected: boolean
  borrowCount?: number
}

interface SnapshotPayload {
  roomId: string
  name: string
  status: string
  players: PlayerView[]
  smallBlind: number
  bigBlind: number
  dealerIndex: number
}

// Derived state
const isMyTurn = computed(() => {
  if (roomStore.status !== 'PLAYING') return false
  // Use currentPlayerId (directly sent by backend) rather than indexing
  // into the players array. The backend's currentPlayerIndex refers to
  // the game-engine-internal player list, which may be ordered differently
  // from the room-based players array sent in the snapshot.
  if (!roomStore.currentPlayerId) return false
  return roomStore.currentPlayerId === userStore.playerId
})

const myPlayer = computed(() => {
  return roomStore.players.find(p => p.playerId === userStore.playerId) || null
})

const isOwner = computed(() => {
  return roomStore.players.some(p => p.owner && p.playerId === userStore.playerId)
})

/** True when I'm in the room but excluded from the current hand (0 chips at game start). */
const isSpectating = computed(() => {
  if (roomStore.status !== 'PLAYING') return false
  const me = myPlayer.value
  return !!(me && (me as any).inGame === false)
})

/** True when I'm all-in during a hand — chips committed, waiting for result. */
const isAllIn = computed(() => {
  if (roomStore.status !== 'PLAYING') return false
  const me = myPlayer.value
  return !!(me?.allIn)
})

const canStart = computed(() => {
  if (roomStore.status !== 'WAITING') return false
  if (roomStore.players.length < 2) return false
  // Owner must have chips to participate
  const owner = roomStore.players.find(p => p.owner)
  if (!owner || owner.chips <= 0) return false
  // At least 2 players must have chips to start
  const withChips = roomStore.players.filter(p => p.chips > 0).length
  return withChips >= 2
})

const startBlockReason = computed(() => {
  if (roomStore.status !== 'WAITING') return ''
  if (roomStore.players.length < 2) return '至少需要2人才能开始'
  const owner = roomStore.players.find(p => p.owner)
  if (owner && owner.chips <= 0) return '房主没有筹码，请先借筹码'
  const withChips = roomStore.players.filter(p => p.chips > 0).length
  if (withChips < 2) return '有玩家筹码归零，请等待借筹码或添加新机器人'
  return ''
})

// Table player data
const tablePlayers = computed(() => {
  return roomStore.players.map(p => ({
    playerId: p.playerId,
    nickname: p.nickname,
    chips: p.chips,
    betInRound: p.betInRound,
    folded: p.folded,
    allIn: p.allIn,
    seatIndex: p.seatIndex,
    holeCards: roomStore.status === 'FINISHED'
      ? (p.holeCards || null)
      : (p.playerId === userStore.playerId ? roomStore.myHoleCards : null),
    isDealer: p.seatIndex === roomStore.dealerIndex,
  }))
})

// Action legality
const legalActions = computed(() => {
  if (!isMyTurn.value || !myPlayer.value) {
    return { canCheck: false, canCall: false, canBet: false, canRaise: false, callAmount: 0 }
  }
  const myBet = myPlayer.value.betInRound
  const toCall = roomStore.currentBet - myBet
  return {
    canCheck: toCall <= 0,
    canCall: toCall > 0,
    canBet: roomStore.currentBet === 0 && myPlayer.value.chips >= Math.min(roomStore.minRaise, myPlayer.value.chips),
    canRaise: roomStore.currentBet > 0 && myPlayer.value.chips > toCall && myPlayer.value.chips > 0,
    callAmount: Math.min(toCall, myPlayer.value.chips),
  }
})

// Client-side countdown — purely visual, real timeout handled server-side by GameTimeoutScheduler
function startCountdown() {
  stopCountdown()
  localCountdown.value = 30
  countdownTimer = setInterval(() => {
    localCountdown.value--
    if (localCountdown.value <= 0) {
      stopCountdown()
    }
  }, 1000)
}
function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}

function handleAction(payload: { type: string; amount?: number }) {
  if (!connected.value) { alert('网络连接已断开，正在重连...'); return }
  logger.logAction('action', { type: payload.type, amount: payload.amount, roomId, chips: myPlayer.value?.chips })
  send(`/app/game/${roomId}/action`, {
    playerId: userStore.playerId,
    action: payload.type,
    amount: payload.amount || 0,
  })
}

function handleNextHand() {
  if (!connected.value) { alert('网络连接已断开，正在重连...'); return }
  logger.logAction('next_hand', { roomId })
  console.log('[RoomView] handleNextHand: sending start to', roomId, 'playerId:', userStore.playerId)
  send(`/app/game/${roomId}/start`, { playerId: userStore.playerId })
}

function handleStartGame() {
  if (!connected.value) { alert('网络连接已断开，正在重连...'); return }
  logger.logAction('start_game', { roomId, playerCount: roomStore.players.length })
  console.log('[RoomView] handleStartGame: sending start to', roomId, 'playerId:', userStore.playerId)
  send(`/app/game/${roomId}/start`, { playerId: userStore.playerId })
}

async function refreshRoom() {
  try {
    const res = await fetch(`/api/rooms/${roomId}`)
    if (res.ok) {
      const data: SnapshotPayload = await res.json()
      roomStore.roomId = data.roomId; roomStore.roomName = data.name
      roomStore.status = data.status as any
      roomStore.players = (data.players || []).map((p: PlayerView) => ({
        playerId: p.playerId, nickname: p.nickname, seatIndex: p.seatIndex,
        chips: p.chips, betInRound: 0, folded: false, allIn: false,
        holeCards: null, lastAction: null, connected: p.connected,
        borrowCount: p.borrowCount || 0,
        owner: p.owner || false,
      }))
      roomStore.smallBlind = data.smallBlind
      roomStore.bigBlind = data.bigBlind
      roomStore.maxSeats = (data as any).maxSeats || 8
      roomStore.initialChips = (data as any).initialChips || 1000
    } else {
      roomStore.roomId = ''
    }
  } catch (e) {
    logger.logError('refresh_room_failed', e)
    console.error('Failed to refresh room', e)
    roomStore.roomId = ''
  }
}

async function handleAddBot() {
  if (addingBot.value) return
  addingBot.value = true
  logger.logAction('add_bot', { roomId, currentCount: roomStore.players.length })
  try {
    const res = await fetch(`/api/rooms/${roomId}/bots?count=1`, { method: 'POST' })
    if (!res.ok) {
      alert('添加机器人失败')
    } else {
      // Refresh UI to show the new bot
      await refreshRoom()
    }
  } catch (e) {
    logger.logError('add_bot_failed', e)
    alert('添加机器人失败')
  } finally {
    addingBot.value = false
  }
}

function handleLeave() {
  logger.logAction('leave_room', { roomId, status: roomStore.status })
  send(`/app/room/${roomId}/leave`, { playerId: userStore.playerId })
  disconnect()
  roomStore.reset()
  router.push('/')
}

function handleBackToRoom() {
  logger.logAction('back_to_lobby', { roomId })
  roomStore.gameOver = false
  roomStore.winners = null
  roomStore.leaderboard = []
  roomStore.bustedPlayerIds = []
  roomStore.status = 'WAITING'
  // Refresh room state to get latest player chips etc.
  refreshRoom()
}

const borrowing = ref(false)
async function handleBorrow() {
  if (borrowing.value) return
  borrowing.value = true
  logger.logAction('borrow_chips', { roomId })
  try {
    const res = await fetch(`/api/rooms/${roomId}/borrow`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: userStore.playerId }),
    })
    if (res.ok) {
      await refreshRoom()
    } else {
      alert('借筹码失败')
    }
  } catch (e) {
    logger.logError('borrow_failed', e)
    alert('借筹码失败')
  } finally {
    borrowing.value = false
  }
}

async function handleBustSpectate() {
  logger.logAction('bust_spectate', { roomId })
  showBustChoice.value = false
  // Player chose to spectate — game continues, auto-fold on next turn
}

// Leaderboard computed
const leaderboard = computed(() => {
  return [...roomStore.players]
    .sort((a, b) => {
      const aNet = a.chips - ((a as any).borrowCount || 0) * roomStore.initialChips
      const bNet = b.chips - ((b as any).borrowCount || 0) * roomStore.initialChips
      return bNet - aNet
    })
    .map((p, i) => {
      const netChips = p.chips - ((p as any).borrowCount || 0) * roomStore.initialChips
      return {
        playerId: p.playerId,
        nickname: p.nickname,
        chips: p.chips,
        netChips,
        rank: i + 1,
      }
    })
})

onUnmounted(() => {
  logger.logLifecycle('unmounted')
  logger.flush()
  stopCountdown()
  disconnect()
  roomStore.reset()
})
</script>

<template>
  <div class="min-h-screen flex flex-col" style="background-color: var(--color-surface)">
    <!-- Header -->
    <div class="p-4 flex items-center justify-between" style="background-color: var(--color-surface-light)">
      <button @click="handleLeave" class="text-sm" style="color: var(--color-text-muted)">
        ← 退出
      </button>
      <button @click="logger.download()" class="text-xs px-2 py-1 rounded" style="color: var(--color-text-muted); border: 1px solid var(--color-border);" title="下载操作日志 JSON">
        📋 日志
      </button>
      <div class="text-center">
        <div class="font-bold" style="color: var(--color-gold)">{{ roomStore.roomName || '房间' }}</div>
        <div class="text-xs" style="color: var(--color-text-muted)">
          房间号: <span class="tracking-widest font-mono">{{ roomId }}</span>
        </div>
      </div>
      <button
        class="text-sm font-bold px-2 py-1 rounded"
        style="color: var(--color-gold); background-color: var(--color-surface)"
        @click="showLeaderboard = !showLeaderboard; logger.logAction('toggle_leaderboard', { visible: showLeaderboard })"
      >
        🏆 排行
      </button>
    </div>

    <!-- Connecting / Error state -->
    <div v-if="!joined" class="flex-1 p-6 flex flex-col items-center justify-center space-y-4">
      <div v-if="joining" class="text-center">
        <div class="text-lg" style="color: var(--color-text-muted)">正在连接...</div>
      </div>
      <div v-else-if="joinError" class="text-center space-y-3">
        <div style="color: var(--color-accent)">{{ joinError }}</div>
        <button @click="handleLeave" class="px-6 py-2 rounded-lg" style="background-color: var(--color-primary); color: white">
          返回首页
        </button>
      </div>
    </div>

    <!-- Game Content (shown after join) -->
    <div v-if="joined" class="flex-1 flex flex-col">
      <!-- PLAYING state: Poker Table -->
      <div v-if="roomStore.status === 'PLAYING' || roomStore.status === 'FINISHED'" class="flex-1 flex flex-col relative">
        <div class="flex-1 p-2">
          <PokerTable
            :players="tablePlayers"
            :community-cards="roomStore.communityCards"
            :pot="roomStore.pot"
            :dealer-index="roomStore.dealerIndex"
            :current-player-index="roomStore.currentPlayerIndex"
            :my-player-id="userStore.playerId"
            :showdown="roomStore.status === 'FINISHED'"
          />
        </div>

        <!-- Spectating: player excluded from this hand (0 chips at game start) -->
        <div v-if="isSpectating" class="text-center py-3 px-4 space-y-2">
          <div class="text-sm font-bold" style="color: var(--color-text-muted)">👀 观战中 — 你未参与此局</div>
          <button
            class="w-full py-2 rounded-lg font-bold text-sm transition active:scale-95"
            style="background-color: var(--color-accent); color: white"
            @click="handleBorrow"
            :disabled="borrowing"
          >
            {{ borrowing ? '处理中...' : '💸 借筹码 (借 1000)' }}
          </button>
          <div class="text-xs" style="color: var(--color-text-muted)">下一局生效</div>
        </div>
        <!-- All-in: my chips are committed to this hand, waiting for result -->
        <div v-else-if="isAllIn" class="text-center py-3 px-4 space-y-1">
          <div class="text-sm font-bold" style="color: var(--color-gold)">🔥 全押! 等待结果...</div>
          <div class="text-xs" style="color: var(--color-text-muted)">你的筹码已全部投入此局</div>
        </div>
        <ActionPanel
          v-else-if="!roomStore.gameOver"
          :is-my-turn="isMyTurn"
          :can-check="legalActions.canCheck"
          :can-call="legalActions.canCall"
          :can-bet="legalActions.canBet"
          :can-raise="legalActions.canRaise"
          :call-amount="legalActions.callAmount"
          :min-raise="roomStore.bigBlind"
          :current-bet="roomStore.currentBet"
          :time-left-sec="localCountdown || roomStore.timeLeftSec"
          :my-chips="myPlayer?.chips || 0"
          @action="handleAction"
        />

        <!-- Hand Result Overlay -->
        <HandResult
          v-if="roomStore.winners && !roomStore.gameOver"
          :winners="roomStore.winners"
          :is-owner="isOwner"
          @next-hand="handleNextHand"
        />

        <!-- Bust Choice Popup (personal, hidden when game over) -->
        <BustChoice
          v-if="showBustChoice && !roomStore.gameOver"
          :player-id="userStore.playerId"
          :nickname="userStore.nickname"
          @spectate="handleBustSpectate"
        />
      </div>

      <!-- WAITING state -->
      <div v-else class="flex-1 p-4 space-y-4">
        <div class="text-center py-2 rounded-lg" style="background-color: var(--color-surface-light)">
          <span class="text-sm" style="color: var(--color-text-muted)">
            状态: 等待玩家加入...
          </span>
          <span class="ml-2 text-sm" style="color: var(--color-text-muted)">
            {{ roomStore.players?.length || 0 }}/{{ roomStore.maxSeats }}人
          </span>
          <span class="ml-2 text-sm" style="color: var(--color-text-muted)">
            盲注 {{ roomStore.smallBlind }}/{{ roomStore.bigBlind }}
          </span>
        </div>

        <!-- Players List -->
        <div class="space-y-2">
          <div class="text-sm font-bold" style="color: var(--color-text-muted)">玩家</div>
          <div
            v-for="player in roomStore.players"
            :key="player.playerId"
            class="flex items-center justify-between p-3 rounded-lg"
            :style="{
              backgroundColor: player.playerId === userStore.playerId ? 'var(--color-primary)' : 'var(--color-surface-light)',
              opacity: player.connected ? 1 : 0.5
            }"
          >
            <div class="flex items-center gap-2">
              <span class="text-lg">{{ player.seatIndex === 0 ? '👑' : '💺' }}</span>
              <span class="font-bold text-white">{{ player.nickname }}</span>
              <span v-if="player.playerId === userStore.playerId" class="text-xs" style="color: var(--color-gold)">(你)</span>
            </div>
            <span class="text-sm" style="color: var(--color-gold)">
              💰 {{ player.chips }}
              <span v-if="(player.borrowCount || 0) > 0" class="text-xs" style="color: var(--color-accent)">
                (净: {{ player.chips - (player.borrowCount || 0) * roomStore.initialChips }})
              </span>
            </span>
          </div>
        </div>

        <!-- Share hint -->
        <div class="text-center text-xs mt-4" style="color: var(--color-text-muted)">
          分享房间号 <span class="font-mono tracking-widest" style="color: var(--color-gold)">{{ roomId }}</span> 给朋友即可加入
        </div>

        <!-- Add Bot Button -->
        <button
          v-if="(roomStore.players?.length || 0) < roomStore.maxSeats"
          class="w-full py-3 rounded-lg font-bold text-white text-base transition active:scale-95 mt-4"
          style="background-color: var(--color-surface-light); border: 1px solid var(--color-primary)"
          @click="handleAddBot"
          :disabled="addingBot"
        >
          {{ addingBot ? '添加中...' : '+ 添加一个机器人' }}
        </button>

        <!-- Borrow Chips Button (for busted players) -->
        <button
          v-if="(myPlayer?.chips ?? 0) <= 0"
          class="w-full py-3 rounded-lg font-bold text-base transition active:scale-95 mt-4"
          style="background-color: var(--color-accent); color: white"
          @click="handleBorrow"
          :disabled="borrowing"
        >
          {{ borrowing ? '处理中...' : '💸 借筹码 (借 1000)' }}
        </button>

        <!-- Start Button -->
        <button
          v-if="isOwner && canStart"
          class="w-full py-4 rounded-lg font-bold text-white text-lg transition active:scale-95 mt-4"
          style="background-color: var(--color-primary)"
          @click="handleStartGame"
        >
          开始游戏
        </button>
        <div v-else-if="isOwner && !canStart" class="text-center text-sm mt-4" style="color: var(--color-text-muted)">
          {{ startBlockReason }}
        </div>
      </div>

      <!-- Game Over Overlay (outside PLAYING/WAITING areas so it stays visible regardless of status) -->
      <GameOver
        v-if="roomStore.gameOver"
        :winners="roomStore.winners || []"
        :leaderboard="roomStore.leaderboard"
        :busted-player-ids="roomStore.bustedPlayerIds"
        @back-to-lobby="handleBackToRoom"
      />
    </div>

    <!-- Leaderboard Modal -->
    <div v-if="showLeaderboard" class="fixed inset-0 z-30 flex items-center justify-center bg-black/60 px-4" @click.self="showLeaderboard = false">
      <div
        class="w-full max-w-xs rounded-xl p-5 space-y-3"
        style="background-color: var(--color-surface-light)"
      >
        <div class="flex items-center justify-between">
          <div class="text-lg font-bold" style="color: var(--color-gold)">🏆 排行榜</div>
          <button @click="showLeaderboard = false" class="text-sm" style="color: var(--color-text-muted)">✕</button>
        </div>
        <div class="space-y-1">
          <div
            v-for="entry in leaderboard"
            :key="entry.playerId"
            class="flex items-center gap-3 p-2 rounded-lg"
            :style="{
              backgroundColor: entry.rank === 1 ? 'rgba(255,215,0,0.12)' : 'var(--color-surface)',
              border: entry.rank === 1 ? '1px solid var(--color-gold)' : '1px solid transparent'
            }"
          >
            <span class="w-7 text-center font-bold text-sm" :style="{ color: entry.rank <= 3 ? 'var(--color-gold)' : 'var(--color-text-muted)' }">
              {{ entry.rank === 1 ? '🥇' : entry.rank === 2 ? '🥈' : entry.rank === 3 ? '🥉' : `#${entry.rank}` }}
            </span>
            <span class="flex-1 text-sm font-bold text-white">
              {{ entry.nickname }}
              <span v-if="entry.playerId === userStore.playerId" class="text-xs" style="color: var(--color-gold)">(你)</span>
            </span>
            <span class="text-sm font-mono font-bold" :style="{ color: (entry.netChips ?? 0) >= 0 ? 'var(--color-gold)' : 'var(--color-accent)' }">
              {{ entry.netChips != null ? (entry.netChips >= 0 ? '+' + entry.netChips : String(entry.netChips)) : entry.chips }}
            </span>
          </div>
          <div v-if="leaderboard.length === 0" class="text-center text-sm py-4" style="color: var(--color-text-muted)">
            暂无玩家
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
