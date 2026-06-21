<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{
  isMyTurn: boolean; canCheck: boolean; canCall: boolean; canBet: boolean; canRaise: boolean
  callAmount: number; minRaise: number; currentBet: number; timeLeftSec: number; myChips: number
}>()

const emit = defineEmits<{ action: [payload: { type: string; amount?: number }] }>()

const betAmount = ref(props.minRaise)
const showSlider = ref(false)

watch(() => props.minRaise, (v) => { if (betAmount.value < v) betAmount.value = v })
watch([() => props.canBet, () => props.canRaise], ([b, r]) => {
  if (b || r) { showSlider.value = true; betAmount.value = b ? props.minRaise : props.currentBet + props.minRaise }
  else { showSlider.value = false }
}, { immediate: true })

function doAction(type: string, amount?: number) { emit('action', { type, amount: amount ?? 0 }) }
function confirmBet() { emit('action', { type: props.canBet ? 'BET' : 'RAISE', amount: clampedBetAmount.value }) }

const betMax = computed(() => props.myChips)
const betMin = computed(() => {
  if (!props.isMyTurn) return 0
  const raw = props.canBet ? props.minRaise : (props.currentBet + props.minRaise)
  return Math.max(0, Math.min(raw, betMax.value))
})
const clampedBetAmount = computed({
  get: () => Math.max(betMin.value, Math.min(betAmount.value, betMax.value)),
  set: (v: number) => { betAmount.value = Math.max(betMin.value, Math.min(v, betMax.value)) }
})
const timerDisplay = computed(() => {
  const s = Math.max(0, Math.floor(props.timeLeftSec))
  return props.timeLeftSec <= 10 ? `⚠ ${s}s` : `${s}s`
})
</script>

<template>
  <div class="action-panel rounded-t-2xl p-4 space-y-3 transition-all"
    :class="isMyTurn ? 'opacity-100' : 'opacity-75'"
    style="background-color:var(--color-panel-bg); font-family:'Press Start 2P',monospace;">
    <div v-if="timeLeftSec > 0" class="text-center text-base font-bold"
      :class="timeLeftSec <= 10 ? 'text-red-400' : ''" style="color:var(--color-text-light);">
      {{ timerDisplay }}
    </div>
    <div v-if="!isMyTurn" class="text-center" style="color:var(--color-text-muted); font-size:12px;">等待对手操作...</div>
    <div v-else class="text-center" style="color:var(--color-gold); font-size:12px;">轮到你行动!</div>

    <div v-if="isMyTurn" class="flex gap-3">
      <button data-test="btn-fold" class="abtn afold" @click="doAction('FOLD')">弃牌</button>
      <button v-if="canCheck" data-test="btn-check" class="abtn acheck" @click="doAction('CHECK')">过牌</button>
      <button v-else-if="canCall" data-test="btn-call" class="abtn acall" @click="doAction('CALL')">跟注 {{ callAmount }}</button>
    </div>

    <div v-if="isMyTurn && showSlider" class="space-y-2">
      <div class="flex items-center gap-3">
        <input type="range" :min="betMin" :max="betMax" v-model.number="clampedBetAmount" class="flex-1 accent-yellow-400" />
        <span class="text-base font-bold text-white min-w-[50px] text-right">{{ clampedBetAmount }}</span>
      </div>
      <button data-test="btn-confirm-bet" class="abtn araise" @click="confirmBet">
        {{ canBet ? `加注 ${clampedBetAmount}` : `加注到 ${clampedBetAmount}` }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.abtn { font-family:'Press Start 2P',monospace; font-size:11px; flex:1; padding:14px 10px; border-radius:10px; font-weight:bold; transition:all 0.1s; cursor:pointer; letter-spacing:1px; border:2px solid; }
.abtn:active { transform:scale(0.97); }
.afold { background:var(--color-accent); border-color:#802020; color:var(--color-text-light); box-shadow:0 3px 0 #802020; }
.acheck, .acall { background:var(--color-primary); border-color:var(--color-button-shadow); color:var(--color-text); box-shadow:0 3px 0 var(--color-button-shadow); }
.araise { width:100%; padding:12px 10px; background:var(--color-gold); border-color:#a08020; color:#382818; box-shadow:0 3px 0 #a08020; font-size:12px; }
</style>
