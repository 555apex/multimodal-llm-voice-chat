export type DigitalHumanMode =
  | 'idle'
  | 'listening'
  | 'thinking'
  | 'answering'
  | 'speaking'
  | 'error'

export type DigitalHumanPose = 'idle' | 'thinking' | 'explaining'

export type DigitalHumanMouthShape = 'closed' | 'half' | 'open'

export interface DigitalHumanSignal {
  mode: DigitalHumanMode
  statusLabel: string
  speechLevel: number
}

export interface DigitalHumanStateMeta {
  label: string
  detail: string
  pose: DigitalHumanPose
  poseLabel: string
}

export interface DigitalHumanPoseAsset {
  webp: string
  png: string
}

export interface DigitalHumanMouthWeights {
  closed: number
  half: number
  open: number
}

export const digitalHumanStateMeta: Record<DigitalHumanMode, DigitalHumanStateMeta> = {
  idle: {
    label: '在线待命',
    detail: '保持轻呼吸，等待新的交通业务请求。',
    pose: 'idle',
    poseLabel: '待命姿态',
  },
  listening: {
    label: '正在聆听',
    detail: '正在接收您的语音输入。',
    pose: 'idle',
    poseLabel: '待命前倾姿态',
  },
  thinking: {
    label: '正在研判',
    detail: '正在连接 Agent、调用工具或生成交通研判。',
    pose: 'thinking',
    poseLabel: '思考姿态',
  },
  answering: {
    label: '正在讲解',
    detail: 'Agent 正在以文字流式输出结果。',
    pose: 'explaining',
    poseLabel: '讲解姿态',
  },
  speaking: {
    label: '正在语音汇报',
    detail: '本地 TTS 音频正在实际播放。',
    pose: 'explaining',
    poseLabel: '语音汇报姿态',
  },
  error: {
    label: '处理异常',
    detail: '本次 Agent 请求失败，稍后将自动恢复待命。',
    pose: 'idle',
    poseLabel: '待命姿态',
  },
}

export const digitalHumanPoseAssets: Record<DigitalHumanPose, DigitalHumanPoseAsset> = {
  idle: {
    webp: '/digital-human/guardian/guardian-idle.webp',
    png: '/digital-human/guardian/guardian-idle.png',
  },
  thinking: {
    webp: '/digital-human/guardian/guardian-thinking.webp',
    png: '/digital-human/guardian/guardian-thinking.png',
  },
  explaining: {
    webp: '/digital-human/guardian/guardian-explaining.webp',
    png: '/digital-human/guardian/guardian-explaining.png',
  },
}

export const digitalHumanMouthAssets: Record<DigitalHumanMouthShape, DigitalHumanPoseAsset> = {
  closed: {
    webp: '/digital-human/guardian/guardian-explaining-mouth-closed-v2.webp',
    png: '/digital-human/guardian/guardian-explaining-mouth-closed-v2.png',
  },
  half: {
    webp: '/digital-human/guardian/guardian-explaining-mouth-half-v2.webp',
    png: '/digital-human/guardian/guardian-explaining-mouth-half-v2.png',
  },
  open: {
    webp: '/digital-human/guardian/guardian-explaining-mouth-open-v2.webp',
    png: '/digital-human/guardian/guardian-explaining-mouth-open-v2.png',
  },
}

export function resolveDigitalHumanMouthWeights(level: number): DigitalHumanMouthWeights {
  const normalized = Math.min(1, Math.max(0, Number.isFinite(level) ? level : 0))
  if (normalized <= 0.45) {
    const half = normalized / 0.45
    return { closed: 1 - half, half, open: 0 }
  }
  const open = (normalized - 0.45) / 0.55
  return { closed: 0, half: 1 - open, open }
}
