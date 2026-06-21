<script setup lang="ts">
import PlayingCard from './PlayingCard.vue'

const props = defineProps<{
  playerId: string; nickname: string; chips: number; betInRound: number
  folded: boolean; allIn: boolean; isDealer: boolean
  isCurrentPlayer: boolean; isMe: boolean; holeCards: string[] | null; showdown?: boolean
}>()
</script>

<template>
  <div data-test="seat"
    class="player-seat flex flex-col items-center gap-1 p-2 rounded-lg border-2 transition-all"
    :class="[
      isCurrentPlayer ? 'border-yellow-400 shadow-lg shadow-yellow-400/30 scale-110' : 'border-transparent',
      folded ? 'opacity-40' : '',
    ]"
    style="background-color: var(--color-panel-bg)">
    <div v-if="isDealer" class="text-xs px-1.5 rounded-full font-bold bg-yellow-500 text-black">D</div>
    <div class="flex gap-0.5 -space-x-3">
      <template v-if="holeCards">
        <PlayingCard v-for="(card, i) in holeCards" :key="i" :card="card" :face-up="isMe || showdown" size="sm" />
      </template>
    </div>
    <span class="seat-nick" :class="{ 'text-yellow-400': isMe }">{{ nickname }}</span>
    <span class="seat-chips">💰{{ chips }}</span>
    <span v-if="betInRound > 0" class="seat-bet">下注 {{ betInRound }}</span>
    <div v-if="folded" data-test="folded" class="seat-fold">FOLD</div>
    <div v-if="allIn" class="seat-allin">ALL IN</div>
  </div>
</template>

<style scoped>
.seat-nick { font-family:'Press Start 2P',monospace; font-size:10px; color:var(--color-text-light); font-weight:bold; max-width:100px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.seat-chips { font-family:'Press Start 2P',monospace; font-size:10px; color:var(--color-gold); }
.seat-bet { font-family:'Press Start 2P',monospace; font-size:9px; background-color:var(--color-primary); color:var(--color-text); padding:2px 6px; border-radius:999px; }
.seat-fold { font-family:'Press Start 2P',monospace; font-size:9px; color:#999; font-weight:bold; }
.seat-allin { font-family:'Press Start 2P',monospace; font-size:9px; color:#f06060; font-weight:bold; animation:pulse 0.8s infinite; }
.seat-nick.text-yellow-400 { color:var(--color-gold); }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.5} }
</style>
