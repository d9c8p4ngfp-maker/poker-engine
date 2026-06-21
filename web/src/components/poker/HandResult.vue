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
  <div class="absolute inset-0 z-10 flex items-center justify-center bg-black/60 px-4">
    <div
      class="w-full max-w-xs rounded-xl p-6 space-y-4 text-center"
      style="background-color: var(--color-surface-light)"
    >
      <div class="text-xl">🏆</div>
      <div class="text-lg font-bold text-white">结果</div>

      <div v-for="w in winners" :key="w.playerId" class="space-y-1">
        <div class="text-sm font-bold" style="color: var(--color-gold)">
          {{ w.nickname ?? w.playerId }}
        </div>
        <div class="text-xs" style="color: var(--color-text-muted)">
          {{ w.handName }}
        </div>
        <div class="text-sm font-bold" style="color: var(--color-gold)">
          +{{ w.amount }}
        </div>
      </div>

      <button
        v-if="isOwner"
        class="w-full py-3 rounded-lg font-bold text-white transition active:scale-95"
        style="background-color: var(--color-primary)"
        @click="$emit('next-hand')"
      >
        下一局
      </button>

      <div v-else class="text-xs" style="color: var(--color-text-muted)">
        等待房主开始下一局...
      </div>
    </div>
  </div>
</template>
