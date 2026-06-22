import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PlayingCard from '../PlayingCard.vue'

describe('PlayingCard', () => {
  it('renders card back when card is null', () => {
    const wrapper = mount(PlayingCard, {
      props: { card: null, faceUp: true },
    })
    expect(wrapper.find('[data-test="card-back"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="card-face"]').exists()).toBe(false)
  })

  it('renders card back when faceUp is false', () => {
    const wrapper = mount(PlayingCard, {
      props: { card: 'Ah', faceUp: false },
    })
    expect(wrapper.find('[data-test="card-back"]').exists()).toBe(true)
  })

  it('renders card face when faceUp is true and card is provided', () => {
    const wrapper = mount(PlayingCard, {
      props: { card: 'Ah', faceUp: true },
    })
    expect(wrapper.find('[data-test="card-face"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('A')
    expect(wrapper.text()).toContain('♥')
  })

  it('renders spades as black ♠', () => {
    const wrapper = mount(PlayingCard, {
      props: { card: 'Ks', faceUp: true },
    })
    const face = wrapper.find('[data-test="card-face"]')
    expect(face.classes()).toContain('black')
  })

  it('renders hearts as red ♥', () => {
    const wrapper = mount(PlayingCard, {
      props: { card: 'Qh', faceUp: true },
    })
    const face = wrapper.find('[data-test="card-face"]')
    expect(face.classes()).toContain('red')
  })

  it('renders ten as T', () => {
    const wrapper = mount(PlayingCard, {
      props: { card: 'Td', faceUp: true },
    })
    expect(wrapper.text()).toContain('T')
  })

  it('handles small card size', () => {
    const wrapper = mount(PlayingCard, {
      props: { card: 'Ah', faceUp: true, size: 'sm' },
    })
    const card = wrapper.find('.card')
    expect(card.classes()).toContain('sm')
  })
})
