<script setup lang="ts">
defineProps<{
  playerId: string
  nickname: string
}>()

defineEmits<{
  spectate: []
}>()
</script>

<template>
  <div class="bust-overlay">
    <div class="bust-modal">
      <div class="bust-emoji">💸</div>
      <div class="bust-title">筹码归零！</div>
      <div class="bust-desc">本局你已无法继续下注。对局会继续进行。</div>
      <button class="bust-btn" @click="$emit('spectate')">👀 观战</button>
      <div class="bust-hint">本局结束后可在等待界面借筹码继续参加下一局</div>
    </div>
  </div>
</template>

<style scoped>
.bust-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.7);
  padding: 0 24px;
}

.bust-modal {
  width: 100%;
  max-width: 320px;
  border-radius: 16px;
  padding: 24px;
  text-align: center;
  background: var(--color-surface-light);
  font-family: 'Press Start 2P', monospace;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bust-emoji {
  font-size: 36px;
}

.bust-title {
  font-size: clamp(12px, 3vh, 16px);
  font-weight: bold;
  color: var(--color-gold);
}

.bust-desc {
  font-size: clamp(8px, 2vh, 11px);
  color: var(--color-text-muted);
}

.bust-btn {
  width: 100%;
  padding: 12px;
  border-radius: 10px;
  font-family: 'Press Start 2P', monospace;
  font-weight: bold;
  font-size: clamp(9px, 2.3vh, 12px);
  background: var(--color-primary);
  color: #fff;
  border: 2px solid var(--color-button-shadow);
  cursor: pointer;
  transition: transform 0.1s;
}

.bust-btn:active {
  transform: scale(0.95);
}

.bust-hint {
  font-size: clamp(7px, 1.6vh, 9px);
  color: var(--color-text-muted);
}
</style>
