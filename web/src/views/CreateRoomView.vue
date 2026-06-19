<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const name = ref(userStore.nickname + '的牌局')
const maxSeats = ref(6)
const minPlayers = ref(2)
const smallBlind = ref(10)
const initialChips = ref(1000)
const actionTimeout = ref(30)
const password = ref('')
const showPassword = ref(false)

async function handleCreate() {
  const res = await fetch('http://localhost:8080/api/rooms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: name.value,
      password: showPassword.value ? password.value : null,
      maxSeats: maxSeats.value,
      minPlayers: minPlayers.value,
      smallBlind: smallBlind.value,
      initialChips: initialChips.value,
      actionTimeoutSec: actionTimeout.value,
    }),
  })
  const data = await res.json()
  router.push(`/room/${data.roomId}`)
}
</script>

<template>
  <div class="min-h-screen p-4" style="background-color: var(--color-surface)">
    <div class="max-w-sm mx-auto space-y-4">
      <button @click="router.push('/')" class="text-sm" style="color: var(--color-text-muted)">
        ← 返回
      </button>
      <h1 class="text-xl font-bold" style="color: var(--color-gold)">创建房间</h1>

      <div class="space-y-3 p-4 rounded-lg" style="background-color: var(--color-surface-light)">
        <div>
          <label class="block text-xs mb-1" style="color: var(--color-text-muted)">房间名称</label>
          <input v-model="name" placeholder="房间名称..." maxlength="20"
            class="w-full px-3 py-2 rounded text-white text-sm outline-none"
            style="background-color: var(--color-surface); border: 1px solid var(--color-text-muted)" />
        </div>

        <div class="flex gap-3">
          <div class="flex-1">
            <label class="block text-xs mb-1" style="color: var(--color-text-muted)">最大人数</label>
            <input v-model.number="maxSeats" type="number" min="2" max="8"
              class="w-full px-3 py-2 rounded text-white text-sm outline-none"
              style="background-color: var(--color-surface); border: 1px solid var(--color-text-muted)" />
          </div>
          <div class="flex-1">
            <label class="block text-xs mb-1" style="color: var(--color-text-muted)">最小开局</label>
            <input v-model.number="minPlayers" type="number" min="2" :max="maxSeats"
              class="w-full px-3 py-2 rounded text-white text-sm outline-none"
              style="background-color: var(--color-surface); border: 1px solid var(--color-text-muted)" />
          </div>
        </div>

        <div class="flex gap-3">
          <div class="flex-1">
            <label class="block text-xs mb-1" style="color: var(--color-text-muted)">小盲注</label>
            <input v-model.number="smallBlind" type="number" min="1"
              class="w-full px-3 py-2 rounded text-white text-sm outline-none"
              style="background-color: var(--color-surface); border: 1px solid var(--color-text-muted)" />
          </div>
          <div class="flex-1">
            <label class="block text-xs mb-1" style="color: var(--color-text-muted)">初始筹码</label>
            <input v-model.number="initialChips" type="number" min="100" step="100"
              class="w-full px-3 py-2 rounded text-white text-sm outline-none"
              style="background-color: var(--color-surface); border: 1px solid var(--color-text-muted)" />
          </div>
        </div>

        <div>
          <label class="block text-xs mb-1" style="color: var(--color-text-muted)">超时(秒)</label>
          <input v-model.number="actionTimeout" type="number" min="10" max="120"
            class="w-full px-3 py-2 rounded text-white text-sm outline-none"
            style="background-color: var(--color-surface); border: 1px solid var(--color-text-muted)" />
        </div>

        <div class="space-y-2">
          <label class="flex items-center gap-2 text-xs" style="color: var(--color-text-muted)">
            <input v-model="showPassword" type="checkbox" /> 设置房间密码
          </label>
          <input v-if="showPassword" v-model="password" type="password" placeholder="房间密码..."
            class="w-full px-3 py-2 rounded text-white text-sm outline-none"
            style="background-color: var(--color-surface); border: 1px solid var(--color-text-muted)" />
        </div>
      </div>

      <button @click="handleCreate"
        class="w-full py-4 rounded-lg font-bold text-white text-lg transition"
        style="background-color: var(--color-primary)">
        创建房间
      </button>
    </div>
  </div>
</template>
