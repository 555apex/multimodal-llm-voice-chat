import { describe, expect, it } from 'vitest'
import { normalizeSpeechText, splitSpeechText } from './speechText'

describe('speech text segmentation', () => {
  it('keeps the complete ordinary answer across low-latency blocks', () => {
    const segments = splitSpeechText(
      '思明区当前存在拥堵路段，并伴有局部缓行。重点路段包括成功大道和嘉禾路。建议提前规划路线并预留通行时间。详细数据请查看页面。',
    )

    expect(segments.length).toBeGreaterThan(1)
    expect(segments[0].length).toBeLessThanOrEqual(40)
    expect(segments.slice(1).every((segment) => segment.length <= 45)).toBe(true)
    expect(segments.join('')).toContain('详细数据请查看页面')
  })

  it('returns no empty segments', () => {
    expect(splitSpeechText('   ')).toEqual([])
  })

  it('verbalizes every road number without cutting the spoken identifier', () => {
    const roadNumber = 'G1523甬莞高速'
    const segments = splitSpeechText(`请持续关注 ${'道路通行提醒，'.repeat(10)}${roadNumber.repeat(8)}`)
    const spoken = segments.join('')

    expect(spoken.match(/国道一五二三/g)).toHaveLength(8)
    expect(spoken).not.toContain('G1523')
    expect(segments.every((segment) => !segment.endsWith('国道一五'))).toBe(true)
  })

  it('reads route arrows as 到 without changing route identifiers', () => {
    expect(normalizeSpeechText('FJ014->FJ023、FJ032 → FJ041、FJ059⇒FJ068'))
      .toBe('福建编号零一四，到福建编号零二三；福建编号零三二，到福建编号零四一；福建编号零五九，到福建编号零六八，')
    expect(splitSpeechText('FJ014->FJ023')).toEqual(['福建编号零一四，到福建编号零二三，'])
  })

  it('verbalizes dense route codes and Chinese place-name dashes for stable TTS', () => {
    expect(normalizeSpeechText(
      'S524 惠安东桥-永春东关、G357 东山-泸水、G355 福州-巴马、S507 厦门曾厝垵-华安仙都',
    )).toBe(
      '省道五二四， 惠安东桥到永春东关；国道三五七， 东山到泸水；国道三五五， 福州到巴马；省道五零七， 厦门曾厝垵到华安仙都',
    )
  })

  it('separates city percentages into simple natural-language TTS clauses', () => {
    const source = '漳州市（30.80%）、龙岩市（20.56%）、宁德市（18.53%）'

    expect(normalizeSpeechText(source)).toBe(
      '漳州市，占比百分之30点八零；龙岩市，占比百分之20点五六；宁德市，占比百分之18点五三',
    )
    expect(splitSpeechText(source)).toEqual([
      '漳州市，占比百分之30点八零；',
      '龙岩市，占比百分之20点五六；',
      '宁德市，占比百分之18点五三',
    ])
  })

  it('uses a short first block and larger following blocks', () => {
    const segments = splitSpeechText('第一段说明，'.repeat(80))

    expect(segments.length).toBeGreaterThan(1)
    expect(segments[0].length).toBeLessThanOrEqual(40)
    expect(segments.slice(1).every((segment) => segment.length <= 45)).toBe(true)
  })

  it('keeps the first and following blocks short for a representative traffic answer', () => {
    const segments = splitSpeechText(
      '福建省国省道整体交通态势：已查询到0条路线概况、20条路段明细。具体状态与采集时间见下方数据。'
      + '截至2026年9月14日14时，福建省国省道整体交通态势显示，已查询路段中20条均为轻度拥堵，无畅通、中度拥堵或堵塞情况。'
      + '补充解读暂未完成，已显示的数据仍可查看。',
    )

    expect(segments.length).toBeGreaterThan(1)
    expect(segments[0].length).toBeLessThanOrEqual(40)
    expect(segments.slice(1).every((segment) => segment.length <= 45)).toBe(true)
    expect(segments.join('')).toContain('补充解读暂未完成')
  })

  it('preserves every normalized character across all playback blocks', () => {
    const source = '福州市联系漳州市（30.80%）、龙岩市（20.56%）、宁德市（18.53%）。建议持续关注跨市通道。'
    const normalized = normalizeSpeechText(source).replace(/\s+/g, ' ').trim()

    expect(splitSpeechText(source).join('')).toBe(normalized)
  })
})
