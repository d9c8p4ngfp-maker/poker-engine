<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  card: string | null
  faceUp: boolean
  size?: 'sm' | 'md' | 'lg'
}>(), {
  size: 'md',
})

const rank = computed(() => props.card?.charAt(0) ?? '')
const suitChar = computed(() => props.card?.charAt(1) ?? '')

const suitSymbol = computed(() => {
  const map: Record<string, string> = { h: '♥', d: '♦', c: '♣', s: '♠' }
  return map[suitChar.value] || ''
})

const isRed = computed(() => suitChar.value === 'h' || suitChar.value === 'd')

const sizeClass = computed(() => props.size)
</script>

<template>
  <div
    class="card"
    :class="[sizeClass]"
    :style="{ imageRendering: 'pixelated' }"
  >
    <!-- Card back -->
    <div v-if="!card || !faceUp" class="card-back" data-test="card-back">
      <div class="card-back-emblem">♠♥♦♣</div>
    </div>

    <!-- Card face -->
    <div v-else class="card-face" :class="{ red: isRed, black: !isRed }" data-test="card-face">
      <div class="card-corner top-left">
        <span class="card-rank">{{ rank }}</span>
        <span class="card-suit-s">{{ suitSymbol }}</span>
      </div>
      <div class="card-center">
        <span class="card-suit-lg">{{ suitSymbol }}</span>
      </div>
      <div class="card-corner bottom-right">
        <span class="card-rank">{{ rank }}</span>
        <span class="card-suit-s">{{ suitSymbol }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  user-select: none;
  transition: transform 0.3s ease;
}

/* Sizes */
.card.sm { width: 32px; height: 44px; }
.card.md { width: 40px; height: 56px; }
.card.lg { width: 56px; height: 80px; }

/* Card face */
.card-face {
  position: relative;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #fffef8 0%, #f5edd8 100%);
  border: 1.5px solid #c0a878;
  border-radius: 4px;
  box-shadow: 1px 2px 3px rgba(0, 0, 0, 0.25);
  animation: cardFlip 0.4s ease;
}
.card-face.red { color: #c0392b; }
.card-face.black { color: #1a1a1a; }

.card-corner {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1;
}
.card-corner.top-left { top: 2px; left: 3px; }
.card-corner.bottom-right { bottom: 2px; right: 3px; transform: rotate(180deg); }

.card-rank {
  font-family: serif;
  font-size: clamp(8px, 2vh, 12px);
  font-weight: bold;
}
.card-suit-s {
  font-size: clamp(6px, 1.5vh, 9px);
}
.card-center {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-suit-lg {
  font-size: clamp(14px, 3.5vh, 22px);
}

/* Card back */
.card-back {
  width: 100%;
  height: 100%;
  background: #8b2020;
  border: 1.5px solid #a83030;
  border-radius: 4px;
  box-shadow: 1px 2px 4px rgba(0, 0, 0, 0.4);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-back::before {
  content: '';
  position: absolute;
  inset: 3px;
  border: 1.5px solid rgba(255, 200, 80, 0.35);
  border-radius: 3px;
}
.card-back::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    repeating-linear-gradient(45deg, transparent, transparent 3px, rgba(0,0,0,0.12) 3px, rgba(0,0,0,0.12) 4px),
    repeating-linear-gradient(-45deg, transparent, transparent 3px, rgba(0,0,0,0.12) 3px, rgba(0,0,0,0.12) 4px);
}
.card-back-emblem {
  position: relative;
  z-index: 1;
  font-size: 0.55em;
  color: rgba(255, 200, 80, 0.4);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

/* Animations */
@keyframes cardFlip {
  0% { transform: rotateY(90deg) scale(0.8); opacity: 0; }
  100% { transform: rotateY(0) scale(1); opacity: 1; }
}
</style>
