<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useLogger } from '../composables/useLogger'
import { API_BASE_URL } from '../config'

const router = useRouter()
const userStore = useUserStore()
const logger = useLogger()

const nickname = ref(userStore.nickname || '')
const joinRoomId = ref('')
const toastMsg = ref('')
let toastTimer: ReturnType<typeof setTimeout> | null = null
function showToast(msg: string, durationMs = 3000) {
  toastMsg.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastMsg.value = '' }, durationMs)
}

onMounted(() => {
  const bgLayer = document.querySelector('.bg-layer') as HTMLElement
  if (bgLayer) bgLayer.style.backgroundImage = "url('/image_564507927161879.png')"
})

function handleCreateRoom() {
  if (!nickname.value.trim()) return
  logger.logAction('create_room', { nickname: nickname.value.trim() })
  userStore.setNickname(nickname.value.trim())
  router.push('/create')
}

async function handleJoinRoom() {
  if (!nickname.value.trim() || !joinRoomId.value.trim()) return
  logger.logAction('join_room', { roomId: joinRoomId.value.trim(), nickname: nickname.value.trim() })
  userStore.setNickname(nickname.value.trim())
  const res = await fetch(`${API_BASE_URL}/api/rooms/${joinRoomId.value.trim()}/join`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ playerId: userStore.playerId, nickname: nickname.value.trim() }),
  })
  if (!res.ok) {
    logger.logError('join_room_failed', { roomId: joinRoomId.value.trim(), status: res.status })
    if (res.status === 404) showToast('房间不存在')
    else if (res.status === 403) showToast('房间密码错误')
    else if (res.status === 409) showToast('房间已满')
    else showToast('加入失败: ' + res.status)
    return
  }
  const data = await res.json()
  userStore.setRoomId(data.roomId)
  router.push(`/room/${data.roomId}`)
}
</script>

<template>
  <div class="screen" data-test="home-screen">
    <div class="overlay"></div>
    <div class="panel">
      <div class="title-block">
        <h1 class="title">酒馆扑克</h1>
        <p class="subtitle">TAVERN POKER</p>
      </div>
      <div class="field">
        <label class="field-label">你的昵称</label>
        <input v-model="nickname" type="text" placeholder="输入昵称..." maxlength="12" class="field-input" />
      </div>
      <div class="btns">
        <button @click="handleCreateRoom" :disabled="!nickname.trim()" data-test="btn-create"
          class="btn btn-primary" :style="{ opacity: nickname.trim() ? 1 : 0.5 }">创建房间</button>
        <div class="join-row">
          <input v-model="joinRoomId" type="text" placeholder="房间号..." maxlength="6" class="field-input join-input" />
          <button @click="handleJoinRoom" :disabled="!nickname.trim() || !joinRoomId.trim()" data-test="btn-join"
            class="btn btn-secondary" :style="{ opacity: (nickname.trim() && joinRoomId.trim()) ? 1 : 0.5 }">加入</button>
        </div>
      </div>
    </div>
    <!-- Toast -->
    <Transition name="toast-fade">
      <div v-if="toastMsg" class="toast-bar" @click="toastMsg = ''">
        {{ toastMsg }}
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.screen { position:relative; min-height:100dvh; display:flex; align-items:center;
  padding:var(--safe-top) var(--safe-right) var(--safe-bottom) var(--safe-left); }
.overlay { position:absolute; inset:0; background:linear-gradient(to right, rgba(30,16,6,0.58), transparent 50%); pointer-events:none; }
.panel { position:relative; z-index:1; display:flex; flex-direction:column; gap:clamp(16px,3.6vh,28px);
  width:clamp(260px,38vw,440px); }
.title-block { line-height:1.1; }
.title { font-size:clamp(24px,6.8vh,40px); font-weight:bold; color:var(--color-gold);
  text-shadow:0 3px 8px rgba(0,0,0,0.6); margin:0; }
.subtitle { font-size:clamp(11px,3.2vh,18px); color:rgba(224,176,48,0.4); margin-top:8px; }
.field { display:flex; flex-direction:column; gap:5px; }
.field-label { font-size:clamp(10px,2.7vh,14px); color:rgba(224,176,48,0.55); }
.field-input { font-family:'Press Start 2P',monospace; font-size:clamp(11px,3vh,15px); padding:12px 14px;
  border-radius:6px; background:var(--color-input-bg); border:2px solid var(--color-border);
  color:var(--color-text-light); outline:none; letter-spacing:1px; }
.field-input:focus { border-color:var(--color-gold); }
.join-input { flex:1; text-align:center; text-transform:uppercase; letter-spacing:3px; }
.btns { display:flex; flex-direction:column; gap:12px; }
.join-row { display:flex; gap:8px; }
.btn { font-family:'Press Start 2P',monospace; font-weight:bold; border-radius:8px; transition:all 0.12s; cursor:pointer; letter-spacing:1px; }
.btn:active { transform:scale(0.97); }
.btn-primary { font-size:clamp(13px,3.6vh,18px); padding:clamp(12px,2.7vh,18px) clamp(24px,6vw,36px);
  background:var(--color-primary); color:var(--color-text); border:3px solid var(--color-button-shadow);
  box-shadow:var(--shadow-button); }
.btn-secondary { font-size:clamp(10px,2.7vh,14px); padding:12px 16px;
  background:rgba(140,96,56,0.92); color:var(--color-text-light); border:2px solid var(--color-button-shadow);
  box-shadow:0 2px 0 var(--color-button-shadow); }

/* Toast */
.toast-bar {
  position: fixed;
  top: calc(env(safe-area-inset-top, 0px) + 8px);
  left: 50%; transform: translateX(-50%);
  background: rgba(0,0,0,0.85);
  color: var(--color-text-light);
  font-family: 'Press Start 2P', monospace;
  font-size: clamp(8px, 2vh, 11px);
  padding: 10px 20px;
  border-radius: 8px;
  z-index: 100;
  pointer-events: auto;
  cursor: pointer;
  max-width: 90vw;
  text-align: center;
}
.toast-fade-enter-active { transition: opacity 0.2s, transform 0.2s; }
.toast-fade-leave-active { transition: opacity 0.3s, transform 0.3s; }
.toast-fade-enter-from { opacity: 0; transform: translateX(-50%) translateY(-10px); }
.toast-fade-leave-to { opacity: 0; transform: translateX(-50%) translateY(-10px); }
</style>
