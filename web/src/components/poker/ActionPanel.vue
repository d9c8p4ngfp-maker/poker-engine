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

// Auto-show slider when bet/raise becomes available (new round)
watch([() => props.canBet, () => props.canRaise], ([b, r]) => {
  if (b || r) {
    showSlider.value = true
    if (b) betAmount.value = props.minRaise
    else betAmount.value = props.callAmount + props.minRaise
  } else {
    showSlider.value = false
  }
}, { immediate: true })

function doAction(type: string, amount?: number) {
  emit('action', { type, amount: amount ?? 0 })
}

function confirmBet() {
  emit('action', { type: props.canBet ? 'BET' : 'RAISE', amount: betAmount.value })
}

const betMax = computed(() => props.myChips)

const timerDisplay = computed(() => {
  const s = Math.max(0, Math.floor(props.timeLeftSec))
  return props.timeLeftSec <= 10 ? `⚠️ ${s}s` : `${s}s`
})
</script>

<template>
  <div
    class="action-panel rounded-t-xl p-3 space-y-2 transition-all"
    :class="isMyTurn ? 'opacity-100' : 'opacity-60'"
    style="background-color: var(--color-surface-light)"
  >
    <!-- Timer -->
    <div
      v-if="timeLeftSec > 0"
      class="text-center text-sm font-bold"
      :class="timeLeftSec <= 10 ? 'text-red-400' : 'text-gray-400'"
    >
      {{ timerDisplay }}
    </div>

    <!-- Waiting text -->
    <div v-if="!isMyTurn" class="text-center text-xs" style="color: var(--color-text-muted)">
      等待对手操作...
    </div>
    <div v-else class="text-center text-xs" style="color: var(--color-gold)">
      轮到你行动!
    </div>

    <!-- Action buttons -->
    <div v-if="isMyTurn" class="flex gap-2">
      <!-- Fold -->
      <button
        data-test="btn-fold"
        class="flex-1 py-3 rounded-lg font-bold text-sm text-white transition active:scale-95"
        style="background-color: var(--color-accent)"
        @click="doAction('FOLD')"
      >
        Fold
      </button>

      <!-- Check or Call -->
      <button
        v-if="canCheck"
        data-test="btn-check"
        class="flex-1 py-3 rounded-lg font-bold text-sm text-white transition active:scale-95"
        style="background-color: var(--color-primary)"
        @click="doAction('CHECK')"
      >
        Check
      </button>
      <button
        v-else-if="canCall"
        data-test="btn-call"
        class="flex-1 py-3 rounded-lg font-bold text-sm text-white transition active:scale-95"
        style="background-color: var(--color-primary)"
        @click="doAction('CALL')"
      >
        Call {{ callAmount }}
      </button>
    </div>

    <!-- Bet/Raise slider -->
    <div v-if="isMyTurn && showSlider" class="space-y-1">
      <div class="flex items-center gap-2">
        <input
          type="range"
          :min="Math.min(canBet ? minRaise : callAmount + minRaise, betMax)"
          :max="betMax"
          v-model.number="betAmount"
          class="flex-1 accent-yellow-400"
        />
        <span class="text-sm font-bold text-white min-w-[40px] text-right">{{ betAmount }}</span>
      </div>
      <button
        data-test="btn-confirm-bet"
        class="w-full py-2 rounded-lg font-bold text-sm text-black transition active:scale-95"
        style="background-color: var(--color-gold)"
        @click="confirmBet"
      >
        {{ canBet ? `Bet ${betAmount}` : `Raise to ${betAmount}` }}
      </button>
    </div>
  </div>
</template>
