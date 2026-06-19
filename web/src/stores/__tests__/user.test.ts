import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../user'

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('should generate a playerId on first access', () => {
    const store = useUserStore()
    expect(store.playerId).toBeTruthy()
    expect(store.playerId.length).toBeGreaterThan(10)
  })

  it('should persist playerId to localStorage', () => {
    const store = useUserStore()
    const id = store.playerId
    expect(localStorage.getItem('poker_player_id')).toBe(id)
  })

  it('should set and persist nickname', () => {
    const store = useUserStore()
    store.setNickname('Alice')
    expect(store.nickname).toBe('Alice')
    expect(localStorage.getItem('poker_nickname')).toBe('Alice')
  })

  it('should restore nickname from localStorage', () => {
    localStorage.setItem('poker_nickname', 'Bob')
    const store = useUserStore()
    expect(store.nickname).toBe('Bob')
  })

  it('should set and clear roomId', () => {
    const store = useUserStore()
    store.setRoomId('ABC123')
    expect(store.currentRoomId).toBe('ABC123')
    store.setRoomId(null)
    expect(store.currentRoomId).toBeNull()
  })
})
