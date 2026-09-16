import { describe, expect, it } from 'vitest'
import { speechSummary, splitSpeechText } from './speechText'

describe('speech text segmentation', () => {
  it('keeps Chinese semantic boundaries', () => {
    const segments = splitSpeechText(
      '思明区当前存在拥堵路段，并伴有局部缓行。重点路段包括成功大道和嘉禾路。建议提前规划路线并预留通行时间。详细数据请查看页面。',
    )

    expect(segments.length).toBeGreaterThan(1)
    expect(segments[0]).toMatch(/[。！？；]$/)
    expect(segments[0].length).toBeLessThanOrEqual(48)
    expect(segments.join('')).toContain('详细数据请查看页面')
  })

  it('keeps values, route markers and times intact', () => {
    const source = `现场数据较多，${'请持续关注道路运行状态，'.repeat(8)}G205公路K123+456的监测值为31.495 mm，利用率80%，时间09:30。`
    const segments = splitSpeechText(source)
    expect(segments.every(segment => segment.length <= 90)).toBe(true)
    for (const token of ['G205', 'K123+456', '31.495 mm', '80%', '09:30']) {
      expect(segments.some(segment => segment.includes(token))).toBe(true)
    }
  })

  it('creates a complete-sentence summary', () => {
    const source = '第一句用于说明当前状态。'.repeat(20)
    expect(speechSummary(source).length).toBeLessThanOrEqual(160)
    expect(speechSummary(source)).toMatch(/。$/)
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
