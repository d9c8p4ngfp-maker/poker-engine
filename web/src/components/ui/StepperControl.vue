<script setup lang="ts">
defineProps<{
  modelValue: number
  min: number
  max: number
  step?: number
  label: string
}>()

const emit = defineEmits<{ 'update:modelValue': [value: number] }>()

function adjust(delta: number, min: number, max: number, step: number, current: number) {
  const next = current + delta
  const clamped = Math.max(min, Math.min(max, next))
  const stepped = Math.round(clamped / step) * step
  emit('update:modelValue', Math.max(min, Math.min(max, stepped)))
}
</script>

<template>
  <div class="stepper" data-test="stepper">
    <div class="stepper-header">
      <label class="stepper-label">{{ label }}</label>
      <span class="stepper-value">{{ modelValue }}</span>
    </div>
    <div class="stepper-controls">
      <button data-test="stepper-minus" class="stepper-btn" @click="adjust(-(step || 1), min, max, step || 1, modelValue)" :disabled="modelValue <= min">−</button>
      <div class="stepper-bar-track">
        <div class="stepper-bar-fill" :style="{ width: ((modelValue - min) / (max - min) * 100) + '%' }"></div>
      </div>
      <button data-test="stepper-plus" class="stepper-btn" @click="adjust(step || 1, min, max, step || 1, modelValue)" :disabled="modelValue >= max">+</button>
    </div>
  </div>
</template>

<style scoped>
.stepper { display:flex; flex-direction:column; gap:6px; }
.stepper-header { display:flex; justify-content:space-between; align-items:center; }
.stepper-label { font-size:13px; color:rgba(224,176,48,0.6); }
.stepper-value { font-size:14px; font-weight:bold; color:var(--color-gold); }
.stepper-controls { display:flex; align-items:center; gap:10px; }
.stepper-btn { font-family:'Press Start 2P',monospace; font-size:16px; width:40px; height:40px; display:flex; align-items:center; justify-content:center; border:2px solid var(--color-button-shadow); border-radius:6px; background:rgba(140,96,56,0.92); color:var(--color-text-light); cursor:pointer; transition:all 0.1s; padding:0; line-height:1; }
.stepper-btn:active:not(:disabled) { transform:scale(0.92); }
.stepper-btn:disabled { opacity:0.35; cursor:default; }
.stepper-bar-track { flex:1; height:12px; background:var(--color-input-bg); border-radius:6px; overflow:hidden; border:1px solid var(--color-border); }
.stepper-bar-fill { height:100%; background:var(--color-primary); border-radius:6px; transition:width 0.2s; }
</style>
