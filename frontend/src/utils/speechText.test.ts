import { describe, expect, it } from 'vitest'
import { splitSpeechText } from './speechText'

describe('speech text segmentation', () => {
  it('keeps Chinese semantic boundaries and makes the first segment shorter', () => {
    const segments = splitSpeechText(
      '思明区当前存在拥堵路段，并伴有局部缓行。重点路段包括成功大道和嘉禾路。建议提前规划路线并预留通行时间。详细数据请查看页面。',
    )

    expect(segments.length).toBeGreaterThan(1)
    expect(segments[0]).toMatch(/[。！？；]$/)
    expect(segments[0].length).toBeLessThanOrEqual(60)
    expect(segments.join('')).toContain('详细数据请查看页面')
  })

  it('returns no empty segments', () => {
    expect(splitSpeechText('   ')).toEqual([])
  })

  it('does not split a continuous road number token', () => {
    const roadNumber = 'G1523甬莞高速'
    const segments = splitSpeechText(`请持续关注 ${'道路通行提醒，'.repeat(10)}${roadNumber.repeat(8)}`)

    expect(segments.join('')).toContain(roadNumber.repeat(8))
    expect(segments.every((segment) => !segment.endsWith('G15'))).toBe(true)
  })
})
