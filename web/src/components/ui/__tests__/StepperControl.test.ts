import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StepperControl from '../StepperControl.vue'

describe('StepperControl', () => {
  it('renders label and value', () => {
    const wrapper = mount(StepperControl, {
      props: { modelValue: 5, min: 1, max: 10, label: '测试' },
    })
    expect(wrapper.text()).toContain('测试')
    expect(wrapper.text()).toContain('5')
  })

  it('increments value when plus is clicked', async () => {
    const wrapper = mount(StepperControl, {
      props: { modelValue: 5, min: 1, max: 10, label: '测试' },
    })
    await wrapper.find('[data-test="stepper-plus"]').trigger('click')
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted![0]).toEqual([6])
  })

  it('decrements value when minus is clicked', async () => {
    const wrapper = mount(StepperControl, {
      props: { modelValue: 5, min: 1, max: 10, label: '测试' },
    })
    await wrapper.find('[data-test="stepper-minus"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([4])
  })

  it('disables minus button at minimum', () => {
    const wrapper = mount(StepperControl, {
      props: { modelValue: 1, min: 1, max: 10, label: '测试' },
    })
    expect(wrapper.find('[data-test="stepper-minus"]').attributes('disabled')).toBeDefined()
  })

  it('disables plus button at maximum', () => {
    const wrapper = mount(StepperControl, {
      props: { modelValue: 10, min: 1, max: 10, label: '测试' },
    })
    expect(wrapper.find('[data-test="stepper-plus"]').attributes('disabled')).toBeDefined()
  })

  it('respects step size', async () => {
    const wrapper = mount(StepperControl, {
      props: { modelValue: 100, min: 0, max: 1000, step: 100, label: '筹码' },
    })
    await wrapper.find('[data-test="stepper-plus"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([200])
  })
})
