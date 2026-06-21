<script setup lang="ts">
import { computed } from 'vue'
import PlayerSeat from './PlayerSeat.vue'
import PlayingCard from './PlayingCard.vue'

interface SeatPlayer {
  playerId:string; nickname:string; chips:number; betInRound:number
  folded:boolean; allIn:boolean; seatIndex:number; holeCards:string[]|null; isDealer:boolean
}

const props = defineProps<{
  players:SeatPlayer[]; communityCards:string[]; pot:number
  dealerIndex:number; currentPlayerIndex:number; myPlayerId:string; showdown?:boolean
}>()

const SEATS:{x:number;y:number}[] = [
  {x:22,y:26},{x:42,y:22},{x:59,y:24},
  {x:14,y:42},{x:81,y:44},
  {x:27,y:72},{x:47,y:76},{x:67,y:72},
]

const sorted = computed(() =>
  [...props.players].sort((a,b)=>a.seatIndex-b.seatIndex)
    .map(p=>({...p,pos:SEATS[p.seatIndex]??SEATS[0]}))
)

const idx = (id:string)=>props.players.findIndex(p=>p.playerId===id)

const slots = computed(()=>{
  const s:(string|null)[]=[null,null,null,null,null]
  props.communityCards.forEach((c,i)=>{if(i<5)s[i]=c})
  return s
})
</script>

<template>
  <div class="table" style="image-rendering:pixelated;">
    <div class="felt"></div>
    <div v-for="p in sorted" :key="p.playerId" class="seat-wrap"
      :style="{left:p.pos.x+'%',top:p.pos.y+'%',transform:'translate(-50%,-50%)'}">
      <PlayerSeat v-bind="p" :is-dealer="idx(p.playerId)===dealerIndex"
        :is-current-player="idx(p.playerId)===currentPlayerIndex"
        :is-me="p.playerId===myPlayerId" :showdown="showdown" />
    </div>
    <div class="center">
      <div class="cards-row">
        <div v-for="(c,i) in slots" :key="i" class="card-slot" :class="{active:c}">
          <PlayingCard v-if="c" :card="c" :face-up="true" size="md" />
        </div>
      </div>
      <div class="pot">🏆 {{ pot }}</div>
    </div>
  </div>
</template>

<style scoped>
.table { position:relative; margin:0 auto; width:100%; max-width:clamp(520px, 88vw, 860px); aspect-ratio:4/3; overflow:hidden; border-radius:12px; }
.felt { position:absolute; inset:0; background:rgba(0,0,0,0.15); border-radius:12px; }
.seat-wrap { position:absolute; }
.center { position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; pointer-events:none; }
.cards-row { display:flex; justify-content:center; align-items:center; gap:clamp(2px,0.4vw,6px); }
.card-slot { width:clamp(32px,6vw,44px); height:clamp(44px,8.5vw,60px); border-radius:4px;
  border:1px dashed rgba(255,255,255,0.12); display:flex; align-items:center; justify-content:center; }
.card-slot.active { border:1px solid rgba(255,255,255,0.25); }
.pot { text-align:center; font-weight:bold; margin-top:clamp(4px,1vh,10px); padding:3px 10px; border-radius:999px;
  color:var(--color-gold); background:rgba(0,0,0,0.5); font-family:'Press Start 2P',monospace; font-size:clamp(9px,2.3vh,13px); }
</style>
