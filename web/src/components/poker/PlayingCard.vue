<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  card: string | null
  faceUp: boolean
  size?: 'sm' | 'md' | 'lg'
}>(), {
  size: 'md',
})

const showFace = computed(() => props.card != null && props.faceUp)

const rank = computed(() => {
  if (!props.card) return ''
  return props.card.charAt(0)
})

const suit = computed(() => {
  if (!props.card) return ''
  return props.card.charAt(1)
})

const suitSymbol = computed(() => {
  const map: Record<string, string> = { h: '♥', d: '♦', c: '♣', s: '♠' }
  return map[suit.value] || ''
})

const isRed = computed(() => suit.value === 'h' || suit.value === 'd')

const sizeClass = computed(() => {
  const map = { sm: 'text-xs', md: 'text-base', lg: 'text-xl' }
  return map[props.size]
})
</script>

<template>
  <div
    class="card inline-flex items-center justify-center rounded-md font-bold select-none"
    :class="[
      size === 'sm' ? 'w-7 h-10' : size === 'lg' ? 'w-14 h-20' : 'w-10 h-14',
      size === 'sm' ? 'rounded-sm' : 'rounded-md',
    ]"
  >
    <div
      v-if="!showFace"
      data-test="card-back"
      class="w-full h-full rounded-md flex items-center justify-center"
      style="background-color: var(--color-card-back)"
    >
      <span class="text-xs opacity-30">♠♥♦♣</span>
    </div>
    <div
      v-else
      data-test="card-face"
      class="w-full h-full rounded-md flex flex-col items-center justify-center bg-white shadow-sm border border-gray-300"
      :class="[
        isRed ? 'text-red-600' : 'text-black',
        sizeClass,
      ]"
    >
      <span class="leading-tight">{{ rank }}</span>
      <span class="leading-tight">{{ suitSymbol }}</span>
    </div>
  </div>
</template>
