<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{
  isMyTurn: boolean
  canCheck: boolean
  canCall: boolean
  canBet: boolean
  canRaise: boolean
  callAmount: number
  minRaise: number
  currentBet: number
  timeLeftSec: number
  myChips: number
}>()

const emit = defineEmits<{
  action: [payload: { type: string; amount?: number }]
}>()

const betAmount = ref(props.minRaise)
const showSlider = ref(false)

watch(() => props.minRaise, (v) => {
  if (betAmount.value < v) betAmount.value = v
})

watch([() => props.canBet, () => props.canRaise], ([b, r]) => {
  if (b || r) {
    showSlider.value = true
    if (b) betAmount.value = props.minRaise
    else betAmount.value = props.currentBet + props.minRaise
  } else {
    showSlider.value = false
  }
}, { immediate: true })

function doAction(type: string, amount?: number) {
  emit('action', { type, amount: amount ?? 0 })
}

function confirmBet() {
  emit('action', { type: props.canBet ? 'BET' : 'RAISE', amount: clampedBetAmount.value })
}

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
  <div
    class="action-panel rounded-t-xl p-3 space-y-2 transition-all"
    :class="isMyTurn ? 'opacity-100' : 'opacity-75'"
    style="background-color: var(--color-panel-bg); font-family: 'Press Start 2P', monospace;"
  >
    <!-- Timer -->
    <div
      v-if="timeLeftSec > 0"
      class="text-center text-sm font-bold"
      :class="timeLeftSec <= 10 ? 'text-red-400' : ''"
      style="color: var(--color-text-light);"
    >
      {{ timerDisplay }}
    </div>

    <!-- Waiting text -->
    <div v-if="!isMyTurn" class="text-center text-xs" style="color: var(--color-text-muted); font-size: 9px;">
      等待对手操作...
    </div>
    <div v-else class="text-center text-xs" style="color: var(--color-gold); font-size: 9px;">
      轮到你行动!
    </div>

    <!-- Action buttons -->
    <div v-if="isMyTurn" class="flex gap-2">
      <!-- Fold -->
      <button
        data-test="btn-fold"
        class="action-btn action-fold"
        @click="doAction('FOLD')"
      >
        弃牌
      </button>

      <!-- Check or Call -->
      <button
        v-if="canCheck"
        data-test="btn-check"
        class="action-btn action-check"
        @click="doAction('CHECK')"
      >
        过牌
      </button>
      <button
        v-else-if="canCall"
        data-test="btn-call"
        class="action-btn action-call"
        @click="doAction('CALL')"
      >
        跟注 {{ callAmount }}
      </button>
    </div>

    <!-- Bet/Raise slider -->
    <div v-if="isMyTurn && showSlider" class="space-y-1">
      <div class="flex items-center gap-2">
        <input
          type="range"
          :min="betMin"
          :max="betMax"
          v-model.number="clampedBetAmount"
          class="flex-1 accent-yellow-400"
        />
        <span class="text-sm font-bold text-white min-w-[40px] text-right">{{ clampedBetAmount }}</span>
      </div>
      <button
        data-test="btn-confirm-bet"
        class="action-btn action-raise"
        @click="confirmBet"
      >
        {{ canBet ? `加注 ${clampedBetAmount}` : `加注到 ${clampedBetAmount}` }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.action-btn {
  font-family: 'Press Start 2P', monospace;
  font-size: 8px;
  flex: 1;
  padding: 12px 8px;
  border-radius: 8px;
  font-weight: bold;
  transition: all 0.1s;
  cursor: pointer;
  letter-spacing: 1px;
  border: 2px solid;
}

.action-btn:active { transform: scale(0.97); }

.action-fold {
  background: var(--color-accent);
  border-color: #802020;
  color: var(--color-text-light);
  box-shadow: 0 2px 0 #802020;
}

.action-check, .action-call {
  background: var(--color-primary);
  border-color: var(--color-button-shadow);
  color: var(--color-text);
  box-shadow: 0 2px 0 var(--color-button-shadow);
}

.action-raise {
  width: 100%;
  padding: 10px 8px;
  background: var(--color-gold);
  border-color: #a08020;
  color: #382818;
  box-shadow: 0 2px 0 #a08020;
  font-size: 9px;
}
</style>
