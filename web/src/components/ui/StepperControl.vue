<script setup lang="ts">
const props = defineProps<{ modelValue:number; min:number; max:number; step?:number; label:string }>()
const emit = defineEmits<{ 'update:modelValue':[value:number] }>()

function adjust(d:number) {
  const s = props.step || 1
  const n = Math.max(props.min, Math.min(props.max, Math.round((props.modelValue + d) / s) * s))
  emit('update:modelValue', n)
}

function onSlide(e: Event) {
  const val = Number((e.target as HTMLInputElement).value)
  const s = props.step || 1
  emit('update:modelValue', Math.round(val / s) * s)
}
</script>
<template>
  <div class="s" data-test="stepper">
    <div class="sh"><span class="sl">{{ label }}</span><span class="sv">{{ modelValue }}</span></div>
    <div class="sc">
      <button data-test="stepper-minus" class="sb" @click="adjust(-(step||1))" :disabled="modelValue<=min">−</button>
      <div class="track-wrap">
        <input type="range" class="sr" :min="min" :max="max" :step="step||1"
          :value="modelValue" @input="onSlide"
          :style="{ '--pct': ((modelValue - min) / (max - min) * 100) + '%' }" />
      </div>
      <button data-test="stepper-plus" class="sb" @click="adjust(step||1)" :disabled="modelValue>=max">+</button>
    </div>
  </div>
</template>
<style scoped>
.s{display:flex;flex-direction:column;gap:5px}
.sh{display:flex;justify-content:space-between;align-items:center}
.sl{font-size:clamp(10px,2.5vh,13px);color:rgba(224,176,48,0.55)}
.sv{font-size:clamp(11px,2.7vh,14px);font-weight:bold;color:var(--color-gold)}
.sc{display:flex;align-items:center;gap:8px}
.sb{font-family:'Press Start 2P',monospace;font-size:clamp(12px,3vh,16px);width:clamp(30px,7vw,40px);height:clamp(30px,7vw,40px);
  display:flex;align-items:center;justify-content:center;border:2px solid var(--color-button-shadow);
  border-radius:6px;background:rgba(140,96,56,0.92);color:var(--color-text-light);cursor:pointer;transition:all 0.1s;padding:0;line-height:1}
.sb:active:not(:disabled){transform:scale(.92)}
.sb:disabled{opacity:.35;cursor:default}
.track-wrap{flex:1;position:relative;height:clamp(28px,6vh,40px);display:flex;align-items:center}
.sr{-webkit-appearance:none;appearance:none;width:100%;height:clamp(8px,2vh,12px);
  background:linear-gradient(to right, var(--color-primary) var(--pct, 0%), var(--color-input-bg) var(--pct, 0%));
  border-radius:5px;border:1px solid var(--color-border);outline:none;cursor:pointer;margin:0}
.sr::-webkit-slider-thumb{-webkit-appearance:none;appearance:none;
  width:clamp(20px,4.5vw,28px);height:clamp(20px,4.5vw,28px);
  border-radius:50%;background:var(--color-gold);border:2px solid var(--color-button-shadow);
  box-shadow:0 2px 4px rgba(0,0,0,0.3);cursor:grab}
.sr::-webkit-slider-thumb:active{cursor:grabbing;transform:scale(1.15)}
.sr::-moz-range-thumb{width:clamp(20px,4.5vw,28px);height:clamp(20px,4.5vw,28px);
  border-radius:50%;background:var(--color-gold);border:2px solid var(--color-button-shadow);
  box-shadow:0 2px 4px rgba(0,0,0,0.3);cursor:grab}
.sr::-moz-range-track{height:clamp(8px,2vh,12px);background:var(--color-input-bg);
  border-radius:5px;border:1px solid var(--color-border)}
.sr::-moz-range-progress{height:100%;background:var(--color-primary);border-radius:5px}
</style>
