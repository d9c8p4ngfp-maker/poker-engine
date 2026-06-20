import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface PlayerView {
  playerId: string
  nickname: string
  seatIndex: number
  chips: number
  betInRound: number
  folded: boolean
  allIn: boolean
  holeCards: string[] | null
  lastAction: string | null
  connected: boolean
  borrowCount?: number
  owner?: boolean
}

export interface RoomSnapshot {
  roomId: string
  name: string
  status: 'WAITING' | 'PLAYING' | 'FINISHED'
  players: PlayerView[]
  communityCards: string[]
  pot: number
  sidePots: { amount: number; eligiblePlayerIds: string[] }[]
  currentBet: number
  currentPlayerIndex: number
  bettingRound: 'PREFLOP' | 'FLOP' | 'TURN' | 'RIVER' | 'SHOWDOWN'
  smallBlind: number
  bigBlind: number
  initialChips?: number
  dealerIndex: number
  timeLeftSec: number
  myHoleCards?: string[]
  winners?: { playerId: string; handName: string; amount: number }[]
}

export const useRoomStore = defineStore('room', () => {
  const roomId = ref<string | null>(null)
  const roomName = ref('')
  const status = ref<'WAITING' | 'PLAYING' | 'FINISHED'>('WAITING')
  const players = ref<PlayerView[]>([])
  const communityCards = ref<string[]>([])
  const pot = ref(0)
  const sidePots = ref<{ amount: number; eligiblePlayerIds: string[] }[]>([])
  const currentBet = ref(0)
  const currentPlayerIndex = ref(-1)
  const bettingRound = ref<string>('PREFLOP')
  const smallBlind = ref(10)
  const bigBlind = ref(20)
  const maxSeats = ref(8)
  const initialChips = ref(1000)
  const minRaise = ref(20)
  const dealerIndex = ref(0)
  const timeLeftSec = ref(0)
  const myHoleCards = ref<string[]>([])
  const winners = ref<{ playerId: string; handName: string; amount: number }[] | null>(null)
  const gameOver = ref(false)
  const leaderboard = ref<{ playerId: string; nickname: string; chips: number; borrowCount?: number; borrowed?: number }[]>([])
  const bustedPlayerIds = ref<string[]>([])
  const messages = ref<{ type: string; text: string; ts: number }[]>([])

  function updateFromSnapshot(snapshot: RoomSnapshot, _myPlayerId: string) {
    roomId.value = snapshot.roomId
    roomName.value = snapshot.name
    status.value = snapshot.status
    // Merge incoming game-engine players with existing room-player metadata.
    // The game engine doesn't know about owner / borrowCount / connected —
    // those live on the Room model, not GamePlayerState. Merge them from
    // whatever we already have locally so they survive every snapshot update.
    const existingMap = new Map(players.value.map(p => [p.playerId, p]))
    players.value = (snapshot.players || []).map(p => {
      const old = existingMap.get(p.playerId)
      return {
        ...p,
        owner: old?.owner ?? (p as any).owner ?? false,
        borrowCount: old?.borrowCount ?? (p as any).borrowCount ?? 0,
        connected: old?.connected ?? (p as any).connected ?? true,
      }
    })
    communityCards.value = snapshot.communityCards
    pot.value = snapshot.pot
    sidePots.value = snapshot.sidePots
    currentBet.value = snapshot.currentBet
    currentPlayerIndex.value = snapshot.currentPlayerIndex
    bettingRound.value = snapshot.bettingRound
    smallBlind.value = snapshot.smallBlind
    bigBlind.value = snapshot.bigBlind
    if ((snapshot as any).initialChips) initialChips.value = (snapshot as any).initialChips
    dealerIndex.value = snapshot.dealerIndex
    minRaise.value = (snapshot as any).minRaise || snapshot.bigBlind || 20
    timeLeftSec.value = snapshot.timeLeftSec
    if (snapshot.myHoleCards) myHoleCards.value = snapshot.myHoleCards
    if (snapshot.winners) winners.value = snapshot.winners
  }

  function addSystemMessage(text: string) {
    messages.value.push({ type: 'system', text, ts: Date.now() })
  }

  function setGameOver(data: { winners: any[]; leaderboard: any[]; bustedPlayerIds: string[] }) {
    winners.value = data.winners
    gameOver.value = true
    leaderboard.value = data.leaderboard
    bustedPlayerIds.value = data.bustedPlayerIds
    // Keep current status so the poker table stays visible behind the GameOver overlay.
    // Transition to WAITING happens in handleBackToRoom() when user clicks "返回房间".
  }

  function reset() {
    roomId.value = null
    roomName.value = ''
    status.value = 'WAITING'
    players.value = []
    communityCards.value = []
    pot.value = 0
    sidePots.value = []
    currentBet.value = 0
    currentPlayerIndex.value = -1
    bettingRound.value = 'PREFLOP'
    smallBlind.value = 10
    bigBlind.value = 20
    maxSeats.value = 8
    initialChips.value = 1000
    minRaise.value = 20
    dealerIndex.value = 0
    timeLeftSec.value = 0
    myHoleCards.value = []
    winners.value = null
    gameOver.value = false
    leaderboard.value = []
    bustedPlayerIds.value = []
    messages.value = []
  }

  return {
    roomId, roomName, status, players, communityCards, pot, sidePots,
    currentBet, currentPlayerIndex, bettingRound, smallBlind, bigBlind,
    maxSeats, initialChips, minRaise, dealerIndex, timeLeftSec, myHoleCards, winners,
    gameOver, leaderboard, bustedPlayerIds, messages,
    updateFromSnapshot, addSystemMessage, setGameOver, reset,
  }
})
