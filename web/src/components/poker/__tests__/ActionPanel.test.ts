import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ActionPanel from '../ActionPanel.vue'

describe('ActionPanel', () => {
  it('disables all buttons when not my turn', () => {
    const wrapper = mount(ActionPanel, {
      props: {
        isMyTurn: false,
        canCheck: false,
        canCall: false,
        canBet: false,
        canRaise: false,
        callAmount: 0,
        minRaise: 20,
        timeLeftSec: 25,
        myChips: 500,
      },
    })
    const buttons = wrapper.findAll('button')
    buttons.forEach(btn => {
      expect(btn.attributes('disabled')).toBeDefined()
    })
  })

  it('shows timer when not my turn', () => {
    const wrapper = mount(ActionPanel, {
      props: {
        isMyTurn: false,
        canCheck: false,
        canCall: false,
        canBet: false,
        canRaise: false,
        callAmount: 0,
        minRaise: 20,
        timeLeftSec: 15,
        myChips: 500,
      },
    })
    expect(wrapper.text()).toContain('15')
  })

  it('shows fold and check when can check', () => {
    const wrapper = mount(ActionPanel, {
      props: {
        isMyTurn: true,
        canCheck: true,
        canCall: false,
        canBet: false,
        canRaise: false,
        callAmount: 0,
        minRaise: 20,
        timeLeftSec: 0,
        myChips: 500,
      },
    })
    expect(wrapper.text()).toContain('Fold')
    expect(wrapper.text()).toContain('Check')
    expect(wrapper.find('button').attributes('disabled')).toBeUndefined()
  })

  it('shows fold and call when must call', () => {
    const wrapper = mount(ActionPanel, {
      props: {
        isMyTurn: true,
        canCheck: false,
        canCall: true,
        canBet: false,
        canRaise: false,
        callAmount: 30,
        minRaise: 20,
        timeLeftSec: 0,
        myChips: 500,
      },
    })
    expect(wrapper.text()).toContain('Call 30')
  })

  it('shows bet input when can bet', () => {
    const wrapper = mount(ActionPanel, {
      props: {
        isMyTurn: true,
        canCheck: false,
        canCall: false,
        canBet: true,
        canRaise: false,
        callAmount: 0,
        minRaise: 20,
        timeLeftSec: 0,
        myChips: 500,
      },
    })
    expect(wrapper.text()).toContain('Bet')
    const slider = wrapper.find('input[type="range"]')
    expect(slider.exists()).toBe(true)
  })

  it('emits fold event', async () => {
    const wrapper = mount(ActionPanel, {
      props: {
        isMyTurn: true,
        canCheck: false,
        canCall: true,
        canBet: false,
        canRaise: false,
        callAmount: 20,
        minRaise: 20,
        timeLeftSec: 0,
        myChips: 500,
      },
    })
    await wrapper.find('[data-test="btn-fold"]').trigger('click')
    expect(wrapper.emitted('action')?.[0]).toEqual([{ type: 'FOLD' }])
  })

  it('caps bet amount to myChips', () => {
    const wrapper = mount(ActionPanel, {
      props: {
        isMyTurn: true,
        canCheck: false,
        canCall: false,
        canBet: true,
        canRaise: false,
        callAmount: 0,
        minRaise: 20,
        timeLeftSec: 0,
        myChips: 100,
      },
    })
    const slider = wrapper.find('input[type="range"]')
    expect(Number(slider.attributes('max'))).toBe(100)
  })
})
