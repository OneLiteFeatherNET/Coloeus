// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { h } from 'vue'
import { describe, expect, it } from 'vitest'
import AppBarChart from '../../../app/components/ui/AppBarChart.vue'
import AppButton from '../../../app/components/ui/AppButton.vue'
import AppDialog from '../../../app/components/ui/AppDialog.vue'
import AppEmptyState from '../../../app/components/ui/AppEmptyState.vue'
import AppFormField from '../../../app/components/ui/AppFormField.vue'
import AppMoney from '../../../app/components/ui/AppMoney.vue'
import AppPageHeader from '../../../app/components/ui/AppPageHeader.vue'
import AppStatus from '../../../app/components/ui/AppStatus.vue'
import AppTable from '../../../app/components/ui/AppTable.vue'

describe('AppButton', () => {
  it('is a disabled button when asked to be', () => {
    const wrapper = mount(AppButton, { props: { disabled: true }, slots: { default: 'Speichern' } })

    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })

  it('is a button of type button by default, so that it never submits a form by accident', () => {
    const wrapper = mount(AppButton, { slots: { default: 'Abbrechen' } })

    expect(wrapper.find('button').attributes('type')).toBe('button')
  })

  it('reports its pressed state to assistive technology when given one', () => {
    const wrapper = mount(AppButton, { props: { pressed: true }, slots: { default: '7 Tage' } })

    expect(wrapper.find('button').attributes('aria-pressed')).toBe('true')
  })
})

describe('AppMoney', () => {
  it('shows a negative decimal string with a comma and the currency code', () => {
    const wrapper = mount(AppMoney, { props: { amount: '-0.10', currency: 'COINS', fractionDigits: 2 } })

    expect(wrapper.text()).toBe('-0,10 COINS')
  })

  it('keeps eight fraction digits exactly', () => {
    const wrapper = mount(AppMoney, { props: { amount: '12.34567890', currency: 'BTC', fractionDigits: 8 } })

    expect(wrapper.text()).toBe('12,34567890 BTC')
  })
})

describe('AppFormField', () => {
  it('links the hint and the error to the control through aria-describedby', () => {
    const wrapper = mount(AppFormField, {
      props: { label: 'Betrag', hint: 'Dezimalzahl', error: 'Ungültig' },
      slots: {
        default: (slot: { id: string, describedby?: string }) => h('input', { id: slot.id, 'aria-describedby': slot.describedby })
      }
    })

    const input = wrapper.find('input')
    const described = (input.attributes('aria-describedby') ?? '').split(' ')
    expect(described).toHaveLength(2)
    expect(wrapper.find(`#${described[0]}`).text()).toBe('Dezimalzahl')
    expect(wrapper.find(`#${described[1]}`).text()).toBe('Ungültig')
  })

  it('labels the control through its id', () => {
    const wrapper = mount(AppFormField, {
      props: { label: 'Grund' },
      slots: {
        default: (slot: { id: string }) => h('input', { id: slot.id })
      }
    })

    expect(wrapper.find('label').attributes('for')).toBe(wrapper.find('input').attributes('id'))
  })
})

describe('AppStatus', () => {
  it('renders the message in a polite live region', () => {
    const wrapper = mount(AppStatus, { props: { message: 'Das Konto wurde zurückgesetzt.', tone: 'success' } })

    expect(wrapper.find('[aria-live]').attributes('aria-live')).toBe('polite')
    expect(wrapper.text()).toBe('Das Konto wurde zurückgesetzt.')
  })

  it('keeps the live region in the page while it has no message', () => {
    const wrapper = mount(AppStatus, { props: { message: '' } })

    expect(wrapper.find('[aria-live="polite"]').exists()).toBe(true)
  })
})

describe('AppTable', () => {
  const columns = [{ key: 'name', label: 'Name' }, { key: 'amount', label: 'Betrag', align: 'end' as const }]
  const rows = [{ id: '1', name: 'Alex', amount: '1,00' }]

  it('marks every header cell with scope col and gives the table a caption', () => {
    const wrapper = mount(AppTable, {
      props: { caption: 'Konten', columns, rows, rowKey: 'id' },
      slots: { cell: ({ row, column }: { row: Record<string, unknown>, column: string }) => String(row[column]) }
    })

    expect(wrapper.find('caption').text()).toBe('Konten')
    expect(wrapper.findAll('th').every(th => th.attributes('scope') === 'col')).toBe(true)
  })

  it('renders the same rows as a stacked list for narrow screens', () => {
    const wrapper = mount(AppTable, {
      props: { caption: 'Konten', columns, rows, rowKey: 'id' },
      slots: { cell: ({ row, column }: { row: Record<string, unknown>, column: string }) => String(row[column]) }
    })

    expect(wrapper.find('ul').findAll('dt').map(dt => dt.text())).toEqual(['Name', 'Betrag'])
  })
})

describe('AppDialog', () => {
  it('names the dialog after its title', () => {
    const wrapper = mount(AppDialog, { props: { open: false, title: 'Konto zurücksetzen' } })

    const dialog = wrapper.find('dialog')
    expect(wrapper.find(`#${dialog.attributes('aria-labelledby')}`).text()).toBe('Konto zurücksetzen')
  })

  it('emits close when the browser asks to cancel it, which is how Escape arrives', async () => {
    const wrapper = mount(AppDialog, { props: { open: false, title: 'Anpassen' } })

    await wrapper.find('dialog').trigger('cancel')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})

describe('AppPageHeader', () => {
  it('renders the title as the only level one heading', () => {
    const wrapper = mount(AppPageHeader, { props: { title: 'Konten', description: 'Suche' } })

    expect(wrapper.findAll('h1').map(heading => heading.text())).toEqual(['Konten'])
  })
})

describe('AppEmptyState', () => {
  it('shows the text it was given', () => {
    const wrapper = mount(AppEmptyState, { slots: { default: 'Noch keine Währungen angelegt.' } })

    expect(wrapper.text()).toBe('Noch keine Währungen angelegt.')
  })
})

describe('AppBarChart', () => {
  it('is one image with the accessible name it was given', () => {
    const wrapper = mount(AppBarChart, {
      props: {
        label: 'Balkendiagramm: COINS, letzte 7 Tage',
        labels: ['2026-11-02', '2026-11-03'],
        series: [{ name: 'erzeugt', values: ['1.00', '0'], pattern: 'solid' }]
      }
    })

    expect(wrapper.find('svg').attributes('role')).toBe('img')
    expect(wrapper.find('svg').attributes('aria-label')).toBe('Balkendiagramm: COINS, letzte 7 Tage')
  })

  it('draws the bars of the zero-filled days with zero height', () => {
    const wrapper = mount(AppBarChart, {
      props: {
        label: 'Verlauf',
        labels: ['a', 'b'],
        series: [{ name: 'erzeugt', values: ['2.00', '0'], pattern: 'solid' }]
      }
    })

    const heights = wrapper.findAll('g rect').map(rect => rect.attributes('height'))
    expect(heights).toEqual(['200', '0'])
  })
})
