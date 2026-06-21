<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import StepperControl from '../components/ui/StepperControl.vue'

const router = useRouter()
const userStore = useUserStore()

const name = ref(userStore.nickname + '的牌局')
const maxSeats = ref(8)
const minPlayers = ref(2)
const smallBlind = ref(10)
const initialChips = ref(1000)
const actionTimeout = ref(30)
const bustEndsGame = ref(true)
const password = ref('')
const showPassword = ref(false)

const errorMsg = ref('')
const creating = ref(false)

onMounted(() => {
  const bgLayer = document.querySelector('.bg-layer') as HTMLElement
  if (bgLayer) {
    bgLayer.style.backgroundImage = "url('/image_166619076022279.png')"
  }
})

async function handleCreate() {
  errorMsg.value = ''
  creating.value = true
  try {
    const res = await fetch('/api/rooms', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        roomName: name.value,
        ownerId: userStore.playerId,
        ownerNickname: userStore.nickname,
        password: showPassword.value ? password.value : null,
        maxSeats: maxSeats.value,
        minPlayers: minPlayers.value,
        smallBlind: smallBlind.value,
        initialChips: initialChips.value,
        actionTimeoutSec: actionTimeout.value,
        bustEndsGame: bustEndsGame.value,
      }),
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }))
      throw new Error(err.message || `创建失败 (${res.status})`)
    }
    const data = await res.json()
    userStore.setRoomId(data.roomId)
    router.push(`/room/${data.roomId}`)
  } catch (e: any) {
    errorMsg.value = e.message || '创建房间失败，请检查后端是否启动'
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div class="create-screen">
    <div class="create-overlay"></div>

    <div class="create-left-col">
      <button
        @click="router.push('/')"
        class="create-back"
        data-test="btn-back"
      >
        ← 返回
      </button>
      <h1 class="create-title">创建房间</h1>

      <!-- Room name -->
      <div class="create-input-group">
        <label class="create-label">房间名称</label>
        <input
          v-model="name"
          placeholder="房间名称..."
          maxlength="20"
          class="create-input"
        />
      </div>

      <!-- Stepper fields -->
      <div class="create-steppers">
        <StepperControl v-model="maxSeats" :min="2" :max="8" label="最大人数" />
        <StepperControl v-model="minPlayers" :min="2" :max="maxSeats" label="最小开局" />
        <StepperControl v-model="smallBlind" :min="1" :max="100" :step="5" label="小盲注" />
        <StepperControl v-model="initialChips" :min="100" :max="10000" :step="100" label="初始筹码" />
        <StepperControl v-model="actionTimeout" :min="10" :max="120" :step="5" label="超时(秒)" />
      </div>

      <!-- Bust checkbox -->
      <label class="create-checkbox">
        <input v-model="bustEndsGame" type="checkbox" />
        淘汰出局：有人输光筹码则比赛结束
      </label>

      <!-- Password -->
      <div class="create-password-section">
        <label class="create-checkbox">
          <input v-model="showPassword" type="checkbox" /> 设置房间密码
        </label>
        <input
          v-if="showPassword"
          v-model="password"
          type="password"
          placeholder="房间密码..."
          class="create-input"
        />
      </div>

      <!-- Error -->
      <p v-if="errorMsg" class="create-error">{{ errorMsg }}</p>

      <!-- Submit -->
      <button
        @click="handleCreate"
        :disabled="creating"
        data-test="btn-submit"
        class="create-submit"
        :style="{ opacity: creating ? 0.7 : 1 }"
      >
        {{ creating ? '创建中...' : '创建房间' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.create-screen {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  padding: var(--safe-top) var(--safe-right) var(--safe-bottom) var(--safe-left);
}

.create-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to right, rgba(30, 16, 6, 0.55), transparent 55%);
  pointer-events: none;
}

.create-left-col {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: min(460px, 35%);
  min-width: 300px;
}

.create-back {
  font-family: 'Press Start 2P', monospace;
  font-size: 9px;
  color: rgba(224, 176, 48, 0.45);
  background: none;
  border: none;
  cursor: pointer;
  text-align: left;
  padding: 0;
}

.create-back:hover {
  color: rgba(224, 176, 48, 0.7);
}

.create-title {
  font-size: 28px;
  font-weight: bold;
  color: var(--color-gold);
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.5);
  margin: 0;
}

.create-input-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.create-label {
  font-size: 9px;
  color: rgba(224, 176, 48, 0.6);
}

.create-input {
  font-family: 'Press Start 2P', monospace;
  font-size: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-input-bg);
  border: 1px solid var(--color-border);
  color: var(--color-text-light);
  outline: none;
  letter-spacing: 1px;
}

.create-input:focus {
  border-color: var(--color-gold);
}

.create-steppers {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.create-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 9px;
  color: rgba(224, 176, 48, 0.55);
  cursor: pointer;
}

.create-checkbox input[type="checkbox"] {
  accent-color: var(--color-gold);
}

.create-password-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.create-error {
  font-size: 9px;
  color: var(--color-accent);
}

.create-submit {
  font-family: 'Press Start 2P', monospace;
  font-weight: bold;
  font-size: 16px;
  padding: 16px 36px;
  background: var(--color-primary);
  color: var(--color-text);
  border: 3px solid var(--color-button-shadow);
  border-radius: 8px;
  box-shadow: var(--shadow-button);
  cursor: pointer;
  transition: all 0.12s;
  letter-spacing: 2px;
}

.create-submit:active:not(:disabled) {
  transform: scale(0.97);
}

@media (max-width: 700px) {
  .create-title { font-size: 22px; }
  .create-submit { font-size: 12px; padding: 12px 24px; }
  .create-left-col { width: 100%; min-width: unset; }
}
</style>
