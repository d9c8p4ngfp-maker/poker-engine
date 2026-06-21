<script setup lang="ts">
import { ref, computed, watch } from 'vue'
const p = defineProps<{
  isMyTurn:boolean; canCheck:boolean; canCall:boolean; canBet:boolean; canRaise:boolean
  callAmount:number; minRaise:number; currentBet:number; timeLeftSec:number; myChips:number
}>()
const emit = defineEmits<{ action:[payload:{type:string;amount?:number}] }>()
const amt = ref(p.minRaise); const show = ref(false)
watch(()=>p.minRaise,v=>{if(amt.value<v)amt.value=v})
watch([()=>p.canBet,()=>p.canRaise],([b,r])=>{
  if(b||r){show.value=true;amt.value=b?p.minRaise:p.currentBet+p.minRaise}else{show.value=false}
},{immediate:true})
const act=(t:string,a?:number)=>emit('action',{type:t,amount:a??0})
const confirm=()=>emit('action',{type:p.canBet?'BET':'RAISE',amount:clamped.value})
const maxB=computed(()=>p.myChips)
const minB=computed(()=>{if(!p.isMyTurn)return 0;const r=p.canBet?p.minRaise:(p.currentBet+p.minRaise);return Math.max(0,Math.min(r,maxB.value))})
const clamped=computed({get:()=>Math.max(minB.value,Math.min(amt.value,maxB.value)),set:(v:number)=>{amt.value=Math.max(minB.value,Math.min(v,maxB.value))}})
const timer=computed(()=>{const s=Math.max(0,Math.floor(p.timeLeftSec));return p.timeLeftSec<=10?`⚠ ${s}s`:`${s}s`})
</script>
<template>
  <div class="ap" :class="{myturn:isMyTurn}">
    <div v-if="timeLeftSec>0" class="timer" :class="{urgent:timeLeftSec<=10}">{{ timer }}</div>
    <div v-if="!isMyTurn" class="wait">等待对手操作...</div>
    <div v-else class="go">轮到你行动!</div>
    <div v-if="isMyTurn" class="row">
      <button data-test="btn-fold" class="ab fold" @click="act('FOLD')">弃牌</button>
      <button v-if="canCheck" data-test="btn-check" class="ab ck" @click="act('CHECK')">过牌</button>
      <button v-else-if="canCall" data-test="btn-call" class="ab cl" @click="act('CALL')">跟注 {{ callAmount }}</button>
    </div>
    <div v-if="isMyTurn&&show" class="bet-area">
      <div class="slider-row">
        <input type="range" :min="minB" :max="maxB" v-model.number="clamped" class="slider" />
        <span class="val">{{ clamped }}</span>
      </div>
      <button data-test="btn-confirm-bet" class="ab raise" @click="confirm">
        {{ canBet?`加注 ${clamped}`:`加注到 ${clamped}` }}
      </button>
    </div>
  </div>
</template>
<style scoped>
.ap { border-radius:16px 16px 0 0; padding:clamp(8px,2vh,14px); background:var(--color-panel-bg);
  font-family:'Press Start 2P',monospace; transition:opacity .2s; opacity:.75; display:flex; flex-direction:column; gap:clamp(6px,1.5vh,10px); }
.ap.myturn { opacity:1; }
.timer { text-align:center; font-size:clamp(10px,2.5vh,14px); font-weight:bold; color:var(--color-text-light); }
.timer.urgent { color:var(--color-accent); animation:pulse 1s infinite; }
.wait { text-align:center; font-size:clamp(9px,2.3vh,12px); color:var(--color-text-muted); }
.go { text-align:center; font-size:clamp(9px,2.3vh,13px); color:var(--color-gold); }
.row { display:flex; gap:clamp(6px,1.5vh,10px); }
.ab { font-family:'Press Start 2P',monospace; font-size:clamp(9px,2.3vh,12px); flex:1; padding:clamp(8px,2vh,14px) 8px;
  border-radius:10px; font-weight:bold; cursor:pointer; letter-spacing:1px; border:2px solid; transition:all .1s; }
.ab:active { transform:scale(.97); }
.fold { background:var(--color-accent); border-color:#802020; color:var(--color-text-light); box-shadow:0 2px 0 #802020; }
.ck,.cl { background:var(--color-primary); border-color:var(--color-button-shadow); color:var(--color-text); box-shadow:0 2px 0 var(--color-button-shadow); }
.bet-area { display:flex; flex-direction:column; gap:clamp(4px,1vh,8px); }
.slider-row { display:flex; align-items:center; gap:clamp(6px,1.5vh,10px); }
.slider { flex:1; accent-color:#f0c040; }
.val { font-size:clamp(9px,2.3vh,13px); font-weight:bold; color:#fff; min-width:44px; text-align:right; }
.raise { width:100%; font-size:clamp(9px,2.5vh,13px); padding:clamp(8px,2vh,12px) 8px;
  background:var(--color-gold); border-color:#a08020; color:#382818; box-shadow:0 2px 0 #a08020; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }
</style>
