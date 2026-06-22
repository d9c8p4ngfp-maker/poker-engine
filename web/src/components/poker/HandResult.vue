<script setup lang="ts">
defineProps<{
  winners: { playerId: string; nickname?: string; handName: string; amount: number }[]
  isOwner: boolean
}>()

defineEmits<{
  'next-hand': []
}>()
</script>

<template>
  <div class="result-overlay">
    <div class="result-modal">
      <div class="result-trophy">🏆</div>
      <div class="result-title">结果</div>

      <div v-for="w in winners" :key="w.playerId" class="winner-row">
        <div class="winner-name">{{ w.nickname ?? w.playerId }}</div>
        <div class="winner-hand">{{ w.handName }}</div>
        <div class="winner-amount">+{{ w.amount }}</div>
      </div>

      <button v-if="isOwner" class="result-btn" @click="$emit('next-hand')">
        下一局
      </button>
      <div v-else class="result-wait">等待房主开始下一局...</div>
    </div>
  </div>
</template>

<style scoped>
.result-overlay {
  position: absolute; inset: 0; z-index: 10;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0, 0, 0, 0.6); padding: 0 16px;
}
.result-modal {
  width: clamp(280px, 50vw, 480px);
  background: var(--color-panel-bg);
  border: 2px solid var(--color-button-shadow);
  border-radius: 14px;
  padding: clamp(12px, 3vh, 24px);
  text-align: center;
  font-family: 'Press Start 2P', monospace;
}
.result-trophy { font-size: 32px; }
.result-title {
  font-size: clamp(11px, 2.5vh, 15px);
  font-weight: bold; color: var(--color-text-light);
  margin: 8px 0 16px;
}
.winner-row { margin-bottom: 12px; }
.winner-name {
  font-size: clamp(9px, 2.3vh, 13px);
  font-weight: bold; color: var(--color-gold);
}
.winner-hand {
  font-size: clamp(7px, 1.8vh, 10px);
  color: var(--color-text-muted);
  margin: 4px 0;
}
.winner-amount {
  font-size: clamp(9px, 2.3vh, 13px);
  font-weight: bold; color: var(--color-gold);
}
.result-btn {
  width: 100%; padding: 12px 0; border-radius: 10px; font-weight: bold;
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(9px, 2.3vh, 12px);
  background: var(--color-primary); color: var(--color-text);
  border: 2px solid var(--color-button-shadow);
  box-shadow: 0 2px 0 var(--color-button-shadow);
  cursor: pointer; transition: transform 0.1s;
  margin-top: 8px;
}
.result-btn:active { transform: scale(0.97); }
.result-wait {
  font-size: clamp(7px, 1.8vh, 10px);
  color: var(--color-text-muted);
  margin-top: 8px;
}
</style>
