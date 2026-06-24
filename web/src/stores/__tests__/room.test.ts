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

  it('should handle partial snapshot without roomId or name', () => {
    const store = useRoomStore()
    store.roomId = 'existing-id'
    store.roomName = 'existing-name'
    const snapshot: RoomSnapshot = {
      roomId: undefined as any, name: undefined as any,
      status: 'PLAYING',
      players: [],
      communityCards: [],
      pot: 500,
      sidePots: [],
      currentBet: 50,
      currentPlayerIndex: 1,
      bettingRound: 'FLOP',
      smallBlind: 10,
      bigBlind: 20,
      dealerIndex: 0,
      timeLeftSec: 25,
      myHoleCards: [],
    }
    store.updateFromSnapshot(snapshot, 'p1')
    // roomId and roomName should not be overwritten to undefined
    expect(store.roomId).toBe('existing-id')
    expect(store.roomName).toBe('existing-name')
    expect(store.pot).toBe(500)
    expect(store.bettingRound).toBe('FLOP')
  })

  it('should store minRaise when provided in snapshot', () => {
    const store = useRoomStore()
    const snapshot: RoomSnapshot = {
      roomId: 'R', name: 'N', status: 'PLAYING',
      players: [], communityCards: [], pot: 0, sidePots: [],
      currentBet: 0, currentPlayerIndex: 0, bettingRound: 'PREFLOP',
      smallBlind: 5, bigBlind: 10, dealerIndex: 0, timeLeftSec: 30,
      myHoleCards: [],
      minRaise: 20,
    }
    store.updateFromSnapshot(snapshot, 'p1')
    expect(store.minRaise).toBe(20)
  })

  it('should default minRaise to bigBlind', () => {
    const store = useRoomStore()
    const snapshot: RoomSnapshot = {
      roomId: 'R', name: 'N', status: 'PLAYING',
      players: [], communityCards: [], pot: 0, sidePots: [],
      currentBet: 0, currentPlayerIndex: 0, bettingRound: 'PREFLOP',
      smallBlind: 5, bigBlind: 10, dealerIndex: 0, timeLeftSec: 30,
      myHoleCards: [],
    }
    store.updateFromSnapshot(snapshot, 'p1')
    expect(store.minRaise).toBe(10) // fallback to bigBlind
  })

  it('should track currentPlayerId correctly', () => {
    const store = useRoomStore()
    const snapshot: RoomSnapshot = {
      roomId: 'R', name: 'N', status: 'PLAYING',
      players: [
        { playerId: 'p1', nickname: 'A', seatIndex: 0, chips: 1000, betInRound: 0,
          folded: false, allIn: false, holeCards: null, lastAction: null, connected: true },
        { playerId: 'p2', nickname: 'B', seatIndex: 1, chips: 1000, betInRound: 0,
          folded: false, allIn: false, holeCards: null, lastAction: null, connected: true },
      ],
      communityCards: [], pot: 0, sidePots: [],
      currentBet: 0, currentPlayerIndex: 1, bettingRound: 'PREFLOP',
      smallBlind: 5, bigBlind: 10, dealerIndex: 0, timeLeftSec: 30,
      myHoleCards: [],
      currentPlayerId: 'p2',
    }
    store.updateFromSnapshot(snapshot, 'p1')
    expect(store.currentPlayerId).toBe('p2')
  })
})
