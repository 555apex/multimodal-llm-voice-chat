const SENTENCE_END = /[^。！？；：\n]+[。！？；：]?/g
const SOFT_END = /[，、,]\s*/g
const PROTECTED_TOKEN = /[A-Za-z]{1,4}\d{1,5}(?:[+-]\d+(?:\.\d+)?)?|\d{1,2}:\d{2}(?::\d{2})?|[+-]?\d+(?:\.\d+)?\s*(?:%|mm|cm|km|m|公里|米|毫米|厘米|小时|分钟|秒)/gi
const BUSINESS_WORDS = /城市对|跨市路线|联系倾向|国省干线|通行能力|交通运行|城市间联系/g

export function speechSummary(source: string): string {
  const text = source.replace(/\s+/g, ' ').trim()
  if (text.length <= 160) return text
  const sentences = text.match(SENTENCE_END) ?? []
  let result = ''
  for (const sentence of sentences) {
    if (result.length + sentence.length > 160) break
    result += sentence
    if (result.length >= 120) break
  }
  return result || '查询结果已显示，请查看详细数据，或点击全文朗读。'
}

/** MP3 fallback segmentation. Streaming TTS receives the complete text once. */
export function splitSpeechText(source: string): string[] {
  const text = source.replace(/\s+/g, ' ').trim()
  if (!text) return []
  const sentences = text.match(SENTENCE_END)?.map(value => value.trim()).filter(Boolean) ?? [text]
  const segments: string[] = []
  let current = ''
  for (const sentence of sentences) {
    const initialMaximum = segments.length === 0 && !current ? 48 : 90
    for (const piece of splitLongSentence(sentence, initialMaximum)) {
      const maximum = segments.length === 0 ? 48 : 90
      if (current && current.length + piece.length > maximum) {
        segments.push(current)
        current = piece
      } else {
        current += piece
      }
    }
  }
  if (current) segments.push(current)
  return segments
}

function splitLongSentence(sentence: string, maximum: number): string[] {
  const result: string[] = []
  let remaining = sentence
  while (remaining.length > maximum) {
    const protectedSpans = [...remaining.matchAll(PROTECTED_TOKEN)].map(match => [match.index!, match.index! + match[0].length])
    protectedSpans.push(...[...remaining.matchAll(BUSINESS_WORDS)].map(match => [match.index!, match.index! + match[0].length]))
    const boundaries = [...remaining.slice(0, maximum + 1).matchAll(SOFT_END)]
      .map(match => match.index! + match[0].length)
      .filter(position => position >= 30 && !protectedSpans.some(([start, end]) => start < position && position < end))
    let cut = boundaries.at(-1) ?? safeCut(maximum, protectedSpans)
    if (cut <= 0) cut = maximum
    result.push(remaining.slice(0, cut).trim())
    remaining = remaining.slice(cut).trim()
  }
  if (remaining) result.push(remaining)
  return result
}

function safeCut(preferred: number, spans: number[][]) {
  const protectedSpan = spans.find(([start, end]) => start < preferred && preferred < end)
  if (!protectedSpan) return preferred
  return protectedSpan[0] >= 16 ? protectedSpan[0] : protectedSpan[1]
}
