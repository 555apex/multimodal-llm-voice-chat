import { describe, expect, it } from 'vitest'
import { resolveDigitalHumanMouthWeights } from './digitalHuman'

describe('resolveDigitalHumanMouthWeights', () => {
  it('interpolates closed to half-open below the midpoint', () => {
    expect(resolveDigitalHumanMouthWeights(0)).toEqual({ closed: 1, half: 0, open: 0 })
    expect(resolveDigitalHumanMouthWeights(0.225)).toEqual({ closed: 0.5, half: 0.5, open: 0 })
    expect(resolveDigitalHumanMouthWeights(0.45)).toEqual({ closed: 0, half: 1, open: 0 })
  })

  it('interpolates half-open to open and clamps invalid input', () => {
    const upper = resolveDigitalHumanMouthWeights(0.725)
    expect(upper.closed).toBe(0)
    expect(upper.half).toBeCloseTo(0.5)
    expect(upper.open).toBeCloseTo(0.5)
    expect(resolveDigitalHumanMouthWeights(1)).toEqual({ closed: 0, half: 0, open: 1 })
    expect(resolveDigitalHumanMouthWeights(-1)).toEqual({ closed: 1, half: 0, open: 0 })
    expect(resolveDigitalHumanMouthWeights(Number.NaN)).toEqual({ closed: 1, half: 0, open: 0 })
  })
})
