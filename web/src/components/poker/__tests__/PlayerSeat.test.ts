import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PlayerSeat from '../PlayerSeat.vue'

const defaultProps = {
  playerId: 'p1',
  nickname: 'Alice',
  chips: 1000,
  betInRound: 0,
  folded: false,
  allIn: false,
  isDealer: false,
  isCurrentPlayer: false,
  isMe: false,
  holeCards: ['Ah', 'Kh'] as string[] | null,
}

describe('PlayerSeat', () => {
  it('renders nickname and chips', () => {
    const wrapper = mount(PlayerSeat, { props: defaultProps })
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('1000')
  })

  it('shows dealer badge', () => {
    const wrapper = mount(PlayerSeat, {
      props: { ...defaultProps, isDealer: true },
    })
    expect(wrapper.text()).toContain('D')
  })

  it('shows folded state', () => {
    const wrapper = mount(PlayerSeat, {
      props: { ...defaultProps, folded: true },
    })
    expect(wrapper.find('[data-test="folded"]').exists()).toBe(true)
  })

  it('shows all-in state', () => {
    const wrapper = mount(PlayerSeat, {
      props: { ...defaultProps, allIn: true },
    })
    expect(wrapper.text()).toContain('ALL IN')
  })

  it('highlights current player', () => {
    const wrapper = mount(PlayerSeat, {
      props: { ...defaultProps, isCurrentPlayer: true },
    })
    const seat = wrapper.find('[data-test="seat"]')
    expect(seat.classes()).toContain('cur')
  })

  it('shows bet amount when bet > 0', () => {
    const wrapper = mount(PlayerSeat, {
      props: { ...defaultProps, betInRound: 50 },
    })
    expect(wrapper.text()).toContain('50')
  })

  it('shows hole cards for self', () => {
    const wrapper = mount(PlayerSeat, {
      props: { ...defaultProps, isMe: true },
    })
    const cards = wrapper.findAll('[data-test="card-face"]')
    expect(cards.length).toBe(2)
  })

  it('shows card backs for opponents', () => {
    const wrapper = mount(PlayerSeat, {
      props: { ...defaultProps, isMe: false },
    })
    const backs = wrapper.findAll('[data-test="card-back"]')
    expect(backs.length).toBe(2)
  })

  it('shows no cards when holeCards is null', () => {
    const wrapper = mount(PlayerSeat, {
      props: { ...defaultProps, isMe: true, holeCards: null },
    })
    const cards = wrapper.findAll('[data-test="card-face"]')
    expect(cards.length).toBe(0)
  })
})
