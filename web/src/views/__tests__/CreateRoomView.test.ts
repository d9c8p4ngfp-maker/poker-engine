import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CreateRoomView from '../CreateRoomView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', component: { template: '<div>home</div>' } }],
})

describe('CreateRoomView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // mock localStorage for CreateRoomView
    if (!globalThis.localStorage) {
      Object.defineProperty(globalThis, 'localStorage', {
        value: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
        writable: true,
      })
    }
  })

  it('renders the create room form', () => {
    const wrapper = mount(CreateRoomView, {
      global: { plugins: [router] },
    })
    expect(wrapper.text()).toContain('创建房间')
  })

  it('has input fields for room config', () => {
    const wrapper = mount(CreateRoomView, {
      global: { plugins: [router] },
    })
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThanOrEqual(3)
  })

  it('has a submit button with create text', () => {
    const wrapper = mount(CreateRoomView, {
      global: { plugins: [router] },
    })
    const buttons = wrapper.findAll('button')
    const createBtn = buttons.find(b => b.text().includes('创建房间'))
    expect(createBtn).toBeTruthy()
  })
})
