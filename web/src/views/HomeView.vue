<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useLogger } from '../composables/useLogger'

const router = useRouter()
const userStore = useUserStore()
const logger = useLogger()

const nickname = ref(userStore.nickname || '')
const joinRoomId = ref('')

onMounted(() => {
  const bgLayer = document.querySelector('.bg-layer') as HTMLElement
  if (bgLayer) {
    bgLayer.style.backgroundImage = "url('/image_564507927161879.png')"
  }
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

  const res = await fetch(`/api/rooms/${joinRoomId.value.trim()}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ playerId: userStore.playerId, nickname: nickname.value.trim() }),
  })

  if (!res.ok) {
    logger.logError('join_room_failed', { roomId: joinRoomId.value.trim(), status: res.status })
    if (res.status === 404) alert('房间不存在')
    else if (res.status === 403) alert('房间密码错误')
    else if (res.status === 409) alert('房间已满')
    else alert('加入失败: ' + res.status)
    return
  }

  const data = await res.json()
  userStore.setRoomId(data.roomId)
  router.push(`/room/${data.roomId}`)
}
</script>

<template>
  <div class="home-screen" data-test="home-screen">
    <div class="home-overlay"></div>
    <div class="home-left-col">
      <div class="home-title-block">
        <h1 class="home-title">酒馆扑克</h1>
        <p class="home-subtitle">TAVERN POKER</p>
      </div>
      <div class="home-input-group">
        <label class="home-label">你的昵称</label>
        <input v-model="nickname" type="text" placeholder="输入昵称..." maxlength="12" class="home-input" />
      </div>
      <div class="home-buttons">
        <button @click="handleCreateRoom" :disabled="!nickname.trim()" data-test="btn-create"
          class="home-btn home-btn-primary" :style="{ opacity: nickname.trim() ? 1 : 0.5 }">
          创建房间
        </button>
        <div class="home-join-row">
          <input v-model="joinRoomId" type="text" placeholder="房间号..." maxlength="6" class="home-input home-join-input" />
          <button @click="handleJoinRoom" :disabled="!nickname.trim() || !joinRoomId.trim()" data-test="btn-join"
            class="home-btn home-btn-secondary" :style="{ opacity: (nickname.trim() && joinRoomId.trim()) ? 1 : 0.5 }">
            加入
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-screen { position:relative; min-height:100vh; display:flex; align-items:center; padding:var(--safe-top) var(--safe-right) var(--safe-bottom) var(--safe-left); }
.home-overlay { position:absolute; inset:0; background:linear-gradient(to right, rgba(30,16,6,0.55), transparent 52%); pointer-events:none; }
.home-left-col { position:relative; z-index:1; display:flex; flex-direction:column; gap:36px; width:min(620px, 38%); min-width:340px; }
.home-title-block { line-height:1.1; }
.home-title { font-size:50px; font-weight:bold; color:var(--color-gold); text-shadow:0 4px 8px rgba(0,0,0,0.6); margin:0; }
.home-subtitle { font-size:18px; color:rgba(224,176,48,0.45); margin-top:10px; text-shadow:0 1px 2px rgba(0,0,0,0.4); }
.home-input-group { display:flex; flex-direction:column; gap:8px; }
.home-label { font-size:13px; color:rgba(224,176,48,0.6); }
.home-input { font-family:'Press Start 2P',monospace; font-size:13px; padding:14px 16px; border-radius:var(--radius-sm); background:var(--color-input-bg); border:2px solid var(--color-border); color:var(--color-text-light); outline:none; letter-spacing:1px; }
.home-input:focus { border-color:var(--color-gold); }
.home-join-input { flex:1; text-align:center; text-transform:uppercase; letter-spacing:4px; }
.home-buttons { display:flex; flex-direction:column; gap:16px; }
.home-join-row { display:flex; gap:10px; }
.home-btn { font-family:'Press Start 2P',monospace; font-weight:bold; border-radius:10px; transition:all 0.12s; cursor:pointer; letter-spacing:2px; }
.home-btn:active { transform:scale(0.97); }
.home-btn-primary { font-size:20px; padding:20px 40px; background:var(--color-primary); color:var(--color-text); border:3px solid var(--color-button-shadow); box-shadow:var(--shadow-button); }
.home-btn-secondary { font-size:13px; padding:14px 20px; background:rgba(140,96,56,0.92); color:var(--color-text-light); border:2px solid var(--color-button-shadow); box-shadow:0 3px 0 var(--color-button-shadow); }

@media (max-width: 800px) {
  .home-title { font-size:34px; }
  .home-btn-primary { font-size:16px; padding:14px 28px; }
  .home-left-col { width:100%; min-width:unset; }
}
</style>
