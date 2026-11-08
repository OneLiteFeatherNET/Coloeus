// Geometry of the inline SVG charts. The money values stay strings; they are compared as scaled integers
// (BigInt), and only the resulting ratio becomes a number, because it is a pixel height and nothing else.

import { fractionLength, isPlainDecimal } from './money'

const SCALE = 1_000_000n

function scaled(value: string, digits: number): bigint {
  const negative = value.startsWith('-')
  const unsigned = negative ? value.slice(1) : value
  const dot = unsigned.indexOf('.')
  const integer = dot < 0 ? unsigned : unsigned.slice(0, dot)
  const fraction = (dot < 0 ? '' : unsigned.slice(dot + 1)).padEnd(digits, '0')
  const result = BigInt(integer + fraction)
  return negative ? -result : result
}

/**
 * Heights of bars in the range 0 to `maxHeight` for plain decimal values. The largest absolute value
 * gets the full height; a value of zero gets zero. Negative values are drawn as their absolute height.
 */
export function barHeights(values: string[], maxHeight: number): number[] {
  const valid = values.filter(isPlainDecimal)
  const digits = Math.max(0, ...valid.map(fractionLength))
  const magnitudes = values.map((value) => {
    if (!isPlainDecimal(value)) {
      return 0n
    }
    const current = scaled(value, digits)
    return current < 0n ? -current : current
  })
  const max = magnitudes.reduce((a, b) => (b > a ? b : a), 0n)
  if (max === 0n) {
    return values.map(() => 0)
  }
  return magnitudes.map(m => Number((m * SCALE) / max) / Number(SCALE) * maxHeight)
}
