<script setup lang="ts">
defineProps<{ modelValue:number; min:number; max:number; step?:number; label:string }>()
const emit = defineEmits<{ 'update:modelValue':[value:number] }>()
function adjust(d:number, min:number, max:number, s:number, v:number) {
  const n = Math.max(min, Math.min(max, Math.round((v+d)/s)*s))
  emit('update:modelValue', n)
}
</script>
<template>
  <div class="s" data-test="stepper">
    <div class="sh"><span class="sl">{{ label }}</span><span class="sv">{{ modelValue }}</span></div>
    <div class="sc">
      <button data-test="stepper-minus" class="sb" @click="adjust(-(step||1),min,max,step||1,modelValue)" :disabled="modelValue<=min">−</button>
      <div class="st"><div class="sf" :style="{width:((modelValue-min)/(max-min)*100)+'%'}"></div></div>
      <button data-test="stepper-plus" class="sb" @click="adjust(step||1,min,max,step||1,modelValue)" :disabled="modelValue>=max">+</button>
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
.st{flex:1;height:clamp(8px,2vh,12px);background:var(--color-input-bg);border-radius:5px;overflow:hidden;border:1px solid var(--color-border)}
.sf{height:100%;background:var(--color-primary);border-radius:5px;transition:width .2s}
</style>
