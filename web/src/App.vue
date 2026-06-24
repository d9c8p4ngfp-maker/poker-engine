<script setup lang="ts">
import { RouterView } from 'vue-router'
import PwaInstallBanner from './components/ui/PwaInstallBanner.vue'
</script>

<template>
  <div class="app-root">
    <!-- Background layer -->
    <div class="bg-layer"></div>
    <!-- CRT scanlines -->
    <div class="scanlines"></div>
    <!-- Vignette -->
    <div class="vignette"></div>
    <!-- Content -->
    <div class="content-layer">
      <RouterView />
      <PwaInstallBanner />
    </div>
  </div>
</template>

<style scoped>
.app-root {
  position: relative;
  min-height: 100dvh;
  overflow: hidden;
}

.bg-layer {
  position: fixed;
  inset: 0;
  z-index: 0;
  background-size: cover;
  background-position: center center;
  background-repeat: no-repeat;
  image-rendering: pixelated;
}

.scanlines {
  position: fixed;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background: repeating-linear-gradient(
    0deg,
    transparent,
    transparent 2px,
    rgba(0, 0, 0, 0.06) 2px,
    rgba(0, 0, 0, 0.06) 4px
  );
}

.vignette {
  position: fixed;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  box-shadow: inset 0 0 120px rgba(0, 0, 0, 0.35);
}

.content-layer {
  position: relative;
  z-index: 10;
  min-height: 100dvh;
}
</style>
