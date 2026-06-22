<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWebSocket, setWsLogger } from '../composables/useWebSocket'
import { useRoomStore } from '../stores/room'
import { useUserStore } from '../stores/user'
import { useLogger } from '../composables/useLogger'
import { API_BASE_URL } from '../config'

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

let prevStatus = ''
watch(() => roomStore.status, (newStatus) => {
  if (newStatus !== prevStatus) {
    logger.logState('status_change', { from: prevStatus, to: newStatus, playerCount: roomStore.players.length })
    prevStatus = newStatus
  }
})

onMounted(() => {
  const bgLayer = document.querySelector('.bg-layer') as HTMLElement
  if (bgLayer) {
    bgLayer.style.backgroundImage = "url('/image_968223578838775.png')"
  }
})

const joinError = ref('')
const joined = ref(false)
const joining = ref(false)
const addingBot = ref(false)
const localCountdown = ref(0)
const showBustChoice = ref(false)
const showLeaderboard = ref(false)
let countdownTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  logger.logLifecycle('mounted')
  joining.value = true

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
    if (data.leaderboard) {
      logger.logState('game_over', { leaderboard: data.leaderboard.length, busted: data.bustedPlayerIds?.length })
      showBustChoice.value = false
      roomStore.setGameOver(data)
      return
    }
    if (data.error) {
      logger.logError('server_error_broadcast', data.error)
      console.error('[Game Error]', data.error)
      alert(data.error)
      stopCountdown()
      return
    }
    if (data.winners) {
      logger.logState('hand_complete_winners', { count: data.winners.length })
      stopCountdown()
      roomStore.winners = data.winners
    } else {
      roomStore.winners = null
      roomStore.gameOver = false
      roomStore.leaderboard = []
      roomStore.bustedPlayerIds = []
      roomStore.updateFromSnapshot(data, userStore.playerId)
    }
    if (roomStore.status === 'PLAYING' && isMyTurn.value) startCountdown()
    else stopCountdown()
  })

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
    if (data.winners) return
    roomStore.updateFromSnapshot(data, userStore.playerId)
    if (roomStore.status === 'PLAYING' && isMyTurn.value) startCountdown()
    else stopCountdown()
  })

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

// Watch for status change to switch background to game table
watch(() => roomStore.status, (newStatus) => {
  const bgLayer = document.querySelector('.bg-layer') as HTMLElement
  if (!bgLayer) return
  if (newStatus === 'PLAYING' || newStatus === 'FINISHED') {
    bgLayer.style.backgroundImage = "url('/image_166619076022278.png')"
  } else {
    bgLayer.style.backgroundImage = "url('/image_968223578838775.png')"
  }
})

let wasConnected = false
watch(connected, async (now) => {
  if (now && !wasConnected && joined.value) {
    logger.logLifecycle('ws_reconnect');
    console.log('[RoomView] WebSocket reconnected, re-joining room')
    try {
      await fetch(`${API_BASE_URL}/api/rooms/${roomId}/join`, {
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
  owner?: boolean
}

interface SnapshotPayload {
  roomId: string
  name: string
  status: string
  players: PlayerView[]
  smallBlind: number
  bigBlind: number
  dealerPlayerId?: string | null
}

const isMyTurn = computed(() => {
  if (roomStore.status !== 'PLAYING') return false
  if (!roomStore.currentPlayerId) return false
  return roomStore.currentPlayerId === userStore.playerId
})

const myPlayer = computed(() => {
  return roomStore.players.find(p => p.playerId === userStore.playerId) || null
})

const isOwner = computed(() => {
  return roomStore.players.some(p => p.owner && p.playerId === userStore.playerId)
})

const isSpectating = computed(() => {
  if (roomStore.status !== 'PLAYING') return false
  const me = myPlayer.value
  return !!(me && (me as any).inGame === false)
})

const isAllIn = computed(() => {
  if (roomStore.status !== 'PLAYING') return false
  const me = myPlayer.value
  return !!(me?.allIn)
})

const canStart = computed(() => {
  if (roomStore.status !== 'WAITING') return false
  if (roomStore.players.length < 2) return false
  const owner = roomStore.players.find(p => p.owner)
  if (!owner || owner.chips <= 0) return false
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
    isDealer: p.playerId === (roomStore.dealerPlayerId ?? ''),
  }))
})

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
    const res = await fetch(`${API_BASE_URL}/api/rooms/${roomId}`)
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
    const res = await fetch(`${API_BASE_URL}/api/rooms/${roomId}/bots?count=1`, { method: 'POST' })
    if (!res.ok) {
      alert('添加机器人失败')
    } else {
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
  refreshRoom()
}

const borrowing = ref(false)
async function handleBorrow() {
  if (borrowing.value) return
  borrowing.value = true
  logger.logAction('borrow_chips', { roomId })
  try {
    const res = await fetch(`${API_BASE_URL}/api/rooms/${roomId}/borrow`, {
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
}

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
  <div class="room-screen">
    <!-- Connecting / Error state -->
    <div v-if="!joined" class="room-join-state">
      <div v-if="joining" class="text-lg" style="color: var(--color-text-muted)">正在连接...</div>
      <div v-else-if="joinError" class="text-center space-y-3">
        <div style="color: var(--color-accent); font-family: 'Press Start 2P', monospace; font-size: clamp(9px,2.5vh,13px);">{{ joinError }}</div>
        <button @click="handleLeave" class="btn-back">
          返回首页
        </button>
      </div>
    </div>

    <div v-if="joined" class="room-content">
      <!-- PLAYING / FINISHED: Full-screen table overlay -->
      <div v-if="roomStore.status === 'PLAYING' || roomStore.status === 'FINISHED'" class="room-game-view">
        <!-- Top info bar -->
        <div class="game-top-bar">
          <div class="game-room-info">
            <span class="game-room-name">{{ roomStore.roomName || '房间' }}</span>
            <span class="game-blinds">盲注 {{ roomStore.smallBlind }}/{{ roomStore.bigBlind }}</span>
          </div>
          <div class="game-meta">
            <span class="game-phase">{{ roomStore.status === 'FINISHED' ? '摊牌' : '进行中' }}</span>
            <span v-if="localCountdown > 0" class="game-countdown" :class="{ 'urgent': localCountdown <= 5 }">
              {{ localCountdown }}s
            </span>
          </div>
        </div>

        <!-- Table area -->
        <div class="game-table-area">
          <PokerTable
            :players="tablePlayers"
            :community-cards="roomStore.communityCards"
            :pot="roomStore.pot"
            :dealer-player-id="roomStore.dealerPlayerId"
            :current-player-index="roomStore.currentPlayerIndex"
            :my-player-id="userStore.playerId"
            :showdown="roomStore.status === 'FINISHED'"
          />
        </div>

        <!-- Action area -->
        <div class="game-action-area">
          <div v-if="isSpectating" class="text-center py-2 space-y-2">
            <div style="color: var(--color-text-muted); font-family: 'Press Start 2P', monospace; font-size: clamp(8px,2vh,11px);">👀 观战中 — 你未参与此局</div>
            <button class="btn-borrow" @click="handleBorrow" :disabled="borrowing">
              {{ borrowing ? '处理中...' : '💸 借筹码 (借 1000)' }}
            </button>
          </div>
          <div v-else-if="isAllIn" class="text-center py-2">
            <div style="color: var(--color-gold); font-family: 'Press Start 2P', monospace; font-size: clamp(9px,2.3vh,12px);">🔥 全押! 等待结果...</div>
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
        </div>

        <HandResult
          v-if="roomStore.winners && !roomStore.gameOver"
          :winners="roomStore.winners"
          :is-owner="isOwner"
          @next-hand="handleNextHand"
        />

        <BustChoice
          v-if="showBustChoice && !roomStore.gameOver"
          :player-id="userStore.playerId"
          :nickname="userStore.nickname"
          @spectate="handleBustSpectate"
        />
      </div>

      <!-- WAITING: Left-column lobby -->
      <div v-else class="room-waiting-view">
        <div class="room-overlay"></div>
        <div class="room-left-col">
          <!-- Header -->
          <div class="room-header">
            <button @click="handleLeave" class="room-link-btn">← 退出</button>
            <button
              class="room-link-btn"
              @click="showLeaderboard = !showLeaderboard; logger.logAction('toggle_leaderboard', { visible: showLeaderboard })"
            >
              🏆 排行
            </button>
          </div>

          <h1 class="room-title">{{ roomStore.roomName || '房间' }}</h1>

          <!-- Room info chips -->
          <div class="room-info-row">
            <span class="room-info-chip">🃏 {{ roomId }}</span>
            <span class="room-info-chip">👤 {{ roomStore.players?.length || 0 }}/{{ roomStore.maxSeats }}</span>
            <span class="room-info-chip">盲注 {{ roomStore.smallBlind }}/{{ roomStore.bigBlind }}</span>
          </div>

          <!-- Player list -->
          <div class="room-player-list">
            <div class="room-list-header">玩家</div>
            <div
              v-for="player in roomStore.players"
              :key="player.playerId"
              class="room-player-item"
              :class="{ 'is-me': player.playerId === userStore.playerId, 'is-disconnected': !player.connected }"
            >
              <div class="room-player-left">
                <span class="room-player-icon">{{ player.seatIndex === 0 ? '👑' : '💺' }}</span>
                <span class="room-player-name">{{ player.nickname }}</span>
                <span v-if="player.playerId === userStore.playerId" class="room-player-you">(你)</span>
              </div>
              <div class="room-player-right">
                <span class="room-player-chips">💰 {{ player.chips }}</span>
                <span v-if="(player.borrowCount || 0) > 0" class="room-player-net">
                  (净: {{ player.chips - (player.borrowCount || 0) * roomStore.initialChips }})
                </span>
              </div>
            </div>
          </div>

          <!-- Share hint -->
          <div class="room-share-hint">
            分享房间号 <span class="room-id-highlight">{{ roomId }}</span> 给朋友
          </div>

          <!-- Add bot -->
          <button
            v-if="(roomStore.players?.length || 0) < roomStore.maxSeats"
            class="room-btn room-btn-secondary"
            @click="handleAddBot"
            :disabled="addingBot"
          >
            {{ addingBot ? '添加中...' : '+ 添加机器人' }}
          </button>

          <!-- Borrow -->
          <button
            v-if="(myPlayer?.chips ?? 0) <= 0"
            class="room-btn room-btn-accent"
            @click="handleBorrow"
            :disabled="borrowing"
          >
            {{ borrowing ? '处理中...' : '💸 借筹码 (借 1000)' }}
          </button>

          <!-- Start -->
          <button
            v-if="isOwner && canStart"
            class="room-btn room-btn-primary"
            @click="handleStartGame"
          >
            开始游戏
          </button>
          <div v-else-if="isOwner && !canStart" class="room-start-hint">
            {{ startBlockReason }}
          </div>
        </div>
      </div>

      <!-- Game Over -->
      <GameOver
        v-if="roomStore.gameOver"
        :winners="roomStore.winners || []"
        :leaderboard="roomStore.leaderboard"
        :busted-player-ids="roomStore.bustedPlayerIds"
        @back-to-lobby="handleBackToRoom"
      />
    </div>

    <!-- Leaderboard Modal -->
    <div v-if="showLeaderboard" class="room-modal-overlay" @click.self="showLeaderboard = false">
      <div class="room-modal">
        <div class="room-modal-header">
          <div class="room-modal-title">🏆 排行榜</div>
          <button @click="showLeaderboard = false" class="room-link-btn">✕</button>
        </div>
        <div class="space-y-1">
          <div
            v-for="entry in leaderboard"
            :key="entry.playerId"
            class="room-leader-item"
            :class="{ 'is-first': entry.rank === 1 }"
          >
            <span class="room-leader-rank">
              {{ entry.rank === 1 ? '🥇' : entry.rank === 2 ? '🥈' : entry.rank === 3 ? '🥉' : `#${entry.rank}` }}
            </span>
            <span class="room-leader-name">
              {{ entry.nickname }}
              <span v-if="entry.playerId === userStore.playerId" class="room-player-you">(你)</span>
            </span>
            <span class="room-leader-chips" :class="{ 'positive': entry.netChips >= 0, 'negative': entry.netChips < 0 }">
              {{ entry.netChips != null ? (entry.netChips >= 0 ? '+' + entry.netChips : String(entry.netChips)) : entry.chips }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.room-screen { position:relative; min-height:100vh; font-family:'Press Start 2P',monospace; }
.room-join-state { display:flex; align-items:center; justify-content:center; min-height:100vh; padding:24px; }
.room-content { min-height:100vh; }

/* Waiting lobby */
.room-waiting-view { position:relative; min-height:100vh; display:flex; align-items:flex-start;
  padding:var(--safe-top) var(--safe-right) var(--safe-bottom) var(--safe-left); }
.room-overlay { position:absolute; inset:0; background:linear-gradient(to right, rgba(30,16,6,0.58), transparent 50%); pointer-events:none; }
.room-left-col { position:relative; z-index:1; display:flex; flex-direction:column; gap:clamp(8px,2vh,14px);
  width:clamp(260px,38vw,440px); }
.room-header { display:flex; justify-content:space-between; gap:8px; }
.room-link-btn { font-size:clamp(8px,2vh,11px); color:rgba(224,176,48,0.42); background:none; border:none; cursor:pointer; padding:0; }
.room-link-btn:hover { color:rgba(224,176,48,0.65); }
.room-title { font-size:clamp(18px,5.5vh,30px); font-weight:bold; color:var(--color-gold);
  text-shadow:0 2px 6px rgba(0,0,0,0.5); margin:0; }
.room-info-row { display:flex; gap:clamp(4px,1vw,8px); flex-wrap:wrap; }
.room-info-chip { font-size:clamp(8px,2vh,11px); padding:5px 8px; background:var(--color-input-bg);
  border:1px solid var(--color-border); border-radius:5px; color:var(--color-text-light); }
.room-player-list { display:flex; flex-direction:column; gap:4px; }
.room-list-header { font-size:clamp(8px,2vh,11px); color:rgba(224,176,48,0.45); margin-bottom:3px; }
.room-player-item { display:flex; align-items:center; justify-content:space-between; padding:clamp(8px,2vh,14px) clamp(10px,2.5vw,16px);
  border-radius:8px; background:var(--color-panel-bg); border:1px solid var(--color-border); }
.room-player-item.is-me { border-color:var(--color-gold); }
.room-player-item.is-disconnected { opacity:0.5; }
.room-player-left { display:flex; align-items:center; gap:6px; }
.room-player-icon { font-size:clamp(14px,3.5vh,18px); }
.room-player-name { font-size:clamp(8px,2vh,11px); color:var(--color-text-light); }
.room-player-you { font-size:clamp(7px,1.8vh,10px); color:var(--color-gold); }
.room-player-right { display:flex; align-items:center; gap:4px; }
.room-player-chips { font-size:clamp(8px,2vh,11px); color:var(--color-gold); }
.room-player-net { font-size:clamp(7px,1.8vh,10px); color:var(--color-accent); }
.room-share-hint { font-size:clamp(7px,1.8vh,10px); color:rgba(224,176,48,0.35); text-align:center; margin-top:4px; }
.room-id-highlight { color:var(--color-gold); }

.room-btn { font-family:'Press Start 2P',monospace; font-weight:bold; font-size:clamp(9px,2.3vh,13px);
  padding:clamp(10px,2.5vh,14px) clamp(16px,4vw,24px); border-radius:10px; cursor:pointer;
  transition:all .1s; letter-spacing:1px; border-width:2px; border-style:solid; }
.room-btn:active:not(:disabled) { transform:scale(.97); }
.room-btn-primary { background:var(--color-primary); border-color:var(--color-button-shadow);
  color:var(--color-text); box-shadow:var(--shadow-button); font-size:clamp(11px,2.7vh,15px); }
.room-btn-secondary { background:rgba(104,64,40,0.6); border-color:var(--color-button-shadow); color:var(--color-text-light); }
.room-btn-accent { background:var(--color-accent); border-color:#802020; color:var(--color-text-light); box-shadow:0 2px 0 #802020; }
.room-start-hint { font-size:clamp(7px,1.8vh,10px); color:var(--color-text-muted); text-align:center; }

.btn-back { font-family:'Press Start 2P',monospace; font-size:clamp(9px,2.3vh,13px);
  padding:clamp(10px,2.5vh,14px) clamp(16px,4vw,24px); background:var(--color-primary);
  color:var(--color-text); border:2px solid var(--color-button-shadow); border-radius:10px; cursor:pointer; }
.btn-borrow { font-family:'Press Start 2P',monospace; font-size:clamp(8px,2vh,11px);
  width:100%; padding:clamp(8px,2vh,12px); background:var(--color-accent);
  border:2px solid #802020; border-radius:10px; color:var(--color-text-light); cursor:pointer; }

/* Game view */
.room-game-view { display:flex; flex-direction:column; min-height:100vh; }
.game-top-bar { display:flex; justify-content:space-between; align-items:center;
  padding:clamp(6px,1.5vh,10px) clamp(10px,2.5vw,20px);
  padding-top:max(clamp(6px,1.5vh,10px), var(--safe-top));
  background:var(--color-panel-bg); border-bottom:1px solid var(--color-border); }
.game-room-info { display:flex; flex-direction:column; gap:2px; }
.game-room-name { font-size:clamp(9px,2.3vh,12px); color:var(--color-gold); }
.game-blinds { font-size:clamp(7px,1.8vh,10px); color:var(--color-text-muted); }
.game-meta { display:flex; align-items:center; gap:8px; }
.game-phase { font-size:clamp(8px,2vh,11px); color:var(--color-text-light); }
.game-countdown { font-size:clamp(10px,2.5vh,14px); color:var(--color-gold); }
.game-countdown.urgent { color:var(--color-accent); animation:pulse 1s infinite; }
.game-table-area { flex:1; display:flex; align-items:center; justify-content:center; padding:clamp(4px,1vh,10px); }
.game-action-area { padding:clamp(4px,1vh,8px) clamp(6px,1.5vh,12px); padding-bottom:max(clamp(8px,2vh,12px), var(--safe-bottom)); }

/* Modal */
.room-modal-overlay { position:fixed; inset:0; z-index:100; display:flex; align-items:center; justify-content:center;
  background:rgba(0,0,0,0.6); padding:16px; }
.room-modal { width:100%; max-width:clamp(280px,48vw,480px); background:var(--color-panel-bg);
  border:2px solid var(--color-button-shadow); border-radius:14px; padding:clamp(12px,3vh,20px); }
.room-modal-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:clamp(8px,2vh,12px); }
.room-modal-title { font-size:clamp(11px,2.7vh,15px); color:var(--color-gold); }
.room-leader-item { display:flex; align-items:center; gap:8px; padding:clamp(6px,1.5vh,10px) clamp(8px,2vh,12px);
  border-radius:8px; background:var(--color-input-bg); border:1px solid transparent; }
.room-leader-item.is-first { background:rgba(224,176,48,0.08); border-color:var(--color-gold); }
.room-leader-rank { font-size:clamp(12px,3vh,16px); }
.room-leader-name { flex:1; font-size:clamp(8px,2vh,11px); color:var(--color-text-light); }
.room-leader-chips { font-size:clamp(8px,2vh,11px); }
.room-leader-chips.positive { color:var(--color-gold); }
.room-leader-chips.negative { color:var(--color-accent); }

@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }
</style>
