<script setup lang="ts">
const props = defineProps<{
  winners: { playerId: string; nickname?: string; handName: string; amount: number }[]
  leaderboard: { playerId: string; nickname: string; chips: number; borrowCount?: number; borrowed?: number; netChips?: number }[]
  bustedPlayerIds: string[]
}>()

defineEmits<{
  'back-to-lobby': []
}>()

const trophy = ['🥇', '🥈', '🥉', '4️⃣', '5️⃣', '6️⃣', '7️⃣', '8️⃣']

function getMedal(index: number) {
  return trophy[index] || `${index + 1}`
}
</script>

<template>
  <div class="gameover-overlay">
    <div class="gameover-modal">
      <div class="gameover-trophy">🏆</div>
      <div class="gameover-title">比赛结束</div>

      <div v-if="winners && winners.length" class="last-winners">
        <div class="lw-label">最后一局赢家</div>
        <div v-for="w in winners" :key="w.playerId" class="lw-row">
          {{ w.nickname ?? w.playerId }} +{{ w.amount }} ({{ w.handName }})
        </div>
      </div>

      <div class="divider"></div>

      <div class="lb-label">最终排名</div>
      <div
        v-for="(entry, i) in leaderboard"
        :key="entry.playerId"
        class="lb-row"
        :class="{ 'first': i === 0 }"
      >
        <span class="lb-medal">{{ getMedal(i) }}</span>
        <span class="lb-name">
          {{ entry.nickname }}
          <span v-if="bustedPlayerIds.includes(entry.playerId)" class="lb-skull">💀</span>
        </span>
        <span class="lb-chips" :class="{ 'pos': (entry.netChips ?? 0) >= 0, 'neg': (entry.netChips ?? 0) < 0 }">
          {{ entry.netChips != null ? (entry.netChips >= 0 ? '+' + entry.netChips : entry.netChips) : entry.chips }}
        </span>
      </div>

      <button class="gameover-btn" @click="$emit('back-to-lobby')">
        返回房间
      </button>
    </div>
  </div>
</template>

<style scoped>
.gameover-overlay {
  position: absolute; inset: 0; z-index: 20;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0, 0, 0, 0.7); padding: 0 16px;
}
.gameover-modal {
  width: clamp(300px, 55vw, 520px);
  background: var(--color-panel-bg);
  border: 2px solid var(--color-button-shadow);
  border-radius: 14px;
  padding: clamp(12px, 3vh, 24px);
  text-align: center;
  font-family: 'Press Start 2P', monospace;
}
.gameover-trophy { font-size: 48px; }
.gameover-title {
  font-size: clamp(13px, 3vh, 18px);
  font-weight: bold; color: var(--color-gold);
  margin: 8px 0 16px;
}
.last-winners {
  margin-bottom: 12px;
}
.lw-label {
  font-size: clamp(7px, 1.8vh, 10px);
  color: var(--color-text-muted);
  margin-bottom: 6px;
}
.lw-row {
  font-size: clamp(8px, 2vh, 11px);
  font-weight: bold; color: var(--color-primary);
  margin-bottom: 2px;
}
.divider {
  height: 1px; background: var(--color-text-muted);
  opacity: 0.3; margin: 12px 0;
}
.lb-label {
  font-size: clamp(7px, 1.8vh, 10px);
  font-weight: bold; color: var(--color-text-muted);
  margin-bottom: 8px;
}
.lb-row {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 12px; border-radius: 10px;
  background: var(--color-surface);
  border: 1px solid transparent;
  margin-bottom: 4px;
}
.lb-row.first {
  background: rgba(255, 215, 0, 0.12);
  border-color: var(--color-gold);
}
.lb-medal { font-size: 18px; width: 32px; }
.lb-name {
  flex: 1; text-align: left;
  font-size: clamp(8px, 2vh, 11px);
  font-weight: bold; color: var(--color-text-light);
}
.lb-skull { font-size: 10px; margin-left: 4px; color: var(--color-accent); }
.lb-chips {
  font-size: clamp(7px, 1.8vh, 10px);
}
.lb-chips.pos { color: var(--color-gold); }
.lb-chips.neg { color: var(--color-accent); }
.gameover-btn {
  width: 100%; padding: 12px 0; border-radius: 10px; font-weight: bold;
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(9px, 2.3vh, 12px);
  background: var(--color-primary); color: var(--color-text);
  border: 2px solid var(--color-button-shadow);
  box-shadow: 0 2px 0 var(--color-button-shadow);
  cursor: pointer; transition: transform 0.1s;
  margin-top: 16px;
}
.gameover-btn:active { transform: scale(0.97); }
</style>
