<script setup lang="ts">
import PlayingCard from './PlayingCard.vue'
const p = defineProps<{
  playerId:string; nickname:string; chips:number; betInRound:number
  folded:boolean; allIn:boolean; isDealer:boolean
  isCurrentPlayer:boolean; isMe:boolean; holeCards:string[]|null; showdown?:boolean
  allInState?:boolean; inGame?:boolean
}>()
</script>
<template>
  <div
    data-test="seat"
    class="seat"
    :class="{
      'cur': isCurrentPlayer,
      'folded': folded,
      'allin-state': allIn,
      'out-of-game': inGame === false,
    }"
  >
    <div v-if="isDealer" class="dealer">D</div>
    <div class="cards">
      <template v-if="holeCards">
        <PlayingCard v-for="(c,i) in holeCards" :key="i" :card="c" :face-up="isMe||showdown" :size="isMe ? 'md' : 'sm'" />
      </template>
    </div>
    <span class="nick" :class="{ 'me': isMe }">{{ nickname }}</span>
    <span class="chips">💰{{ chips }}</span>
    <span v-if="betInRound>0" class="bet">下注 {{ betInRound }}</span>
    <div v-if="folded" data-test="folded" class="fold">FOLD</div>
    <div v-if="allIn" class="allin">ALL IN</div>
  </div>
</template>
<style scoped>
.seat {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: clamp(4px, 1vh, 8px); border-radius: 8px;
  border: 2px solid transparent; transition: all 0.15s;
  background: var(--color-panel-bg);
}
.seat.cur {
  border-color: #f0c040;
  box-shadow: 0 0 16px rgba(240, 192, 64, 0.5), 0 0 4px rgba(240, 192, 64, 0.8);
  transform: scale(1.1);
}
.seat.folded { opacity: 0.35; filter: grayscale(0.6); }
.seat.allin-state {
  border-color: #f06060;
  box-shadow: 0 0 12px rgba(240, 96, 96, 0.4);
}
.seat.out-of-game {
  opacity: 0.45; filter: grayscale(0.5) brightness(0.8);
  border-style: dashed;
}
.seat.out-of-game .nick::after {
  content: ' 👀'; font-size: 0.7em;
}
.dealer {
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(7px, 1.8vh, 10px);
  padding: 1px 5px; border-radius: 999px;
  font-weight: bold; background: #f0c040; color: #382818;
}
.cards { display: flex; gap: 1px; }
.nick {
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(6px, 1.6vh, 10px);
  color: var(--color-text-light);
  font-weight: bold; max-width: clamp(48px, 10vw, 84px); overflow: hidden;
  text-overflow: ellipsis; white-space: nowrap;
}
.nick.me { color: var(--color-gold); }
.chips {
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(6px, 1.6vh, 10px);
  color: var(--color-gold);
}
.bet {
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(6px, 1.6vh, 9px);
  background: var(--color-primary);
  color: var(--color-text);
  padding: 1px 5px; border-radius: 999px;
}
.fold {
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(6px, 1.6vh, 9px);
  color: #888; font-weight: bold;
}
.allin {
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(6px, 1.6vh, 9px);
  color: #f06060; font-weight: bold;
  animation: pulse 0.8s infinite;
}
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>
