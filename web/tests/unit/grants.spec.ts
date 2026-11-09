import { describe, expect, it } from 'vitest'
import { groupGrants } from '~/utils/grants'

const labels: Record<string, string> = {
  READ: 'Lesen',
  MINT: 'Erzeugen',
  BURN: 'Vernichten',
  TRANSFER_ON_BEHALF: 'Überweisen im Auftrag'
}

describe('groupGrants', () => {
  it('groups grants by currency in the order of first appearance', () => {
    const grants = [
      { currency: 'EUR', permission: 'READ' },
      { currency: 'BTC', permission: 'MINT' },
      { currency: 'EUR', permission: 'MINT' }
    ]

    expect(groupGrants(grants, labels)).toEqual([
      { currency: 'EUR', permissions: ['Lesen', 'Erzeugen'] },
      { currency: 'BTC', permissions: ['Erzeugen'] }
    ])
  })

  it('orders permissions READ, MINT, BURN, TRANSFER_ON_BEHALF', () => {
    const grants = [
      { currency: 'EUR', permission: 'TRANSFER_ON_BEHALF' },
      { currency: 'EUR', permission: 'BURN' },
      { currency: 'EUR', permission: 'MINT' },
      { currency: 'EUR', permission: 'READ' }
    ]

    expect(groupGrants(grants, labels)).toEqual([
      { currency: 'EUR', permissions: ['Lesen', 'Erzeugen', 'Vernichten', 'Überweisen im Auftrag'] }
    ])
  })

  it('falls back to the raw code for an unknown permission', () => {
    const grants = [{ currency: 'EUR', permission: 'FREEZE' }]

    expect(groupGrants(grants, labels)).toEqual([
      { currency: 'EUR', permissions: ['FREEZE'] }
    ])
  })

  it('returns an empty array for no grants', () => {
    expect(groupGrants([], labels)).toEqual([])
  })
})
