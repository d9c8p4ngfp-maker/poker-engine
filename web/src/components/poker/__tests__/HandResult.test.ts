import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import HandResult from '../HandResult.vue'

describe('HandResult', () => {
  it('displays winners', () => {
    const wrapper = mount(HandResult, {
      props: {
        winners: [
          { playerId: 'A', nickname: 'Alice', handName: 'Flush', amount: 500 },
        ],
        isOwner: false,
      },
    })
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('Flush')
    expect(wrapper.text()).toContain('500')
  })

  it('shows multiple winners', () => {
    const wrapper = mount(HandResult, {
      props: {
        winners: [
          { playerId: 'A', nickname: 'Alice', handName: 'Two Pair', amount: 300 },
          { playerId: 'B', nickname: 'Bob', handName: 'Two Pair', amount: 200 },
        ],
        isOwner: false,
      },
    })
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('Bob')
  })

  it('shows next hand button for owner', () => {
    const wrapper = mount(HandResult, {
      props: {
        winners: [
          { playerId: 'A', nickname: 'Alice', handName: 'High Card', amount: 100 },
        ],
        isOwner: true,
      },
    })
    expect(wrapper.text()).toContain('下一局')
  })

  it('hides next hand button for non-owner', () => {
    const wrapper = mount(HandResult, {
      props: {
        winners: [
          { playerId: 'A', nickname: 'Alice', handName: 'High Card', amount: 100 },
        ],
        isOwner: false,
      },
    })
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('emits next-hand event', async () => {
    const wrapper = mount(HandResult, {
      props: {
        winners: [
          { playerId: 'A', nickname: 'Alice', handName: 'High Card', amount: 100 },
        ],
        isOwner: true,
      },
    })
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('next-hand')).toBeTruthy()
  })
})
