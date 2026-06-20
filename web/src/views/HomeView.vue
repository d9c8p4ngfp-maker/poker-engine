<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useLogger } from '../composables/useLogger'

const router = useRouter()
const userStore = useUserStore()
const logger = useLogger()

const nickname = ref(userStore.nickname || '')
const joinRoomId = ref('')

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
    body: JSON.stringify({
      playerId: userStore.playerId,
      nickname: nickname.value.trim(),
    }),
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
  <div class="min-h-screen flex flex-col items-center justify-center p-4"
       style="background-color: var(--color-surface)">

    <div class="w-full max-w-sm space-y-5">
      <!-- Title -->
      <div class="text-center relative">
        <button @click="logger.download()" class="absolute right-0 top-0 text-xs px-2 py-1 rounded" style="color: var(--color-text-muted); border: 1px solid var(--color-border);" title="下载操作日志">📋</button>
        <h1 class="text-3xl font-bold" style="color: var(--color-gold)">
          ♠ Texas Hold'em
        </h1>
        <p class="mt-1 text-sm" style="color: var(--color-text-muted)">手机网页版多人联机</p>
      </div>

      <!-- Nickname -->
      <div>
        <label class="block text-sm mb-1" style="color: var(--color-text-muted)">你的昵称</label>
        <input
          v-model="nickname"
          type="text"
          placeholder="输入昵称..."
          maxlength="12"
          class="w-full px-4 py-3 rounded-lg text-white outline-none"
          style="background-color: var(--color-surface-light); border: 1px solid var(--color-text-muted)"
        />
      </div>

      <!-- Create Room -->
      <button
        @click="handleCreateRoom"
        :disabled="!nickname.trim()"
        class="w-full py-4 rounded-lg font-bold text-white text-lg transition"
        style="background-color: var(--color-primary)"
        :style="{ opacity: nickname.trim() ? 1 : 0.5 }"
      >
        创建房间
      </button>

      <!-- Join Room -->
      <div class="flex gap-2">
        <input
          v-model="joinRoomId"
          type="text"
          placeholder="房间号..."
          maxlength="6"
          class="flex-1 px-4 py-3 rounded-lg text-white outline-none uppercase tracking-widest text-center text-lg"
          style="background-color: var(--color-surface-light); border: 1px solid var(--color-text-muted)"
        />
        <button
          @click="handleJoinRoom"
          :disabled="!nickname.trim() || !joinRoomId.trim()"
          class="px-6 py-3 rounded-lg font-bold text-white transition"
          style="background-color: var(--color-surface-light); border: 1px solid var(--color-primary)"
          :style="{ opacity: (nickname.trim() && joinRoomId.trim()) ? 1 : 0.5 }"
        >
          加入
        </button>
      </div>
    </div>
  </div>
</template>
