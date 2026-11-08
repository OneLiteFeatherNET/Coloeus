// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { h, nextTick } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import AppButton from '../../../app/components/ui/AppButton.vue'
import AppCheckbox from '../../../app/components/ui/AppCheckbox.vue'
import AppDialog from '../../../app/components/ui/AppDialog.vue'
import AppFormField from '../../../app/components/ui/AppFormField.vue'
import AppSegmentedButton from '../../../app/components/ui/AppSegmentedButton.vue'

// The Material Design 3 building blocks: variants, the text field's error wiring, the segmented choice, the dialog's
// focus return and the checkbox. Each test mounts its own component; nothing is shared between tests.
const mounted: Array<{ unmount: () => void }> = []

afterEach(() => {
  for (const wrapper of mounted.splice(0)) {
    wrapper.unmount()
  }
  document.body.innerHTML = ''
})

describe('AppButton variants', () => {
  it('fills the default variant with the primary role', () => {
    const wrapper = mount(AppButton, { slots: { default: 'Speichern' } })

    expect(wrapper.classes()).toContain('bg-primary')
  })

  it('draws the outlined variant with the outline role and no fill', () => {
    const wrapper = mount(AppButton, { props: { variant: 'outlined' }, slots: { default: 'Abbrechen' } })

    expect(wrapper.classes()).toContain('border-outline')
    expect(wrapper.classes()).not.toContain('bg-primary')
  })

  it('fills the danger variant with the error role', () => {
    const wrapper = mount(AppButton, { props: { variant: 'danger' }, slots: { default: 'Zurücksetzen' } })

    expect(wrapper.classes()).toContain('bg-error')
  })

  it('carries the state layer that the MD3 hover and focus states use', () => {
    const wrapper = mount(AppButton, { slots: { default: 'Speichern' } })

    expect(wrapper.classes()).toContain('md-state')
  })
})

describe('AppFormField with an invalid control', () => {
  it('shows the error text and marks the control as invalid', () => {
    const wrapper = mount(AppFormField, {
      props: { label: 'Betrag', error: 'Der Betrag hat zu viele Nachkommastellen.' },
      slots: {
        default: (slot: { id: string, describedby?: string, invalid: boolean }) => h('input', {
          id: slot.id,
          'aria-invalid': String(slot.invalid),
          'aria-describedby': slot.describedby
        })
      }
    })

    const input = wrapper.find('input')
    expect(input.attributes('aria-invalid')).toBe('true')
    const errorId = (input.attributes('aria-describedby') ?? '').split(' ').at(-1) ?? ''
    expect(wrapper.find(`#${errorId}`).text()).toBe('Der Betrag hat zu viele Nachkommastellen.')
  })

  it('does not mark a valid control as invalid', () => {
    const wrapper = mount(AppFormField, {
      props: { label: 'Grund' },
      slots: {
        default: (slot: { id: string, invalid: boolean }) => h('input', { id: slot.id, 'aria-invalid': String(slot.invalid) })
      }
    })

    expect(wrapper.find('input').attributes('aria-invalid')).toBe('false')
  })
})

describe('AppSegmentedButton', () => {
  const options = [{ value: '7D', label: '7 Tage' }, { value: 'ALL', label: 'Gesamt' }]

  it('reports only the selected option as pressed', () => {
    const wrapper = mount(AppSegmentedButton, { props: { label: 'Zeitraum', modelValue: '7D', options } })
    mounted.push(wrapper)

    const pressed = wrapper.findAll('button').map(button => button.attributes('aria-pressed'))
    expect(pressed).toEqual(['true', 'false'])
  })

  it('names the group after its label', () => {
    const wrapper = mount(AppSegmentedButton, { props: { label: 'Zeitraum', modelValue: '7D', options } })
    mounted.push(wrapper)

    expect(wrapper.find('[role="group"]').attributes('aria-label')).toBe('Zeitraum')
  })

  it('emits the value of the option that was clicked', async () => {
    const wrapper = mount(AppSegmentedButton, { props: { label: 'Zeitraum', modelValue: '7D', options } })
    mounted.push(wrapper)

    await wrapper.findAll('button')[1]?.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['ALL']])
  })
})

describe('AppDialog focus', () => {
  it('returns the focus to the element that opened the dialog when it closes', async () => {
    const opener = document.createElement('button')
    opener.textContent = 'Anpassen'
    document.body.appendChild(opener)
    opener.focus()

    const wrapper = mount(AppDialog, {
      props: { open: false, title: 'Anpassen' },
      attachTo: document.body
    })
    mounted.push(wrapper)

    await wrapper.setProps({ open: true })
    await nextTick()
    await wrapper.setProps({ open: false })
    await nextTick()

    expect(document.activeElement).toBe(opener)
  })
})

describe('AppCheckbox', () => {
  it('emits the new checked state when the box changes', async () => {
    const wrapper = mount(AppCheckbox, { props: { modelValue: false, label: 'Protokollieren' } })

    await wrapper.find('input').setValue(true)

    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('names the checkbox through its label', () => {
    const wrapper = mount(AppCheckbox, { props: { modelValue: false, label: 'Protokollieren' } })

    expect(wrapper.find('label').text()).toBe('Protokollieren')
    expect(wrapper.find('input').attributes('type')).toBe('checkbox')
  })
})
