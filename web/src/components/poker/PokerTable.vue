<script setup lang="ts">
import { computed } from 'vue'
import PlayerSeat from './PlayerSeat.vue'
import PlayingCard from './PlayingCard.vue'

interface SeatPlayer {
  playerId: string
  nickname: string
  chips: number
  betInRound: number
  folded: boolean
  allIn: boolean
  seatIndex: number
  holeCards: string[] | null
  isDealer: boolean
}

const props = defineProps<{
  players: SeatPlayer[]
  communityCards: string[]
  pot: number
  dealerIndex: number
  currentPlayerIndex: number
  myPlayerId: string
  showdown?: boolean
}>()

// Seat positions mapped to the 8 chairs on the top-down tavern table background
// Coordinates relative to the table container
const SEAT_POSITIONS: { x: number; y: number }[] = [
  { x: 22, y: 26 },   // seat 0 — top-left
  { x: 42, y: 22 },   // seat 1 — top-center
  { x: 59, y: 24 },   // seat 2 — top-right
  { x: 14, y: 42 },   // seat 3 — mid-left
  { x: 81, y: 44 },   // seat 4 — mid-right
  { x: 27, y: 72 },   // seat 5 — bottom-left
  { x: 47, y: 76 },   // seat 6 — bottom-center
  { x: 67, y: 72 },   // seat 7 — bottom-right
]

const sortedPlayers = computed(() => {
  return [...props.players]
    .sort((a, b) => a.seatIndex - b.seatIndex)
    .map(p => ({
      ...p,
      position: SEAT_POSITIONS[p.seatIndex] ?? SEAT_POSITIONS[0],
    }))
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
  <div
    class="poker-table relative mx-auto w-full max-w-lg aspect-[4/3] overflow-hidden"
    style="image-rendering: pixelated;"
  >
    <!-- Table felt overlay (semi-transparent to show background) -->
    <div class="absolute inset-0 bg-black/20 rounded-lg"></div>

    <!-- Player seats -->
    <div
      v-for="p in sortedPlayers"
      :key="p.playerId"
      class="absolute"
      :style="{
        left: p.position.x + '%',
        top: p.position.y + '%',
        transform: 'translate(-50%, -50%)',
      }"
    >
      <PlayerSeat
        v-bind="p"
        :is-dealer="playerIndex(p.playerId) === dealerIndex"
        :is-current-player="playerIndex(p.playerId) === currentPlayerIndex"
        :is-me="p.playerId === myPlayerId"
        :showdown="showdown"
      />
    </div>

    <!-- Community cards center area -->
    <div class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
      <div class="flex justify-center items-center gap-1">
        <div
          v-for="(card, i) in cardSlots"
          :key="i"
          class="w-8 h-11 rounded border border-dashed border-white/15 flex items-center justify-center"
          :class="card ? 'border-solid border-white/30' : ''"
        >
          <PlayingCard v-if="card" :card="card" :face-up="true" size="sm" />
        </div>
      </div>

      <!-- Pot -->
      <div class="text-center text-sm font-bold mt-1 px-2 py-0.5 rounded-full"
        style="color: var(--color-gold); background: rgba(0,0,0,0.55); font-family: 'Press Start 2P', monospace; font-size: 8px;">
        🏆 {{ pot }}
      </div>
    </div>
  </div>
</template>
