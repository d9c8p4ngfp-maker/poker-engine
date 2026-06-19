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
}>()

// Arrange players into top row and bottom row for mobile layout
const topSeats = computed(() => {
  return props.players.filter(p => p.seatIndex !== 0).slice(0, 4)
})

const bottomSeats = computed(() => {
  return props.players.filter(p => p.seatIndex === 0)
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
  <div class="poker-table relative mx-auto w-full max-w-md rounded-2xl overflow-hidden"
       style="background-color: var(--color-felt)">
    <!-- Table border -->
    <div class="absolute inset-2 rounded-xl border-4 border-opacity-20 border-white"></div>

    <!-- Top seats row -->
    <div class="flex justify-center gap-1 pt-4 pb-1 px-2">
      <PlayerSeat
        v-for="p in topSeats"
        :key="p.playerId"
        v-bind="p"
        :is-dealer="playerIndex(p.playerId) === dealerIndex"
        :is-current-player="playerIndex(p.playerId) === currentPlayerIndex"
        :is-me="p.playerId === myPlayerId"
      />
    </div>

    <!-- Community cards area -->
    <div class="flex justify-center items-center gap-1 py-2">
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
    <div class="text-center text-sm font-bold py-1" style="color: var(--color-gold)">
      🏆 {{ pot }}
    </div>

    <!-- Bottom seats row (you) -->
    <div class="flex justify-center gap-1 pb-4 pt-1 px-2">
      <PlayerSeat
        v-for="p in bottomSeats"
        :key="p.playerId"
        v-bind="p"
        :is-dealer="playerIndex(p.playerId) === dealerIndex"
        :is-current-player="playerIndex(p.playerId) === currentPlayerIndex"
        :is-me="p.playerId === myPlayerId"
      />
    </div>
  </div>
</template>
