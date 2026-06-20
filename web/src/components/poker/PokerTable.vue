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

// Oval seat positions for 0–7 players (percentage-based, relative to table container).
// seatIndex 0 always at bottom-center.
const SEAT_POSITIONS: { x: number; y: number }[] = [
  { x: 50, y: 93 },   // seat 0  bottom-center
  { x: 88, y: 78 },   // seat 1  bottom-right
  { x: 95, y: 53 },   // seat 2  right
  { x: 88, y: 25 },   // seat 3  top-right
  { x: 50, y: 10 },   // seat 4  top
  { x: 12, y: 25 },   // seat 5  top-left
  { x:  5, y: 53 },   // seat 6  left
  { x: 12, y: 78 },   // seat 7  bottom-left
]

// Sort players by seatIndex and attach computed position
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
    class="poker-table relative mx-auto w-full max-w-md aspect-[4/3] rounded-[50%] overflow-hidden"
    style="background-color: var(--color-felt)"
  >
    <!-- Table border -->
    <div class="absolute inset-2 rounded-[50%] border-4 border-opacity-20 border-white"></div>

    <!-- Player seats laid out around the oval -->
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

    <!-- Community cards area (center of table) -->
    <div class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
      <div class="flex justify-center items-center gap-1">
        <div
          v-for="(card, i) in cardSlots"
          :key="i"
          class="w-8 h-11 rounded border border-dashed border-white/20 flex items-center justify-center"
          :class="card ? 'border-solid border-white/50' : ''"
        >
          <PlayingCard v-if="card" :card="card" :face-up="true" size="sm" />
        </div>
      </div>

      <!-- Pot -->
      <div class="text-center text-sm font-bold mt-1 px-2 py-0.5 rounded-full" style="color: var(--color-gold); background: rgba(0,0,0,0.4)">
        🏆 {{ pot }}
      </div>
    </div>
  </div>
</template>
