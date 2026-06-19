<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  winners: { playerId: string; nickname: string; handName: string; amount: number }[]
  leaderboard: { playerId: string; nickname: string; chips: number; borrowCount?: number; borrowed?: number }[]
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
  <div class="absolute inset-0 z-20 flex items-center justify-center bg-black/70 px-4">
    <div
      class="w-full max-w-xs rounded-xl p-6 space-y-4 text-center"
      style="background-color: var(--color-surface-light)"
    >
      <div class="text-4xl">🏆</div>
      <div class="text-xl font-bold" style="color: var(--color-gold)">比赛结束</div>

      <!-- Last hand winners -->
      <div v-if="winners && winners.length" class="space-y-1">
        <div class="text-xs" style="color: var(--color-text-muted)">最后一局赢家</div>
        <div v-for="w in winners" :key="w.playerId" class="text-sm font-bold" style="color: var(--color-primary)">
          {{ w.nickname }} +{{ w.amount }} ({{ w.handName }})
        </div>
      </div>

      <div class="h-px" style="background-color: var(--color-text-muted)"></div>

      <!-- Leaderboard -->
      <div class="space-y-2">
        <div class="text-xs font-bold" style="color: var(--color-text-muted)">最终排名</div>
        <div
          v-for="(entry, i) in leaderboard"
          :key="entry.playerId"
          class="flex items-center gap-3 p-2 rounded-lg"
          :style="{
            backgroundColor: i === 0 ? 'rgba(255,215,0,0.15)' : 'var(--color-surface)',
            border: i === 0 ? '1px solid var(--color-gold)' : '1px solid transparent'
          }"
        >
          <span class="text-lg w-8">{{ getMedal(i) }}</span>
          <span class="flex-1 text-left text-sm font-bold text-white">
            {{ entry.nickname }}
            <span v-if="bustedPlayerIds.includes(entry.playerId)" class="text-xs ml-1" style="color: var(--color-accent)">💀</span>
          </span>
          <span class="text-xs font-mono" :style="{ color: (entry.netChips ?? 0) >= 0 ? 'var(--color-gold)' : 'var(--color-accent)' }">
            {{ entry.netChips != null ? (entry.netChips >= 0 ? '+'+entry.netChips : entry.netChips) : entry.chips }}
          </span>
        </div>
      </div>

      <button
        class="w-full py-3 rounded-lg font-bold text-white transition active:scale-95"
        style="background-color: var(--color-primary)"
        @click="$emit('back-to-lobby')"
      >
        返回房间
      </button>
    </div>
  </div>
</template>
