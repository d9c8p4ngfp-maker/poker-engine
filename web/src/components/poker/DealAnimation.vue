<script setup lang="ts">
import { ref, onMounted } from 'vue'

const props = defineProps<{
  playerPositions: { playerId: string; x: number; y: number }[]
  dealerPosition: { x: number; y: number }
  cardCount: number
}>()

const emit = defineEmits<{ 'complete': [] }>()

const cards = ref<{ id: number; targetX: number; targetY: number; delay: number; visible: boolean }[]>([])

onMounted(() => {
  let id = 0
  for (let round = 0; round < props.cardCount; round++) {
    for (const pos of props.playerPositions) {
      cards.value.push({
        id: id++,
        targetX: pos.x,
        targetY: pos.y,
        delay: id * 100,
        visible: false
      })
    }
  }

  cards.value.forEach((card, i) => {
    setTimeout(() => { card.visible = true }, card.delay)
  })

  const totalTime = cards.value.length * 100 + 500
  setTimeout(() => emit('complete'), totalTime)
})
</script>

<template>
  <div class="deal-layer">
    <div
      v-for="card in cards"
      :key="card.id"
      class="flying-card"
      :class="{ 'fly': card.visible }"
      :style="{
        '--start-x': dealerPosition.x + 'px',
        '--start-y': dealerPosition.y + 'px',
        '--end-x': card.targetX + 'px',
        '--end-y': card.targetY + 'px',
        'animation-delay': card.delay + 'ms'
      }"
    >
      <div class="card-back-mini">🂠</div>
    </div>
  </div>
</template>

<style scoped>
.deal-layer {
  position: absolute; inset: 0;
  pointer-events: none; z-index: 5;
}
.flying-card {
  position: absolute;
  left: var(--start-x); top: var(--start-y);
  width: 28px; height: 40px;
  opacity: 0;
  transition: none;
}
.flying-card.fly {
  animation: deal-fly 0.4s ease-out forwards;
}
@keyframes deal-fly {
  0% {
    opacity: 1;
    transform: translate(0, 0) rotate(0deg) scale(0.5);
  }
  100% {
    opacity: 1;
    transform: translate(
      calc(var(--end-x) - var(--start-x)),
      calc(var(--end-y) - var(--start-y))
    ) rotate(360deg) scale(1);
  }
}
.card-back-mini {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1a5276, #2980b9);
  border-radius: 4px;
  font-size: 20px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}
</style>
