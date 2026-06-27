<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { API_BASE_URL } from '../config'
import StepperControl from '../components/ui/StepperControl.vue'

const router = useRouter()
const userStore = useUserStore()

const name = ref(userStore.nickname + '的牌局')
const maxSeats = ref(8); const minPlayers = ref(2); const smallBlind = ref(10)
const initialChips = ref(1000); const actionTimeout = ref(30)
const bustEndsGame = ref(true); const autoContinue = ref(false); const password = ref(''); const showPassword = ref(false)
const bonus27Enabled = ref(false); const bonus27Amount = ref(0)
const bonusSFEnabled = ref(false); const bonusSFAmount = ref(0)
const bonusRoyalFlushDouble = ref(true)
const errorMsg = ref(''); const creating = ref(false)

onMounted(() => {
  const bgLayer = document.querySelector('.bg-layer') as HTMLElement
  if (bgLayer) bgLayer.style.backgroundImage = "url('/image_474537086141506.png')"
})

async function handleCreate() {
  errorMsg.value = ''; creating.value = true
  try {
    const res = await fetch(`${API_BASE_URL}/api/rooms`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        roomName: name.value, ownerId: userStore.playerId, ownerNickname: userStore.nickname,
        password: showPassword.value ? password.value : null,
        maxSeats: maxSeats.value, minPlayers: minPlayers.value,
        smallBlind: smallBlind.value, initialChips: initialChips.value,
        actionTimeoutSec: actionTimeout.value, bustEndsGame: bustEndsGame.value, autoContinue: autoContinue.value,
        bonus27Enabled: bonus27Enabled.value, bonus27Amount: bonus27Amount.value,
        bonusStraightFlushEnabled: bonusSFEnabled.value, bonusStraightFlushAmount: bonusSFAmount.value,
        bonusRoyalFlushDouble: bonusRoyalFlushDouble.value,
      }),
    })
    if (!res.ok) { const e = await res.json().catch(() => ({ message: res.statusText })); throw new Error(e.message || `创建失败 (${res.status})`) }
    const data = await res.json(); userStore.setRoomId(data.roomId); router.push(`/room/${data.roomId}`)
  } catch (e: any) { errorMsg.value = e.message || '创建房间失败，请检查后端是否启动' }
  finally { creating.value = false }
}
</script>

<template>
  <div class="screen">
    <div class="overlay"></div>
    <div class="panel">
      <button @click="router.push('/')" class="back-btn" data-test="btn-back">← 返回</button>
      <h1 class="title">创建房间</h1>
      <div class="field">
        <label class="field-label">房间名称</label>
        <input v-model="name" placeholder="房间名称..." maxlength="20" class="field-input" />
      </div>
      <div class="steppers">
        <StepperControl v-model="maxSeats" :min="2" :max="8" label="最大人数" />
        <StepperControl v-model="minPlayers" :min="2" :max="maxSeats" label="最小开局" />
        <StepperControl v-model="smallBlind" :min="1" :max="100" :step="5" label="小盲注" />
        <StepperControl v-model="initialChips" :min="100" :max="10000" :step="100" label="初始筹码" />
        <StepperControl v-model="actionTimeout" :min="10" :max="120" :step="5" label="超时(秒)" />
      </div>
      <label class="check"><input v-model="bustEndsGame" type="checkbox" /> 淘汰出局：有人输光则比赛结束</label>
      <label class="check"><input v-model="autoContinue" type="checkbox" /> 自动续局：所有人准备后自动开始</label>
      <div class="bonus-section">
        <div class="bonus-title">🎲 彩蛋规则</div>
        <div class="bonus-row">
          <label class="check" style="margin-bottom:4px"><input v-model="bonus27Enabled" type="checkbox" /> 2-7 Game</label>
          <div class="bonus-desc">拿 2-7 起手牌赢得底池，每人奖励彩金</div>
          <StepperControl v-if="bonus27Enabled" v-model="bonus27Amount" :min="0" :max="500" :step="10" label="彩金 (0=自动)" />
        </div>
        <div class="bonus-row">
          <label class="check" style="margin-bottom:4px"><input v-model="bonusSFEnabled" type="checkbox" /> 同花顺彩金</label>
          <div class="bonus-desc">同花顺赢得底池，每人奖励彩金（皇家翻倍）</div>
          <StepperControl v-if="bonusSFEnabled" v-model="bonusSFAmount" :min="0" :max="1000" :step="50" label="彩金 (0=自动)" />
        </div>
      </div>
      <div class="pw-section">
        <label class="check"><input v-model="showPassword" type="checkbox" /> 设置房间密码</label>
        <input v-if="showPassword" v-model="password" type="password" placeholder="房间密码..." class="field-input" />
      </div>
      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      <button @click="handleCreate" :disabled="creating" data-test="btn-submit" class="btn-submit" :style="{ opacity: creating ? 0.7 : 1 }">
        {{ creating ? '创建中...' : '创建房间' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.screen { position:relative; min-height:100dvh; display:flex; align-items:flex-start;
  padding:var(--safe-top) var(--safe-right) var(--safe-bottom) var(--safe-left); }
.overlay { position:absolute; inset:0; background:linear-gradient(to right, rgba(30,16,6,0.58), transparent 50%); pointer-events:none; }
.panel { position:relative; z-index:1; display:flex; flex-direction:column; gap:clamp(10px,2.3vh,18px);
  width:clamp(260px,38vw,440px); }
.back-btn { font-family:'Press Start 2P',monospace; font-size:clamp(9px,2.3vh,13px);
  color:rgba(224,176,48,0.42); background:none; border:none; cursor:pointer; text-align:left; padding:0; }
.back-btn:hover { color:rgba(224,176,48,0.65); }
.title { font-size:clamp(20px,6vh,36px); font-weight:bold; color:var(--color-gold);
  text-shadow:0 2px 6px rgba(0,0,0,0.5); margin:0; }
.field { display:flex; flex-direction:column; gap:5px; }
.field-label { font-size:clamp(10px,2.7vh,14px); color:rgba(224,176,48,0.55); }
.field-input { font-family:'Press Start 2P',monospace; font-size:clamp(11px,3vh,15px); padding:12px 14px;
  border-radius:6px; background:var(--color-input-bg); border:2px solid var(--color-border);
  color:var(--color-text-light); outline:none; letter-spacing:1px; }
.field-input:focus { border-color:var(--color-gold); }
.steppers { display:flex; flex-direction:column; gap:clamp(8px,2vh,14px); }
.check { display:flex; align-items:center; gap:6px; font-size:clamp(9px,2.3vh,12px);
  color:rgba(224,176,48,0.5); cursor:pointer; }
.check input[type="checkbox"] { accent-color:var(--color-gold); }
.pw-section { display:flex; flex-direction:column; gap:8px; }
.err { font-size:clamp(9px,2.3vh,12px); color:var(--color-accent); }
.btn-submit { font-family:'Press Start 2P',monospace; font-weight:bold;
  font-size:clamp(13px,3.6vh,18px); padding:clamp(12px,2.7vh,18px) clamp(24px,6vw,36px);
  background:var(--color-primary); color:var(--color-text); border:3px solid var(--color-button-shadow);
  border-radius:8px; box-shadow:var(--shadow-button); cursor:pointer; transition:all 0.12s; letter-spacing:1px; }
.btn-submit:active:not(:disabled) { transform:scale(0.97); }
.bonus-section { margin-top: 8px; }
.bonus-title {
  font-size: clamp(12px, 3vh, 15px); color: var(--color-gold);
  margin-bottom: 8px;
  font-weight: 700;
}
.bonus-row {
  background: var(--color-input-bg); border: 1px solid var(--color-border);
  border-radius: 8px; padding: 10px; margin-bottom: 8px;
}
.bonus-row .check {
  font-size: clamp(11px, 2.6vh, 14px);
}
.bonus-desc {
  font-size: clamp(9px, 2vh, 12px); color: var(--color-text-muted);
  margin: 4px 0 8px;
}
</style>
