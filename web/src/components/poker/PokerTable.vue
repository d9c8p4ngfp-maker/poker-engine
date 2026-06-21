<script setup lang="ts">
import { computed } from 'vue'
import PlayerSeat from './PlayerSeat.vue'
import PlayingCard from './PlayingCard.vue'

interface SeatPlayer {
  playerId: string; nickname: string; chips: number; betInRound: number
  folded: boolean; allIn: boolean; seatIndex: number; holeCards: string[] | null; isDealer: boolean
}

const props = defineProps<{
  players: SeatPlayer[]; communityCards: string[]; pot: number
  dealerIndex: number; currentPlayerIndex: number; myPlayerId: string; showdown?: boolean
}>()

const SEAT_POSITIONS: { x: number; y: number }[] = [
  { x: 22, y: 26 }, { x: 42, y: 22 }, { x: 59, y: 24 },
  { x: 14, y: 42 }, { x: 81, y: 44 },
  { x: 27, y: 72 }, { x: 47, y: 76 }, { x: 67, y: 72 },
]

const sortedPlayers = computed(() => {
  return [...props.players].sort((a, b) => a.seatIndex - b.seatIndex)
    .map(p => ({ ...p, position: SEAT_POSITIONS[p.seatIndex] ?? SEAT_POSITIONS[0] }))
})

function playerIndex(playerId: string): number {
  return props.players.findIndex(p => p.playerId === playerId)
}

const cardSlots = computed(() => {
  const slots: (string | null)[] = [null, null, null, null, null]
  props.communityCards.forEach((c, i) => { if (i < 5) slots[i] = c })
  return slots
})
</script>

<template>
  <div class="poker-table relative mx-auto w-full max-w-3xl aspect-[4/3] overflow-hidden" style="image-rendering:pixelated;">
    <div class="absolute inset-0 bg-black/20 rounded-lg"></div>

    <div v-for="p in sortedPlayers" :key="p.playerId" class="absolute"
      :style="{ left: p.position.x + '%', top: p.position.y + '%', transform: 'translate(-50%, -50%)' }">
      <PlayerSeat v-bind="p" :is-dealer="playerIndex(p.playerId) === dealerIndex"
        :is-current-player="playerIndex(p.playerId) === currentPlayerIndex"
        :is-me="p.playerId === myPlayerId" :showdown="showdown" />
    </div>

    <div class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
      <div class="flex justify-center items-center gap-1.5">
        <div v-for="(card, i) in cardSlots" :key="i"
          class="w-10 h-14 rounded border border-dashed border-white/15 flex items-center justify-center"
          :class="card ? 'border-solid border-white/30' : ''">
          <PlayingCard v-if="card" :card="card" :face-up="true" size="md" />
        </div>
      </div>
      <div class="text-center font-bold mt-2 px-3 py-1 rounded-full"
        style="color:var(--color-gold); background:rgba(0,0,0,0.55); font-family:'Press Start 2P',monospace; font-size:12px;">
        🏆 {{ pot }}
      </div>
    </div>
  </div>
</template>
