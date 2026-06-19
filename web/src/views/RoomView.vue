<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWebSocket } from '../composables/useWebSocket'
import { useRoomStore } from '../stores/room'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const roomId = route.params.roomId as string
const { connect, disconnect, subscribe, connected } = useWebSocket()
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

const canStart = computed(() => {
  return roomStore.players.length >= 2 && roomStore.status === 'WAITING'
})

const isOwner = computed(() => {
  if (roomStore.players.length === 0) return false
  return roomStore.players[0].playerId === userStore.playerId
})

onMounted(async () => {
  try {
    await connect()
  } catch (e) {
    joinError.value = 'WebSocket 连接失败'
    return
  }

  subscribe(`/topic/room/${roomId}`, (msg) => {
    const data = JSON.parse(msg.body)
    if (data.type === 'system') {
      roomStore.addSystemMessage(data.text)
    } else if (data.roomId) {
      // Room snapshot
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

  // Join via REST first, then WS will broadcast updates
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

    <!-- Room Content -->
    <div v-if="!joinError" class="flex-1 p-4 space-y-4">
      <!-- Status -->
      <div class="text-center py-2 rounded-lg" style="background-color: var(--color-surface-light)">
        <span class="text-sm" style="color: var(--color-text-muted)">
          状态: {{ roomStore.status === 'WAITING' ? '等待玩家加入...' : roomStore.status }}
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

      <!-- System Messages -->
      <div class="space-y-1 max-h-32 overflow-y-auto">
        <div
          v-for="(msg, i) in roomStore.messages.slice(-10)"
          :key="i"
          class="text-xs px-2 py-1 rounded"
          style="color: var(--color-text-muted); background-color: var(--color-surface-light)"
        >
          {{ msg.text }}
        </div>
      </div>

      <!-- Start Button (owner only) -->
      <button
        v-if="isOwner && canStart"
        class="w-full py-4 rounded-lg font-bold text-white text-lg transition"
        style="background-color: var(--color-primary)"
      >
        开始游戏 🃏
      </button>
      <div v-else-if="isOwner && !canStart" class="text-center text-sm" style="color: var(--color-text-muted)">
        至少需要2人才能开始
      </div>

      <!-- Share hint -->
      <div class="text-center text-xs mt-4" style="color: var(--color-text-muted)">
        分享房间号 <span class="font-mono tracking-widest" style="color: var(--color-gold)">{{ roomId }}</span> 给朋友即可加入
      </div>
    </div>
  </div>
</template>
