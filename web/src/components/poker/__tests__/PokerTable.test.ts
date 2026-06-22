import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PokerTable from '../PokerTable.vue'

const mockPlayer = (id: string, seat: number) => ({
  playerId: id,
  nickname: id,
  chips: 1000,
  betInRound: 0,
  folded: false,
  allIn: false,
  seatIndex: seat,
  holeCards: null as string[] | null,
  isDealer: false,
})

describe('PokerTable', () => {
  it('renders players in seats', () => {
    const players = [mockPlayer('A', 0), mockPlayer('B', 1), mockPlayer('C', 2)]
    const wrapper = mount(PokerTable, {
      props: {
        players,
        communityCards: [],
        pot: 0,
        dealerPlayerId: null,
        currentPlayerIndex: 0,
        myPlayerId: 'A',
      },
    })
    const seats = wrapper.findAll('[data-test="seat"]')
    expect(seats.length).toBe(3)
  })

  it('shows community cards area', () => {
    const wrapper = mount(PokerTable, {
      props: {
        players: [mockPlayer('A', 0), mockPlayer('B', 1)],
        communityCards: ['Ah', 'Kh', 'Qh'],
        pot: 200,
        dealerPlayerId: null,
        currentPlayerIndex: 0,
        myPlayerId: 'A',
      },
    })
    const cards = wrapper.findAll('[data-test="card-face"]')
    expect(cards.length).toBe(3)
  })

  it('displays pot amount', () => {
    const wrapper = mount(PokerTable, {
      props: {
        players: [mockPlayer('A', 0), mockPlayer('B', 1)],
        communityCards: [],
        pot: 350,
        dealerPlayerId: null,
        currentPlayerIndex: 0,
        myPlayerId: 'A',
      },
    })
    expect(wrapper.text()).toContain('350')
  })

  it('marks dealer player', () => {
    const players = [mockPlayer('A', 0), mockPlayer('B', 1)]
    const wrapper = mount(PokerTable, {
      props: {
        players,
        communityCards: [],
        pot: 0,
        dealerPlayerId: 'A',
        currentPlayerIndex: 0,
        myPlayerId: 'A',
      },
    })
    expect(wrapper.text()).toContain('D')
  })

  it('highlights current player', () => {
    const players = [mockPlayer('A', 0), mockPlayer('B', 1)]
    const wrapper = mount(PokerTable, {
      props: {
        players,
        communityCards: [],
        pot: 0,
        dealerPlayerId: null,
        currentPlayerIndex: 1,
        myPlayerId: 'A',
      },
    })
    const seats = wrapper.findAll('[data-test="seat"]')
    expect(seats[1].classes()).toContain('cur')
  })
})
