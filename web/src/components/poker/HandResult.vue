<script setup lang="ts">
import { computed } from 'vue'
import PlayingCard from './PlayingCard.vue'

const props = defineProps<{
  winners: { playerId: string; nickname?: string; handName: string; amount: number }[]
  isOwner: boolean
  players: { playerId: string; nickname: string; chips: number; holeCards: string[] | null; folded: boolean }[]
  readyPlayers: string[]
  totalActive: number
  allReady: boolean
  myPlayerId: string
  myChips: number
  minPlayers: number
  hasPendingGameOver: boolean
  roomStatus: string
  maxSeats: number
  playerCount: number
}>()

const emit = defineEmits<{
  'next-hand': []
  'ready': []
  'show-game-over': []
  'add-bot': []
  'borrow': []
  'dismiss-result': []
}>()

const amIReady = computed(() => props.readyPlayers.includes(props.myPlayerId))
const readyCount = computed(() => props.readyPlayers.length)
const canStart = computed(() => props.totalActive >= props.minPlayers)
const isSettling = computed(() => props.roomStatus !== 'WAITING' && !props.hasPendingGameOver)
const iAmBusted = computed(() => props.myChips <= 0)

// Filter to players who actually participated (have holeCards or folded)
const showPlayers = computed(() =>
  props.players.filter(p => p.holeCards !== null || p.folded)
)
</script>

<template>
  <div class="result-overlay" data-test="hand-result">
    <div class="result-modal">
      <!-- Winner banner -->
      <div class="result-trophy">🏆</div>
      <div class="result-title">本局结果</div>
      <div v-for="w in winners" :key="w.playerId" class="winner-row">
        <span class="winner-name">{{ w.nickname ?? w.playerId }}</span>
        <span class="winner-hand">{{ w.handName }}</span>
        <span class="winner-amount">+{{ w.amount }}</span>
      </div>

      <!-- All players' cards -->
      <div class="cards-divider"></div>
      <div class="cards-section">
        <div class="cards-section-title">玩家手牌</div>
        <div class="cards-grid">
          <div
            v-for="p in showPlayers"
            :key="p.playerId"
            class="cards-player"
            :class="{
              'is-winner': winners.some(w => w.playerId === p.playerId),
              'is-folded': p.folded,
            }"
          >
            <div class="cards-player-top">
              <span class="cards-player-name" :class="{ 'me': p.playerId === myPlayerId }">
                {{ p.nickname }}
                <span v-if="p.playerId === myPlayerId" class="cards-player-you">(你)</span>
              </span>
              <span class="cards-player-chips">💰{{ p.chips }}</span>
            </div>
            <div class="cards-player-hand">
              <template v-if="p.folded">
                <span class="cards-folded-label">FOLD</span>
              </template>
              <template v-else-if="p.holeCards">
                <PlayingCard
                  v-for="(c, i) in p.holeCards"
                  :key="i"
                  :card="c"
                  :face-up="true"
                  size="sm"
                />
              </template>
              <template v-else>
                <span class="cards-no-data">—</span>
              </template>
            </div>
            <!-- Ready indicator -->
            <div v-if="isSettling" class="cards-ready-dot settling">···</div>
            <div v-else-if="readyPlayers.includes(p.playerId)" class="cards-ready-dot ready">✅</div>
            <div v-else class="cards-ready-dot waiting">⏳</div>
          </div>
        </div>
      </div>

      <!-- Bottom: actions -->
      <div class="cards-divider"></div>

      <template v-if="hasPendingGameOver">
        <div class="result-outro">本局最终结算</div>
        <button class="result-btn result-next-btn" data-test="btn-show-ranking" @click="$emit('show-game-over')">
          查看最终排名
        </button>
      </template>

      <template v-else>
        <!-- Ready + action buttons -->
        <button
          v-if="playerCount < maxSeats"
          class="result-btn result-add-bot-btn"
          data-test="btn-add-bot-hand-result"
          @click="$emit('add-bot')"
        >
          + 添加机器人
        </button>

        <!-- Busted: spectate or borrow -->
        <template v-if="iAmBusted">
          <div class="result-wait-players">
            💸 你已破产，可以观战或借筹码继续
          </div>
          <div class="result-btn-row">
            <button class="result-btn result-spectate-btn" data-test="btn-spectate" @click="$emit('dismiss-result')">
              观战
            </button>
            <button class="result-btn result-borrow-btn" data-test="btn-borrow-hand-result" @click="$emit('borrow')">
              借筹码
            </button>
          </div>
        </template>

        <template v-else-if="!canStart">
          <div class="result-wait-players">
            ⏳ 等待破产玩家借筹码加入...
          </div>
          <button v-if="isOwner" class="result-btn" disabled>
            人数不足，无法开始
          </button>
        </template>

        <template v-else>
          <button
            v-if="!amIReady"
            class="result-btn ready-btn"
            data-test="btn-ready"
            @click="$emit('ready')"
          >
            准备
          </button>
          <div v-else class="result-wait">
            ✅ 已准备 — {{ isOwner ? '等待其他玩家准备...' : '等待房主开始...' }}
          </div>

          <button
            v-if="isOwner"
            class="result-btn"
            data-test="btn-next-hand"
            :class="{ 'btn-disabled': !allReady }"
            :disabled="!allReady"
            @click="$emit('next-hand')"
          >
            {{ allReady ? '下一局' : `下一局 (${readyCount}/${totalActive})` }}
          </button>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.result-overlay {
  position: absolute; inset: 0; z-index: 10;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0, 0, 0, 0.7); padding: 0 12px;
}
.result-modal {
  width: min(92vw, 700px);
  max-height: 90vh; overflow-y: auto;
  background: var(--color-panel-bg);
  border: 2px solid var(--color-button-shadow);
  border-radius: 14px;
  padding: clamp(12px, 2.5vh, 20px) clamp(12px, 3vw, 24px);
  text-align: center;
  font-family: 'Press Start 2P', monospace;
}

/* Winner */
.result-trophy { font-size: 28px; }
.result-title {
  font-size: clamp(10px, 2.3vh, 14px);
  font-weight: bold; color: var(--color-text-light);
  margin: 6px 0 10px;
}
.winner-row {
  display: inline-flex; gap: 8px; align-items: baseline;
  margin-bottom: 6px; flex-wrap: wrap; justify-content: center;
}
.winner-name {
  font-size: clamp(8px, 2vh, 11px);
  font-weight: bold; color: var(--color-gold);
}
.winner-hand {
  font-size: clamp(7px, 1.6vh, 9px);
  color: var(--color-text-muted);
}
.winner-amount {
  font-size: clamp(8px, 2vh, 11px);
  font-weight: bold; color: var(--color-gold);
}

/* Divider */
.cards-divider {
  height: 1px;
  background: var(--color-text-muted);
  opacity: 0.25;
  margin: 10px 0;
}

/* Cards section */
.cards-section { margin-bottom: 4px; }
.cards-section-title {
  font-size: clamp(7px, 1.5vh, 9px);
  color: var(--color-text-muted);
  margin-bottom: 8px;
}
.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px;
}
.cards-player {
  background: var(--color-input-bg);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 8px 6px 6px;
  text-align: center;
  transition: border-color 0.3s;
}
.cards-player.is-winner {
  border-color: var(--color-gold);
  background: rgba(240, 192, 64, 0.08);
}
.cards-player.is-folded {
  opacity: 0.55;
}

.cards-player-top {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 4px;
}
.cards-player-name {
  font-size: clamp(7px, 1.4vh, 9px);
  color: var(--color-text-light);
  max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.cards-player-name.me { color: var(--color-gold); }
.cards-player-you {
  font-size: 0.7em; color: var(--color-text-muted);
}
.cards-player-chips {
  font-size: clamp(6px, 1.2vh, 8px);
  color: var(--color-text-muted);
}
.cards-player-hand {
  display: flex; justify-content: center; gap: 6px;
  min-height: 44px; align-items: center;
}
.cards-folded-label {
  font-size: clamp(8px, 1.6vh, 10px);
  color: var(--color-accent); font-weight: bold;
}
.cards-no-data {
  font-size: clamp(8px, 1.6vh, 10px);
  color: var(--color-text-muted);
}

/* Ready dot below each player */
.cards-ready-dot {
  margin-top: 4px;
  font-size: 10px;
  line-height: 1;
}
.cards-ready-dot.ready { color: #4CAF50; }
.cards-ready-dot.waiting { color: var(--color-text-muted); }
.cards-ready-dot.settling {
  color: #ffc107;
  animation: pulse-settling 1.5s ease-in-out infinite;
}

/* Bottom actions */
.result-outro {
  font-size: clamp(8px, 1.8vh, 10px);
  color: #ffc107;
  padding: 4px 0 6px;
}

/* Buttons */
.result-btn {
  width: 100%; padding: 10px 0; border-radius: 10px; font-weight: bold;
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(8px, 2vh, 11px);
  background: var(--color-primary); color: var(--color-text);
  border: 2px solid var(--color-button-shadow);
  box-shadow: 0 2px 0 var(--color-button-shadow);
  cursor: pointer; transition: all 0.2s;
  margin-top: 8px;
}
.result-btn:active { transform: scale(0.97); }
.result-btn.btn-disabled {
  opacity: 0.5; cursor: not-allowed;
}
.ready-btn {
  background: #4CAF50;
  animation: pulse-ready 2s ease-in-out infinite;
}
.result-borrow-btn {
  background: #ff9800;
  border-color: #cc7a00;
}
.result-spectate-btn {
  background: var(--color-input-bg);
  color: var(--color-text-muted);
  border-color: var(--color-border);
}
.result-btn-row {
  display: flex; gap: 8px; justify-content: center;
}
.result-btn-row .result-btn {
  flex: 1; max-width: 160px;
}
.result-add-bot-btn {
  background: var(--color-input-bg);
  border: 1px dashed var(--color-border);
  box-shadow: none;
  font-size: clamp(7px, 1.4vh, 8px);
}
.result-next-btn {
  background: var(--color-gold); color: #382818;
}
@keyframes pulse-ready {
  0%, 100% { box-shadow: 0 2px 0 var(--color-button-shadow); }
  50% { box-shadow: 0 2px 12px rgba(76, 175, 80, 0.4); }
}
@keyframes pulse-settling {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
.result-wait {
  font-size: clamp(7px, 1.6vh, 9px);
  color: var(--color-text-muted); margin-top: 8px;
  padding: 4px 0;
}
.result-wait-players {
  font-size: clamp(7px, 1.6vh, 9px);
  color: #ff9800;
  padding: 6px 0;
}
</style>
