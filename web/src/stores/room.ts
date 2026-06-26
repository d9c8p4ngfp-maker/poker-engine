import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

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
  inGame?: boolean
}

export interface RoomSnapshot {
  roomId: string
  name: string
  status?: 'WAITING' | 'PLAYING' | 'FINISHED'
  players: PlayerView[]
  communityCards: string[]
  pot: number
  sidePots: { amount: number; eligiblePlayerIds: string[] }[]
  currentBet: number
  currentPlayerIndex: number
  currentPlayerId?: string
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
  const currentPlayerId = ref<string | null>(null)
  const bettingRound = ref<string>('PREFLOP')
  const smallBlind = ref(10)
  const bigBlind = ref(20)
  const maxSeats = ref(8)
  const minPlayers = ref(2)
  const initialChips = ref(1000)
  const minRaise = ref(20)
  const dealerIndex = ref(0)
  const dealerPlayerId = ref<string | null>(null)
  const timeLeftSec = ref(0)
  const myHoleCards = ref<string[]>([])
  const winners = ref<{ playerId: string; handName: string; amount: number }[] | null>(null)
  const gameOver = ref(false)
  const leaderboard = ref<{ playerId: string; nickname: string; chips: number; borrowCount?: number; borrowed?: number; netChips?: number }[]>([])
  const bustedPlayerIds = ref<string[]>([])
  const messages = ref<{ type: string; text: string; ts: number }[]>([])
  const readyPlayers = ref<string[]>([])
  const pendingGameOver = ref<{ winners: any[]; leaderboard: any[]; bustedPlayerIds: string[] } | null>(null)

  const activeCount = computed(() => players.value.filter(p => p.chips > 0).length)
  const readyCount = computed(() => readyPlayers.value.length)
  const allReady = computed(() => activeCount.value > 0 && readyCount.value >= activeCount.value)
  const hasPendingGameOver = computed(() => pendingGameOver.value !== null)


  function receiveReadyStatus(data: { readyPlayers: string[]; totalActive: number; allReady: boolean }) {
    readyPlayers.value = data.readyPlayers || []
  }

  function updateFromSnapshot(snapshot: RoomSnapshot, _myPlayerId: string) {
    if (snapshot.roomId != null) roomId.value = snapshot.roomId
    if (snapshot.name != null) roomName.value = snapshot.name
    players.value = (snapshot.players || []).map(p => ({
      playerId: p.playerId, nickname: p.nickname, seatIndex: p.seatIndex,
      chips: p.chips, betInRound: p.betInRound, folded: p.folded, allIn: p.allIn,
      holeCards: p.holeCards, lastAction: p.lastAction,
      connected: (p as any).connected ?? true,
      borrowCount: (p as any).borrowCount ?? 0,
      owner: (p as any).owner ?? false,
      inGame: (p as any).inGame,
    }))
    communityCards.value = snapshot.communityCards
    pot.value = snapshot.pot
    sidePots.value = snapshot.sidePots
    currentBet.value = snapshot.currentBet
    currentPlayerIndex.value = snapshot.currentPlayerIndex
    currentPlayerId.value = snapshot.currentPlayerId ?? null
    bettingRound.value = snapshot.bettingRound
    smallBlind.value = snapshot.smallBlind
    bigBlind.value = snapshot.bigBlind
    if ((snapshot as any).initialChips) initialChips.value = (snapshot as any).initialChips
    dealerIndex.value = snapshot.dealerIndex
    dealerPlayerId.value = (snapshot as any).dealerPlayerId ?? null
    minRaise.value = (snapshot as any).minRaise || snapshot.bigBlind || 20
    timeLeftSec.value = snapshot.timeLeftSec
    if (snapshot.myHoleCards) myHoleCards.value = snapshot.myHoleCards
    if (snapshot.winners) winners.value = snapshot.winners
  }

  function addSystemMessage(text: string) {
    messages.value.push({ type: 'system', text, ts: Date.now() })
  }

  function setGameOver(data: { winners: any[]; leaderboard: any[]; bustedPlayerIds: string[]; deferred?: boolean }) {
    if (data.deferred) {
      // Store pending game over data so HandResult can show "查看最终排名" button.
      // The 6s auto-broadcast (without deferred flag) will auto-apply if user doesn't click first.
      pendingGameOver.value = data
      return
    }
    pendingGameOver.value = null
    winners.value = data.winners
    gameOver.value = true
    leaderboard.value = data.leaderboard
    bustedPlayerIds.value = data.bustedPlayerIds
    // Keep current status so the poker table stays visible behind the GameOver overlay.
    // Transition to WAITING happens in handleBackToRoom() when user clicks "返回房间".
  }

  function showGameOver() {
    const d = pendingGameOver.value
    if (!d) return
    pendingGameOver.value = null
    winners.value = d.winners
    gameOver.value = true
    leaderboard.value = d.leaderboard
    bustedPlayerIds.value = d.bustedPlayerIds
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
    currentPlayerId.value = null
    bettingRound.value = 'PREFLOP'
    smallBlind.value = 10
    bigBlind.value = 20
    maxSeats.value = 8
    minPlayers.value = 2
    initialChips.value = 1000
    minRaise.value = 20
    dealerIndex.value = 0
    dealerPlayerId.value = null
    timeLeftSec.value = 0
    myHoleCards.value = []
    winners.value = null
    gameOver.value = false
    leaderboard.value = []
    bustedPlayerIds.value = []
    messages.value = []
    readyPlayers.value = []
    pendingGameOver.value = null
  }

  return {
    roomId, roomName, status, players, communityCards, pot, sidePots,
    currentBet, currentPlayerIndex, currentPlayerId, bettingRound, smallBlind, bigBlind,
    maxSeats, minPlayers, initialChips, minRaise, dealerIndex, dealerPlayerId, timeLeftSec, myHoleCards, winners,
    gameOver, leaderboard, bustedPlayerIds, messages,
    readyPlayers, allReady, readyCount, activeCount, pendingGameOver, hasPendingGameOver,
    updateFromSnapshot, addSystemMessage, setGameOver, showGameOver, reset, receiveReadyStatus,
  }
})
