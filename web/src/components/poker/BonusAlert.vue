<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'

const props = defineProps<{
  bonusType: '27_GAME' | 'STRAIGHT_FLUSH' | 'ROYAL_FLUSH'
  winnerName: string
  bonusPerPlayer: number
  transfers: Record<string, number>
}>()

const emit = defineEmits<{ close: [] }>()
const phase = ref<'enter' | 'show' | 'exit'>('enter')

const config: Record<string, { emoji: string; title: string; subtitle: string; color: string; glow: string; particles: string }> = {
  '27_GAME': {
    emoji: '\uD83C\uDCCF', title: '2-7 GAME!',
    subtitle: '\u6700\u5DEE\u724C\u8D62\u4E86\uFF01',
    color: '#f0c040', glow: 'rgba(240,192,64,0.6)',
    particles: '\u2728\uD83C\uDCCF\uD83D\uDCB0',
  },
  'STRAIGHT_FLUSH': {
    emoji: '\uD83D\uDD25', title: '\u540C\u82B1\u987A!',
    subtitle: '\u8D85\u5F3A\u724C\u578B\uFF01',
    color: '#ff6040', glow: 'rgba(255,96,64,0.6)',
    particles: '\uD83D\uDD25\uD83D\uDC8E\u26A1',
  },
  'ROYAL_FLUSH': {
    emoji: '\uD83D\uDC51', title: '\u7687\u5BB6\u540C\u82B1\u987A!!',
    subtitle: '\u81F3\u5C0A\u65E0\u4E0A\uFF01\uFF01',
    color: '#ff4080', glow: 'rgba(255,64,128,0.6)',
    particles: '\uD83D\uDC51\uD83D\uDC8E\uD83C\uDF1F\u2728\uD83D\uDD25',
  },
}

const cfg = computed(() => config[props.bonusType] || config['27_GAME'])

const particles = computed(() => {
  const chars = [...new Intl.Segmenter().segment(cfg.value.particles)].map(s => s.segment).filter(c => c !== '\uFE0F')
  if (chars.length === 0) chars.push('\u2728')
  return Array.from({ length: 20 }, (_, i) => ({
    char: chars[i % chars.length],
    x: Math.random() * 100,
    delay: Math.random() * 2,
    duration: 1.5 + Math.random() * 2,
    size: 12 + Math.random() * 16,
  }))
})

let exitTimer: ReturnType<typeof setTimeout> | null = null
let closeTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  phase.value = 'show'
  exitTimer = setTimeout(() => { phase.value = 'exit' }, 5000)
  closeTimer = setTimeout(() => emit('close'), 5800)
})

onUnmounted(() => {
  if (exitTimer) clearTimeout(exitTimer)
  if (closeTimer) clearTimeout(closeTimer)
})

function dismiss() {
  if (exitTimer) clearTimeout(exitTimer)
  if (closeTimer) clearTimeout(closeTimer)
  phase.value = 'exit'
  closeTimer = setTimeout(() => emit('close'), 800)
}
</script>
<template>
  <div class="bonus-overlay" :class="phase" @click="dismiss">
    <div class="particles">
      <span v-for="(p, i) in particles" :key="i" class="particle"
        :style="{
          left: p.x + '%',
          fontSize: p.size + 'px',
          animationDelay: p.delay + 's',
          animationDuration: p.duration + 's',
        }">{{ p.char }}</span>
    </div>
    <div class="glow-ring" :style="{ '--glow': cfg.glow, '--color': cfg.color }"></div>
    <div class="bonus-card" :style="{ '--color': cfg.color, '--glow': cfg.glow }" @click.stop>
      <div class="bonus-emoji">{{ cfg.emoji }}</div>
      <div class="bonus-title">{{ cfg.title }}</div>
      <div class="bonus-winner">{{ winnerName }}</div>
      <div class="bonus-subtitle">{{ cfg.subtitle }}</div>
      <div class="bonus-divider"></div>
      <div class="bonus-payout">
        <div class="payout-label">每位玩家支付</div>
        <div class="payout-amount">💰 {{ bonusPerPlayer }}</div>
      </div>
      <div class="bonus-tap-hint">点击空白处关闭</div>
    </div>
  </div>
</template>
<style scoped>
.bonus-overlay {
  position: absolute; inset: 0; z-index: 30;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0, 0, 0, 0);
  transition: background 0.5s;
  overflow: hidden;
  cursor: pointer;
}
.bonus-overlay.show { background: rgba(0, 0, 0, 0.7); }
.bonus-overlay.exit {
  background: rgba(0, 0, 0, 0); transition: background 0.8s;
  pointer-events: none;
}
.particles { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.particle {
  position: absolute; bottom: -30px;
  animation: floatUp linear infinite;
  opacity: 0.7;
}
@keyframes floatUp {
  0% { transform: translateY(0) rotate(0deg); opacity: 0; }
  10% { opacity: 0.8; }
  90% { opacity: 0.6; }
  100% { transform: translateY(-110vh) rotate(360deg); opacity: 0; }
}
.glow-ring {
  position: absolute; width: 300px; height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--glow) 0%, transparent 70%);
  animation: glowPulse 2s ease-in-out infinite;
  pointer-events: none;
}
@keyframes glowPulse {
  0%, 100% { transform: scale(0.8); opacity: 0.4; }
  50% { transform: scale(1.2); opacity: 0.8; }
}
.bonus-card {
  position: relative; z-index: 1;
  text-align: center; padding: clamp(16px, 4vh, 32px) clamp(20px, 5vw, 40px);
  background: linear-gradient(145deg, rgba(40, 20, 8, 0.95), rgba(60, 30, 12, 0.95));
  border: 3px solid var(--color);
  border-radius: 20px;
  box-shadow:
    0 0 30px var(--glow),
    0 0 60px var(--glow),
    inset 0 1px 0 rgba(255,255,255,0.1);
  font-family: 'Press Start 2P', monospace;
  animation: cardSlam 0.6s cubic-bezier(0.2, 0.8, 0.2, 1.2);
  max-width: 90vw;
  cursor: default;
}
.exit .bonus-card {
  animation: cardExit 0.8s ease forwards;
}
@keyframes cardSlam {
  0% { transform: scale(0) rotate(-10deg); opacity: 0; }
  50% { transform: scale(1.15) rotate(2deg); }
  70% { transform: scale(0.95) rotate(-1deg); }
  100% { transform: scale(1) rotate(0deg); opacity: 1; }
}
@keyframes cardExit {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(0.5) translateY(-50px); opacity: 0; }
}
.bonus-emoji {
  font-size: clamp(36px, 10vh, 64px);
  animation: emojiShake 0.5s ease 0.6s;
  filter: drop-shadow(0 4px 8px rgba(0,0,0,0.5));
}
@keyframes emojiShake {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(-15deg) scale(1.1); }
  75% { transform: rotate(15deg) scale(1.1); }
}
.bonus-title {
  font-size: clamp(14px, 4vh, 24px); font-weight: bold;
  color: var(--color);
  text-shadow: 0 0 20px var(--glow), 0 2px 4px rgba(0,0,0,0.5);
  margin: 8px 0;
  animation: titleGlow 1.5s ease-in-out infinite;
}
@keyframes titleGlow {
  0%, 100% { text-shadow: 0 0 20px var(--glow), 0 2px 4px rgba(0,0,0,0.5); }
  50% { text-shadow: 0 0 40px var(--glow), 0 0 60px var(--glow), 0 2px 4px rgba(0,0,0,0.5); }
}
.bonus-winner {
  font-size: clamp(10px, 2.5vh, 16px); color: #fff;
  text-shadow: 0 1px 3px rgba(0,0,0,0.5);
  margin: 4px 0;
}
.bonus-subtitle {
  font-size: clamp(7px, 1.8vh, 11px); color: var(--color-text-muted);
  margin-bottom: 12px;
}
.bonus-divider {
  height: 2px; margin: 8px auto;
  width: 60%;
  background: linear-gradient(90deg, transparent, var(--color), transparent);
  opacity: 0.5;
}
.bonus-payout { margin-top: 12px; }
.payout-label {
  font-size: clamp(6px, 1.5vh, 9px); color: var(--color-text-muted);
  margin-bottom: 4px;
}
.payout-amount {
  font-size: clamp(12px, 3vh, 18px); font-weight: bold;
  color: var(--color-gold);
  text-shadow: 0 0 10px rgba(224,176,48,0.5);
  animation: amountPop 0.3s ease 0.8s both;
}
@keyframes amountPop {
  0% { transform: scale(0); }
  60% { transform: scale(1.2); }
  100% { transform: scale(1); }
}
.bonus-tap-hint {
  margin-top: 12px;
  font-size: clamp(5px, 1.2vh, 7px);
  color: rgba(255, 255, 255, 0.3);
  animation: hintFade 1s ease 3s both;
}
@keyframes hintFade {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
