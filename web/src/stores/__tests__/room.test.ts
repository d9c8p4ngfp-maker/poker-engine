import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRoomStore, type RoomSnapshot } from '../room'

describe('useRoomStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should start with default values', () => {
    const store = useRoomStore()
    expect(store.roomId).toBeNull()
    expect(store.status).toBe('WAITING')
    expect(store.players).toEqual([])
    expect(store.pot).toBe(0)
    expect(store.smallBlind).toBe(10)
    expect(store.bigBlind).toBe(20)
    expect(store.myHoleCards).toEqual([])
  })

  it('should update from snapshot', () => {
    const store = useRoomStore()
    const snapshot: RoomSnapshot = {
      roomId: 'ABC123',
      name: '测试房',
      status: 'WAITING',
      players: [
        { playerId: 'p1', nickname: 'Alice', seatIndex: 0, chips: 1000,
          betInRound: 0, folded: false, allIn: false, holeCards: null,
          lastAction: null, connected: true },
      ],
      communityCards: [],
      pot: 0,
      sidePots: [],
      currentBet: 0,
      currentPlayerIndex: 0,
      bettingRound: 'PREFLOP',
      smallBlind: 5,
      bigBlind: 10,
      dealerIndex: 0,
      timeLeftSec: 30,
      myHoleCards: ['Ah', 'Kh'],
    }

    store.updateFromSnapshot(snapshot, 'my-id')
    expect(store.roomId).toBe('ABC123')
    expect(store.roomName).toBe('测试房')
    expect(store.players.length).toBe(1)
    expect(store.players[0].nickname).toBe('Alice')
    expect(store.smallBlind).toBe(5)
    expect(store.bigBlind).toBe(10)
    expect(store.myHoleCards).toEqual(['Ah', 'Kh'])
  })

  it('should add system messages', () => {
    const store = useRoomStore()
    store.addSystemMessage('Alice joined')
    expect(store.messages.length).toBe(1)
    expect(store.messages[0].text).toBe('Alice joined')
    expect(store.messages[0].type).toBe('system')
  })

  it('should reset to defaults', () => {
    const store = useRoomStore()
    store.roomId = 'XYZ'
    store.addSystemMessage('test')
    store.reset()
    expect(store.roomId).toBeNull()
    expect(store.messages).toEqual([])
    expect(store.status).toBe('WAITING')
  })
})
