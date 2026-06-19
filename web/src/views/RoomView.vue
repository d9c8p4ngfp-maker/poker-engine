<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWebSocket } from '../composables/useWebSocket'
import { useRoomStore } from '../stores/room'
import { useUserStore } from '../stores/user'
import PokerTable from '../components/poker/PokerTable.vue'
import ActionPanel from '../components/poker/ActionPanel.vue'
import HandResult from '../components/poker/HandResult.vue'

const route = useRoute()
const router = useRouter()
const roomId = route.params.roomId as string
const { connect, disconnect, subscribe, send, connected } = useWebSocket()
const roomStore = useRoomStore()
const userStore = useUserStore()

const joinError = ref('')

interface PlayerView {
  playerId: string
  nickname: string
  seatIndex: number
  chips: number
  connected: boolean
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
  const idx = roomStore.currentPlayerIndex
  if (idx < 0 || idx >= roomStore.players.length) return false
  return roomStore.players[idx].playerId === userStore.playerId
})

const myPlayer = computed(() => {
  return roomStore.players.find(p => p.playerId === userStore.playerId) || null
})

const isOwner = computed(() => {
  if (roomStore.players.length === 0) return false
  return roomStore.players[0].playerId === userStore.playerId
})

const canStart = computed(() => {
  return roomStore.players.length >= 2 && roomStore.status === 'WAITING'
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
    holeCards: p.playerId === userStore.playerId ? roomStore.myHoleCards : null,
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
    canBet: roomStore.currentBet === 0 && myPlayer.value.chips >= roomStore.minRaise,
    canRaise: roomStore.currentBet > 0 && myPlayer.value.chips > toCall,
    callAmount: Math.min(toCall, myPlayer.value.chips),
  }
})

function handleAction(payload: { type: string; amount?: number }) {
  send(`/app/game/${roomId}/action`, {
    playerId: userStore.playerId,
    action: payload.type,
    amount: payload.amount || 0,
  })
}

function handleNextHand() {
  send(`/app/game/${roomId}/start`, {})
}

function handleStartGame() {
  send(`/app/game/${roomId}/start`, {})
}

onMounted(async () => {
  try {
    await connect()
  } catch (e) {
    joinError.value = 'WebSocket 连接失败'
    return
  }

  // Room updates
  subscribe(`/topic/room/${roomId}`, (msg) => {
    const data = JSON.parse(msg.body)
    if (data.type === 'system') {
      roomStore.addSystemMessage(data.text)
    } else if (data.roomId) {
      roomStore.roomId = data.roomId
      roomStore.roomName = data.name || ''
      roomStore.status = data.status || 'WAITING'
      roomStore.players = (data.players || []).map((p: PlayerView) => ({
        playerId: p.playerId,
        nickname: p.nickname,
        seatIndex: p.seatIndex,
        chips: p.chips,
        betInRound: 0,
        folded: false,
        allIn: false,
        holeCards: null,
        lastAction: null,
        connected: p.connected,
      }))
      roomStore.smallBlind = data.smallBlind || 10
      roomStore.bigBlind = data.bigBlind || 20
    }
  })

  // Game state updates
  subscribe(`/topic/room/${roomId}/game`, (msg) => {
    const data = JSON.parse(msg.body)
    roomStore.updateFromSnapshot(data, userStore.playerId)
  })

  // Join via REST
  try {
    const res = await fetch(`/api/rooms/${roomId}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        playerId: userStore.playerId,
        nickname: userStore.nickname,
      }),
    })
    if (!res.ok) {
      if (res.status === 404) joinError.value = '房间不存在'
      else if (res.status === 403) joinError.value = '房间密码错误'
      else if (res.status === 409) joinError.value = '房间已满'
      else joinError.value = '加入失败'
      return
    }

    const data: SnapshotPayload = await res.json()
    roomStore.roomId = data.roomId
    roomStore.roomName = data.name
    roomStore.status = data.status as any
    roomStore.players = data.players.map((p: PlayerView) => ({
      playerId: p.playerId,
      nickname: p.nickname,
      seatIndex: p.seatIndex,
      chips: p.chips,
      betInRound: 0,
      folded: false,
      allIn: false,
      holeCards: null,
      lastAction: null,
      connected: p.connected,
    }))
    roomStore.smallBlind = data.smallBlind
    roomStore.bigBlind = data.bigBlind
    roomStore.addSystemMessage(`${userStore.nickname} 加入了房间`)
  } catch (e) {
    joinError.value = '连接服务器失败'
  }
})

onUnmounted(() => {
  disconnect()
  roomStore.reset()
})

function handleLeave() {
  router.push('/')
}
</script>

<template>
  <div class="min-h-screen flex flex-col" style="background-color: var(--color-surface)">
    <!-- Header -->
    <div class="p-4 flex items-center justify-between" style="background-color: var(--color-surface-light)">
      <button @click="handleLeave" class="text-sm" style="color: var(--color-text-muted)">
        ← 退出
      </button>
      <div class="text-center">
        <div class="font-bold" style="color: var(--color-gold)">{{ roomStore.roomName || '房间' }}</div>
        <div class="text-xs" style="color: var(--color-text-muted)">
          房间号: <span class="tracking-widest font-mono">{{ roomId }}</span>
        </div>
      </div>
      <div class="text-xs" :style="{ color: connected ? 'var(--color-primary)' : 'var(--color-accent)' }">
        {{ connected ? '已连接' : '断开' }}
      </div>
    </div>

    <!-- Error -->
    <div v-if="joinError" class="p-4 text-center" style="color: var(--color-accent)">
      {{ joinError }}
      <br />
      <button @click="router.push('/')" class="mt-2 underline">返回大厅</button>
    </div>

    <!-- Game Content -->
    <div v-if="!joinError" class="flex-1 flex flex-col">
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
          />
        </div>

        <!-- Action panel -->
        <ActionPanel
          :is-my-turn="isMyTurn"
          :can-check="legalActions.canCheck"
          :can-call="legalActions.canCall"
          :can-bet="legalActions.canBet"
          :can-raise="legalActions.canRaise"
          :call-amount="legalActions.callAmount"
          :min-raise="roomStore.bigBlind"
          :time-left-sec="roomStore.timeLeftSec"
          :my-chips="myPlayer?.chips || 0"
          @action="handleAction"
        />

        <!-- Hand Result Overlay -->
        <HandResult
          v-if="roomStore.winners"
          :winners="roomStore.winners"
          :is-owner="isOwner"
          @next-hand="handleNextHand"
        />
      </div>

      <!-- WAITING state -->
      <div v-else class="flex-1 p-4 space-y-4">
        <div class="text-center py-2 rounded-lg" style="background-color: var(--color-surface-light)">
          <span class="text-sm" style="color: var(--color-text-muted)">
            状态: 等待玩家加入...
          </span>
          <span class="ml-2 text-sm" style="color: var(--color-text-muted)">
            {{ roomStore.players.length }}/{{ roomStore.players.length > 0 ? 8 : '?' }}人
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
            </span>
          </div>
        </div>

        <!-- Share hint -->
        <div class="text-center text-xs mt-4" style="color: var(--color-text-muted)">
          分享房间号 <span class="font-mono tracking-widest" style="color: var(--color-gold)">{{ roomId }}</span> 给朋友即可加入
        </div>

        <!-- Start Button -->
        <button
          v-if="isOwner && canStart"
          class="w-full py-4 rounded-lg font-bold text-white text-lg transition active:scale-95"
          style="background-color: var(--color-primary)"
          @click="handleStartGame"
        >
          开始游戏
        </button>
        <div v-else-if="isOwner && !canStart" class="text-center text-sm" style="color: var(--color-text-muted)">
          至少需要2人才能开始
        </div>
      </div>
    </div>
  </div>
</template>
