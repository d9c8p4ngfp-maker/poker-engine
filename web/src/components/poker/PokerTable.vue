<script setup lang="ts">
import { computed } from 'vue'
import PlayerSeat from './PlayerSeat.vue'
import PlayingCard from './PlayingCard.vue'

interface SeatPlayer {
  playerId:string; nickname:string; chips:number; betInRound:number
  folded:boolean; allIn:boolean; seatIndex:number; holeCards:string[]|null; isDealer:boolean
  allInState?:boolean; inGame?:boolean
}

const props = defineProps<{
  players:SeatPlayer[]; communityCards:string[]; pot:number
  dealerPlayerId:string|null; currentPlayerIndex:number; currentPlayerId:string|null; myPlayerId:string; showdown?:boolean
}>()

const ALL_SEATS:{x:number;y:number}[] = [
  { x: 28, y: 18 },  // 0: 上左
  { x: 50, y: 14 },  // 1: 上中
  { x: 72, y: 18 },  // 2: 上右
  { x: 15, y: 48 },  // 3: 左中
  { x: 85, y: 48 },  // 4: 右中
  { x: 28, y: 82 },  // 5: 下左
  { x: 50, y: 86 },  // 6: 下中
  { x: 72, y: 82 },  // 7: 下右
]

const BOTTOM_CENTER = 6

const sorted = computed(() => {
  const me = props.players.find(p => p.playerId === props.myPlayerId)
  const mySeat = me?.seatIndex ?? 0
  const offset = (BOTTOM_CENTER - mySeat + ALL_SEATS.length) % ALL_SEATS.length

  return [...props.players]
    .sort((a, b) => a.seatIndex - b.seatIndex)
    .map(p => {
      const visualIdx = (p.seatIndex + offset) % ALL_SEATS.length
      return { ...p, pos: ALL_SEATS[visualIdx] ?? ALL_SEATS[0] }
    })
})

const slots = computed(() => {
  const s:(string|null)[] = [null, null, null, null, null]
  props.communityCards.forEach((c, i) => { if (i < 5) s[i] = c })
  return s
})
</script>

<template>
  <div class="table" style="image-rendering:pixelated;">
    <div class="felt"></div>
    <div v-for="p in sorted" :key="p.playerId" class="seat-wrap"
      :style="{ left: p.pos.x + '%', top: p.pos.y + '%', transform: 'translate(-50%,-50%)' }">
      <PlayerSeat v-bind="p" :is-dealer="p.playerId === dealerPlayerId"
        :is-current-player="p.playerId === props.currentPlayerId"
        :is-me="p.playerId === myPlayerId" :showdown="showdown" />
    </div>
    <div class="center">
      <div class="cards-row">
        <div v-for="(c, i) in slots" :key="i" class="card-slot" :class="{ active: c }">
          <PlayingCard v-if="c" :card="c" :face-up="true" size="md" />
        </div>
      </div>
      <div class="pot" :key="pot">🏆 {{ pot }}</div>
    </div>
  </div>
</template>

<style scoped>
.table {
  position: relative; margin: 0 auto; width: 100%;
  max-width: min(92vw, 960px);
  aspect-ratio: 16/9;
  max-height: 100%;
  overflow: hidden; border-radius: 12px;
}
.felt {
  position: absolute; inset: 0;
  background: rgba(64, 144, 72, 0.08);
  border-radius: 12px;
}
.seat-wrap { position: absolute; }
.center {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center; pointer-events: none;
}
.cards-row {
  display: flex; justify-content: center; align-items: center; gap: clamp(2px, 0.4vw, 6px);
}
.card-slot {
  width: clamp(24px, 5vw, 44px); height: clamp(34px, 7vw, 60px);
  border-radius: 4px; border: 1px dashed rgba(255, 255, 255, 0.12);
  display: flex; align-items: center; justify-content: center;
}
.card-slot.active { border: 1px solid rgba(255, 255, 255, 0.25); }
.pot {
  text-align: center; font-weight: bold; margin-top: clamp(4px, 1vh, 10px);
  padding: 3px 10px; border-radius: 999px; color: var(--color-gold);
  background: rgba(0, 0, 0, 0.5); font-family: 'Press Start 2P', monospace;
  font-size: clamp(9px, 2.3vh, 13px);
  animation: potBump 0.3s ease;
}
@keyframes potBump {
  0% { transform: scale(1); }
  50% { transform: scale(1.2); }
  100% { transform: scale(1); }
}
@media (orientation: portrait) {
  .table { aspect-ratio: 4/3; }
}
</style>
