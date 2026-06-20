<script setup lang="ts">
import PlayingCard from './PlayingCard.vue'

const props = defineProps<{
  playerId: string
  nickname: string
  chips: number
  betInRound: number
  folded: boolean
  allIn: boolean
  isDealer: boolean
  isCurrentPlayer: boolean
  isMe: boolean
  holeCards: string[] | null
  showdown?: boolean
}>()
</script>

<template>
  <div
    data-test="seat"
    class="player-seat flex flex-col items-center gap-1 p-1 rounded-lg border-2 transition-all"
    :class="[
      isCurrentPlayer ? 'border-yellow-400 shadow-lg shadow-yellow-400/20' : 'border-transparent',
      folded ? 'opacity-40' : '',
    ]"
    style="background-color: var(--color-surface-light)"
  >
    <!-- Dealer badge -->
    <div v-if="isDealer" class="text-xs px-1.5 rounded-full font-bold bg-yellow-500 text-black">D</div>

    <!-- Hole cards -->
    <div class="flex gap-0.5 -space-x-3">
      <template v-if="holeCards">
        <PlayingCard
          v-for="(card, i) in holeCards"
          :key="i"
          :card="card"
          :face-up="isMe || showdown"
          size="sm"
        />
      </template>
    </div>

    <!-- Nickname -->
    <span
      class="text-xs font-bold truncate max-w-[80px]"
      :class="isMe ? 'text-yellow-400' : 'text-white'"
    >
      {{ nickname }}
    </span>

    <!-- Chips -->
    <span class="text-xs" style="color: var(--color-gold)">💰{{ chips }}</span>

    <!-- Current bet -->
    <span v-if="betInRound > 0" class="text-xs px-1.5 rounded-full" style="background-color: var(--color-primary)">
      下注 {{ betInRound }}
    </span>

    <!-- Folded -->
    <div v-if="folded" data-test="folded" class="text-xs text-gray-500 font-bold">FOLD</div>

    <!-- All-in -->
    <div v-if="allIn" class="text-xs text-red-400 font-bold animate-pulse">ALL IN</div>
  </div>
</template>
