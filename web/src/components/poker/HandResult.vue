<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  winners: { playerId: string; nickname?: string; handName: string; amount: number }[]
  isOwner: boolean
  players: { playerId: string; nickname: string; chips: number }[]
  readyPlayers: string[]
  totalActive: number
  allReady: boolean
  myPlayerId: string
  minPlayers: number
  hasPendingGameOver: boolean
}>()

const emit = defineEmits<{
  'next-hand': []
  'ready': []
  'show-game-over': []
}>()

const amIReady = computed(() => props.readyPlayers.includes(props.myPlayerId))
const readyCount = computed(() => props.readyPlayers.length)
const canStart = computed(() => props.totalActive >= props.minPlayers)
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

      <template v-if="hasPendingGameOver || !canStart">
        <div class="result-outro">本局最终结算</div>
        <button class="result-btn result-next-btn" @click="$emit('show-game-over')">
          查看最终排名
        </button>
      </template>
      <template v-else>
        <div class="ready-divider"></div>

        <!-- Ready panel -->
        <div class="ready-panel">
          <div class="ready-title">准备状态 ({{ readyCount }}/{{ totalActive }})</div>
          <div class="ready-grid">
            <div
              v-for="p in players"
              :key="p.playerId"
              class="ready-player"
              :class="{ 'is-ready': readyPlayers.includes(p.playerId) }"
            >
              <span class="ready-icon">{{ readyPlayers.includes(p.playerId) ? '✅' : '⏳' }}</span>
              <span class="ready-name">{{ p.nickname }}</span>
            </div>
          </div>
        </div>

        <!-- Action buttons -->
        <button
          v-if="!amIReady"
          class="result-btn ready-btn"
          @click="$emit('ready')"
        >
          准备
        </button>
        <div v-else class="result-wait">
          ✅ 已准备 — 等待房主开始...
        </div>
        <button
          v-if="isOwner"
          class="result-btn"
          :class="{ 'btn-disabled': !allReady }"
          :disabled="!allReady"
          @click="$emit('next-hand')"
        >
          {{ allReady ? '下一局' : `等待准备 (${readyCount}/${totalActive})` }}
        </button>
      </template>
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
  width: clamp(300px, 55vw, 500px);
  max-height: 80vh; overflow-y: auto;
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
  color: var(--color-text-muted); margin: 4px 0;
}
.winner-amount {
  font-size: clamp(9px, 2.3vh, 13px);
  font-weight: bold; color: var(--color-gold);
}

/* Ready panel */
.ready-divider {
  height: 1px;
  background: var(--color-text-muted);
  opacity: 0.3;
  margin: 16px 0;
}
.ready-panel { margin-bottom: 16px; }
.ready-title {
  font-size: clamp(8px, 2vh, 11px);
  font-weight: bold;
  color: var(--color-text-muted);
  margin-bottom: 10px;
}
.ready-grid {
  display: flex; flex-wrap: wrap; gap: 8px;
  justify-content: center;
}
.ready-player {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 10px;
  border-radius: 8px;
  background: var(--color-input-bg);
  border: 1px solid var(--color-border);
  transition: all 0.3s ease;
}
.ready-player.is-ready {
  background: rgba(76, 175, 80, 0.15);
  border-color: #4CAF50;
}
.ready-icon { font-size: 14px; }
.ready-name {
  font-size: clamp(7px, 1.5vh, 9px);
  color: var(--color-text-light);
}

/* Buttons */
.result-btn {
  width: 100%; padding: 12px 0; border-radius: 10px; font-weight: bold;
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(9px, 2.3vh, 12px);
  background: var(--color-primary); color: var(--color-text);
  border: 2px solid var(--color-button-shadow);
  box-shadow: 0 2px 0 var(--color-button-shadow);
  cursor: pointer; transition: all 0.2s;
  margin-top: 8px;
}
.result-btn:active { transform: scale(0.97); }
.result-btn.btn-disabled {
  opacity: 0.5; cursor: not-allowed;
}
.ready-btn {
  background: #4CAF50;
  animation: pulse-ready 2s ease-in-out infinite;
}
@keyframes pulse-ready {
  0%, 100% { box-shadow: 0 2px 0 var(--color-button-shadow); }
  50% { box-shadow: 0 2px 12px rgba(76, 175, 80, 0.4); }
}
.result-wait {
  font-size: clamp(7px, 1.8vh, 10px);
  color: var(--color-text-muted); margin-top: 12px;
  padding: 8px 0;
}
.result-cant-start {
  font-size: clamp(8px, 2vh, 11px);
  color: #ff9800; margin-top: 16px;
  padding: 12px 0;
}
</style>
