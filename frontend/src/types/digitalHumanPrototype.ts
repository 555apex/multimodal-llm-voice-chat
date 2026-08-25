export type PrototypeDigitalHumanState = 'idle' | 'listening' | 'speaking' | 'error'
export type PrototypeDigitalHumanPose = 'idle' | 'thinking' | 'explaining'

export interface PrototypeStateMeta {
  label: string
  detail: string
  pose: PrototypeDigitalHumanPose
  poseLabel: string
}

export const prototypeStateMeta: Record<PrototypeDigitalHumanState, PrototypeStateMeta> = {
  idle: {
    label: '在线待命',
    detail: '保持轻呼吸，等待新的交通业务请求。',
    pose: 'idle',
    poseLabel: '待命姿态',
  },
  listening: {
    label: '正在聆听 / 处理中',
    detail: '原型阶段沿用 listening，同时表达研判与处理。',
    pose: 'thinking',
    poseLabel: '思考姿态',
  },
  speaking: {
    label: '正在讲解',
    detail: '使用讲解姿态和声波反馈，不进行口型同步。',
    pose: 'explaining',
    poseLabel: '讲解姿态',
  },
  error: {
    label: '处理异常',
    detail: '回退待命姿态，通过错误色和文字说明状态。',
    pose: 'idle',
    poseLabel: '待命姿态',
  },
}

export const prototypePoseAssets: Record<PrototypeDigitalHumanPose, string> = {
  idle: '/digital-human/figure-a/idle.png',
  thinking: '/digital-human/figure-a/thinking.png',
  explaining: '/digital-human/figure-a/explaining.png',
}
