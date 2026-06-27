import { defineStore } from 'pinia'
import { ref } from 'vue'

function randomUUID(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}

export const useUserStore = defineStore('user', () => {
  const playerId = ref(localStorage.getItem('poker_player_id') || randomUUID())
  const nickname = ref(localStorage.getItem('poker_nickname') || '')
  const currentRoomId = ref<string | null>(null)

  function setNickname(name: string) {
    nickname.value = name
    localStorage.setItem('poker_nickname', name)
  }

  function setRoomId(roomId: string | null) {
    currentRoomId.value = roomId
    if (roomId) localStorage.setItem('poker_room_id', roomId)
    else localStorage.removeItem('poker_room_id')
  }

  // Persist player ID
  localStorage.setItem('poker_player_id', playerId.value)

  return { playerId, nickname, currentRoomId, setNickname, setRoomId }
})
