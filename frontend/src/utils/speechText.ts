const SENTENCE_END = /[^。！？；\n]+[。！？；]?/g
const SOFT_END = /[，、,：:]\s*/g

/** 按中文语义边界切分；首段较短以降低首音频延迟，后续段较长以减少停顿。 */
export function splitSpeechText(source: string): string[] {
  const text = source.replace(/\s+/g, ' ').trim()
  if (!text) return []
  const sentences = text.match(SENTENCE_END)?.map((value) => value.trim()).filter(Boolean) ?? [text]
  const segments: string[] = []
  let current = ''

  for (const sentence of sentences.flatMap((value) => splitLongSentence(value, segments.length ? 140 : 60))) {
    const maximum = segments.length === 0 ? 60 : 140
    if (current && current.length + sentence.length > maximum) {
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
