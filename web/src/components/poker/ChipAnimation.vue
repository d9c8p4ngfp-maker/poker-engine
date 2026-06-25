<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  animations: {
    id: string
    fromX: number; fromY: number
    toX: number; toY: number
    amount: number
    delay: number
  }[]
}>()

const activeAnimations = ref<typeof props.animations>([])

watch(() => props.animations, (newAnims) => {
  if (!newAnims?.length) return
  newAnims.forEach((anim) => {
    setTimeout(() => {
      activeAnimations.value.push(anim)
      setTimeout(() => {
        activeAnimations.value = activeAnimations.value.filter(a => a.id !== anim.id)
      }, 600)
    }, anim.delay)
  })
}, { deep: true })

function formatChips(n: number): string {
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}
</script>

<template>
  <div class="chip-animation-layer">
    <div
      v-for="anim in activeAnimations"
      :key="anim.id"
      class="flying-chip"
      :style="{
        '--from-x': anim.fromX + 'px',
        '--from-y': anim.fromY + 'px',
        '--to-x': anim.toX + 'px',
        '--to-y': anim.toY + 'px',
      }"
    >
      <span class="chip-icon">🪙</span>
      <span class="chip-amount">{{ formatChips(anim.amount) }}</span>
    </div>
  </div>
</template>

<style scoped>
.chip-animation-layer {
  position: absolute; inset: 0;
  pointer-events: none; z-index: 4;
}
.flying-chip {
  position: absolute;
  left: var(--from-x); top: var(--from-y);
  display: flex; align-items: center; gap: 2px;
  animation: chip-fly 0.5s ease-in-out forwards;
}
@keyframes chip-fly {
  0% {
    opacity: 1;
    transform: translate(0, 0) scale(1);
  }
  80% {
    opacity: 1;
    transform: translate(
      calc(var(--to-x) - var(--from-x)),
      calc(var(--to-y) - var(--from-y))
    ) scale(1.2);
  }
  100% {
    opacity: 0;
    transform: translate(
      calc(var(--to-x) - var(--from-x)),
      calc(var(--to-y) - var(--from-y))
    ) scale(0.8);
  }
}
.chip-icon { font-size: 20px; }
.chip-amount {
  font-family: 'Press Start 2P', monospace;
  font-size: 10px; font-weight: bold;
  color: var(--color-gold);
  text-shadow: 0 1px 2px rgba(0,0,0,0.5);
}
</style>
