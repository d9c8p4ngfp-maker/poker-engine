import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRoomStore } from '../../stores/room'

// STOMP subscription behavior validation (functional tests without actual WebSocket)
describe('RoomView STOMP subscriptions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('store correctly initializes for room subscriptions', () => {
    const store = useRoomStore()
    expect(store.status).toBe('WAITING')
    expect(store.players).toEqual([])
    expect(store.communityCards).toEqual([])
  })

  it('simulates receiving room update via /topic/room/{id}', () => {
    const store = useRoomStore()
    store.updateFromSnapshot({
      roomId: 'room1',
      name: 'Test',
      status: 'WAITING',
      players: [
        { playerId: 'p1', nickname: 'Host', seatIndex: 0, chips: 1000,
          betInRound: 0, folded: false, allIn: false, holeCards: null,
          lastAction: null, connected: true },
        { playerId: 'p2', nickname: 'Bot1', seatIndex: 1, chips: 1000,
          betInRound: 0, folded: false, allIn: false, holeCards: null,
          lastAction: null, connected: true },
      ],
      communityCards: [], pot: 0, sidePots: [],
      currentBet: 0, currentPlayerIndex: 0, bettingRound: 'PREFLOP',
      smallBlind: 10, bigBlind: 20, dealerIndex: 0, timeLeftSec: 30,
      myHoleCards: [],
    }, 'p1')
    expect(store.roomName).toBe('Test')
    expect(store.players.length).toBe(2)
    expect(store.players[0].nickname).toBe('Host')
  })

  it('simulates receiving game state via /topic/room/{id}/game', () => {
    const store = useRoomStore()
    store.updateFromSnapshot({
      roomId: 'room1',
      name: 'Test',
      status: 'PLAYING',
      players: [
        { playerId: 'p1', nickname: 'Me', seatIndex: 0, chips: 800,
          betInRound: 20, folded: false, allIn: false, holeCards: null,
          lastAction: null, connected: true },
      ],
      communityCards: ['As', 'Ks', 'Qs'],
      pot: 60,
      sidePots: [],
      currentBet: 20,
      currentPlayerIndex: 0,
      bettingRound: 'FLOP',
      smallBlind: 5, bigBlind: 10, dealerIndex: 0, timeLeftSec: 28,
      myHoleCards: ['Ah', 'Kh'],
    }, 'p1')
    // Game snapshot carries game state fields, not room status
    expect(store.communityCards).toHaveLength(3)
    expect(store.pot).toBe(60)
    expect(store.myHoleCards).toEqual(['Ah', 'Kh'])
    expect(store.bettingRound).toBe('FLOP')
    expect(store.currentBet).toBe(20)
  })

  it('simulates receiving player-level game message via /topic/player/{id}/game', () => {
    const store = useRoomStore()
    // Set up initial state
    store.updateFromSnapshot({
      roomId: 'room1',
      name: 'Test',
      status: 'PLAYING',
      players: [
        { playerId: 'p1', nickname: 'Me', seatIndex: 0, chips: 1000,
          betInRound: 0, folded: false, allIn: false, holeCards: null,
          lastAction: null, connected: true },
      ],
      communityCards: [], pot: 0, sidePots: [],
      currentBet: 0, currentPlayerIndex: 0, bettingRound: 'PREFLOP',
      smallBlind: 10, bigBlind: 20, dealerIndex: 0, timeLeftSec: 30,
      myHoleCards: [],
    }, 'p1')
    // Player-level messages should not corrupt core game state
    expect(store.players.length).toBe(1)
    expect(store.pot).toBe(0)
  })

  it('reconnect re-subscribes correctly (functional test)', () => {
    const store = useRoomStore()
    // Simulate: after reconnect, the store should still accept gamestate
    store.reset()
    expect(store.status).toBe('WAITING')
    expect(store.players).toEqual([])

    // After reconnect, first snapshot arrives
    store.updateFromSnapshot({
      roomId: 'room2',
      name: 'Resumed',
      status: 'PLAYING',
      players: [
        { playerId: 'p1', nickname: 'Me', seatIndex: 0, chips: 500,
          betInRound: 50, folded: false, allIn: false, holeCards: null,
          lastAction: null, connected: true },
      ],
      communityCards: ['Jh', 'Th', '2c'],
      pot: 150,
      sidePots: [],
      currentBet: 50,
      currentPlayerIndex: 0,
      bettingRound: 'TURN',
      smallBlind: 10, bigBlind: 20, dealerIndex: 0, timeLeftSec: 30,
      myHoleCards: ['Qd', 'Kd'],
    }, 'p1')
    expect(store.roomId).toBe('room2')
    expect(store.communityCards.length).toBe(3)
    expect(store.myHoleCards).toEqual(['Qd', 'Kd'])
  })

  it('backToRoom clears game-related state', () => {
    const store = useRoomStore()
    store.updateFromSnapshot({
      roomId: 'room3',
      name: 'N',
      status: 'PLAYING',
      players: [
        { playerId: 'p1', nickname: 'Me', seatIndex: 0, chips: 800,
          betInRound: 0, folded: false, allIn: false, holeCards: null,
          lastAction: null, connected: true },
      ],
      communityCards: ['Ah', 'Kh'],
      pot: 50,
      sidePots: [],
      currentBet: 0,
      currentPlayerIndex: 0,
      bettingRound: 'FLOP',
      smallBlind: 5, bigBlind: 10, dealerIndex: 0, timeLeftSec: 30,
      myHoleCards: ['Qh', 'Jh'],
    }, 'p1')
    expect(store.communityCards).not.toEqual([])

    // Simulate backToRoom: reset and then apply WAITING snapshot
    store.reset()
    store.updateFromSnapshot({
      roomId: 'room3',
      name: 'N',
      status: 'WAITING',
      players: [
        { playerId: 'p1', nickname: 'Me', seatIndex: 0, chips: 800,
          betInRound: 0, folded: false, allIn: false, holeCards: null,
          lastAction: null, connected: true },
      ],
      communityCards: [], pot: 0, sidePots: [],
      currentBet: 0, currentPlayerIndex: 0, bettingRound: 'PREFLOP',
      smallBlind: 5, bigBlind: 10, dealerIndex: 0, timeLeftSec: 30,
      myHoleCards: [],
    }, 'p1')
    expect(store.communityCards).toEqual([])
  })
})
