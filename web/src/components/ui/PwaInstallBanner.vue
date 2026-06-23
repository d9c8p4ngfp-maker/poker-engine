<script setup lang="ts">
import { ref, onMounted } from 'vue'

const show = ref(false)
onMounted(() => {
  const isStandalone = window.matchMedia('(display-mode: standalone)').matches
    || (window.navigator as any).standalone === true
  const dismissed = localStorage.getItem('pwa-dismissed')
  if (!isStandalone && !dismissed && /iPhone|iPad/.test(navigator.userAgent)) {
    show.value = true
  }
})
function dismiss() {
  show.value = false
  localStorage.setItem('pwa-dismissed', '1')
}
</script>
<template>
  <Transition name="slide">
    <div v-if="show" class="pwa-banner">
      <span class="pwa-text">📱 点击 Safari <strong>分享按钮 ⬆</strong> → "添加到主屏幕"，可获得全屏体验</span>
      <button @click="dismiss" class="pwa-close">✕</button>
    </div>
  </Transition>
</template>
<style scoped>
.pwa-banner {
  position: fixed; bottom: 0; left: 0; right: 0; z-index: 9999;
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 16px; gap: 12px;
  background: rgba(40, 20, 8, 0.95); color: var(--color-gold);
  font-family: 'Press Start 2P', monospace; font-size: clamp(6px, 1.5vh, 9px);
  border-top: 2px solid var(--color-gold); line-height: 1.6;
}
.pwa-text { flex: 1; }
.pwa-text strong { color: #fff; }
.pwa-close { background: none; border: none; color: var(--color-gold);
  font-size: 14px; cursor: pointer; padding: 4px 8px; flex-shrink: 0; }
.slide-enter-active, .slide-leave-active { transition: transform 0.3s ease; }
.slide-enter-from, .slide-leave-to { transform: translateY(100%); }
</style>
