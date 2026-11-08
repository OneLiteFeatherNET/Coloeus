import { describe, expect, it } from 'vitest'
import { barHeights } from '~/utils/chart'

describe('barHeights', () => {
  it('gives the largest value the full height', () => {
    expect(barHeights(['1', '4'], 100)).toEqual([25, 100])
  })

  it('gives a zero value no height', () => {
    expect(barHeights(['0', '2'], 100)).toEqual([0, 100])
  })

  it('gives every value zero height when all values are zero', () => {
    expect(barHeights(['0', '0.00'], 100)).toEqual([0, 0])
  })

  it('draws a negative value with its absolute height', () => {
    expect(barHeights(['-2', '4'], 100)).toEqual([50, 100])
  })

  it('compares values with different fraction digits exactly', () => {
    expect(barHeights(['0.00000001', '0.00000002'], 100)).toEqual([50, 100])
  })

  it('treats a value that is not a plain decimal as zero', () => {
    expect(barHeights(['abc', '1'], 100)).toEqual([0, 100])
  })
})
