const SENTENCE_END = /[^。！？；\n]+[。！？；]?/g
const SOFT_END = /[，、,：:]\s*/g
const SPEECH_ARROW = /\s*(?:->|→|⇒|⟶|➜)\s*/g
const CHINESE_NAME_DASH = /([\p{Script=Han}])\s*[-—–－]\s*(?=[\p{Script=Han}])/gu
const ROUTE_LIST_SEPARATOR = /、\s*(?=(?:FJ|[GS])\d+)/giu
const ROUTE_CODE = /\b(FJ|G|S)(\d+)\b/giu
const CITY_PERCENTAGE = /([\p{Script=Han}]{2,}市)\s*[（(]\s*(\d+(?:\.\d+)?)\s*%\s*[）)]/gu
const CITY_LIST_SEPARATOR = /、\s*(?=[\p{Script=Han}]{2,}市(?:[，,（(]))/gu
const PERCENTAGE = /(\d+(?:\.\d+)?)\s*%/g
const FIRST_SEGMENT_MAXIMUM = 40
const FOLLOWING_SEGMENT_MAXIMUM = 45
const SPOKEN_DIGITS: Record<string, string> = {
  '0': '零', '1': '一', '2': '二', '3': '三', '4': '四',
  '5': '五', '6': '六', '7': '七', '8': '八', '9': '九',
}

/** 只调整送入TTS的文本；界面仍保留原始符号。 */
export function normalizeSpeechText(source: string): string {
  return source
    .replace(SPEECH_ARROW, '到')
    .replace(CHINESE_NAME_DASH, '$1到')
    .replace(ROUTE_LIST_SEPARATOR, '；')
    .replace(CITY_PERCENTAGE, (_match, city: string, value: string) => `${city}，占比${speakPercentage(value)}`)
    .replace(CITY_LIST_SEPARATOR, '；')
    .replace(PERCENTAGE, (_match, value: string) => speakPercentage(value))
    .replace(ROUTE_CODE, (_match, prefix: string, digits: string) => {
      const routeType = prefix.toUpperCase() === 'G' ? '国道'
        : prefix.toUpperCase() === 'S' ? '省道' : '福建编号'
      return `${routeType}${[...digits].map((digit) => SPOKEN_DIGITS[digit] ?? digit).join('')}，`
    })
    .replace(/，\s*([，、；。！？])/g, '$1')
}

function speakPercentage(value: string): string {
  const [integer, fraction] = value.split('.')
  if (!fraction) return `百分之${integer}`
  const spokenFraction = [...fraction].map((digit) => SPOKEN_DIGITS[digit] ?? digit).join('')
  return `百分之${integer}点${spokenFraction}`
}

/** 按中文语义边界切分；普通完整回答尽量合成一个音频块，避免句间重复等待TTS。 */
export function speechSummary(source: string): string {
  const text = normalizeSpeechText(source).replace(/\s+/g, ' ').trim()
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

export function streamingSpeechSegments(source: string): string[] {
  const result: string[] = []
  for (const part of splitSpeechText(source)) {
    if (result.length && result[result.length - 1].length + part.length <= 160) result[result.length - 1] += part
    else result.push(part)
  }
  return result
}

export function splitSpeechText(source: string): string[] {
  const text = normalizeSpeechText(source).replace(/\s+/g, ' ').trim()
  if (!text) return []
  const sentences = text.match(SENTENCE_END)?.map((value) => value.trim()).filter(Boolean) ?? [text]
  const segments: string[] = []
  let current = ''

  // 先切成较小语义单元，再将首块控制得更短、后续块适当放大。
  for (const sentence of sentences.flatMap((value) => splitLongSentence(value, FIRST_SEGMENT_MAXIMUM))) {
    const maximum = segments.length === 0 ? FIRST_SEGMENT_MAXIMUM : FOLLOWING_SEGMENT_MAXIMUM
    if (current && (current.endsWith('；') || current.length + sentence.length > maximum)) {
      segments.push(current)
      current = sentence
    } else {
      current += sentence
    }
  }
  if (current) segments.push(current)
  return segments.filter(Boolean)
}

function splitLongSentence(sentence: string, maximum: number): string[] {
  if (sentence.length <= maximum) return [sentence]
  const pieces: string[] = []
  let start = 0
  let match: RegExpExecArray | null
  SOFT_END.lastIndex = 0
  while ((match = SOFT_END.exec(sentence)) !== null) {
    const end = match.index + match[0].length
    if (end - start >= Math.max(30, maximum - 30)) {
      pieces.push(sentence.slice(start, end))
      start = end
    }
  }
  if (start < sentence.length) pieces.push(sentence.slice(start))

  return pieces.flatMap((piece) => {
    if (piece.length <= maximum) return [piece]
    return splitByWordBoundaries(piece, maximum)
  })
}

function splitByWordBoundaries(text: string, maximum: number): string[] {
  const segmenter = new Intl.Segmenter('zh-CN', { granularity: 'word' })
  const result: string[] = []
  let current = ''
  for (const { segment } of segmenter.segment(text)) {
    if (current && current.length + segment.length > maximum) {
      result.push(current)
      current = ''
    }
    // 单个道路名或连续数字宁可略超目标长度，也不从中间硬切。
    current += segment
  }
  if (current) result.push(current)
  return result
}
