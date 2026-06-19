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
  <div class="absolute inset-0 z-20 flex items-center justify-center bg-black/70 px-6">
    <div
      class="w-full max-w-xs rounded-xl p-6 space-y-4 text-center"
      style="background-color: var(--color-surface-light)"
    >
      <div class="text-4xl">💸</div>
      <div class="text-lg font-bold" style="color: var(--color-gold)">筹码归零！</div>
      <div class="text-sm" style="color: var(--color-text-muted)">
        本局你已无法继续下注。对局会继续进行。
      </div>

      <button
        class="w-full py-3 rounded-lg font-bold text-sm text-white transition active:scale-95"
        style="background-color: var(--color-primary)"
        @click="$emit('spectate')"
      >
        👀 观战
      </button>

      <div class="text-xs" style="color: var(--color-text-muted)">
        本局结束后可在等待界面借筹码继续参加下一局
      </div>
    </div>
  </div>
</template>
