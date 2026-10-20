export type ScreenAction = 'OPEN_COMMAND' | 'OPEN_MONITOR' | 'SET_MAP_RESOURCES'
  | 'SEARCH_NEARBY_RESOURCES' | 'OPEN_MATERIALS'

export interface ScreenActionIntent {
  action: ScreenAction
  params: Record<string, unknown>
}

export interface ScreenActionRequest extends ScreenActionIntent {
  requestId: string
}

export interface ScreenActionReceipt {
  requestId: string
  status: 'accepted' | 'completed' | 'rejected' | 'failed'
  message: string
  data: Record<string, unknown> | null
}

export type ScreenActionHandler = (request: ScreenActionRequest) =>
  ScreenActionReceipt | Promise<ScreenActionReceipt>

const resourceCodes = new Set([
  'warningmarker', 'route', 'bridge', 'tunnel', 'slope', 'servicearea',
  'video', 'board', 'traffic', 'organization', 'warehouse', 'team',
])
const statuses = new Set(['accepted', 'completed', 'rejected', 'failed'])
let handler: ScreenActionHandler | undefined

export function setScreenActionHandler(value?: ScreenActionHandler) {
  if (value !== undefined && typeof value !== 'function') throw new Error('onActionRequest 必须是函数')
  handler = value
}

function plainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function exactKeys(value: Record<string, unknown>, keys: string[]) {
  return Object.keys(value).every((key) => keys.includes(key))
}

export function validScreenActionIntent(value: unknown): value is ScreenActionIntent {
  if (!plainObject(value) || !exactKeys(value, ['action', 'params']) || !plainObject(value.params)) return false
  const params = value.params
  switch (value.action) {
    case 'OPEN_COMMAND':
    case 'OPEN_MATERIALS':
      return Object.keys(params).length === 0
    case 'OPEN_MONITOR':
      return exactKeys(params, ['name']) && typeof params.name === 'string'
        && params.name.trim().length > 0
    case 'SET_MAP_RESOURCES': {
      if (!exactKeys(params, ['items']) || !Array.isArray(params.items)
        || params.items.length === 0 || params.items.length > 12) return false
      const seen = new Set<string>()
      for (const item of params.items) {
        if (!plainObject(item) || !exactKeys(item, ['resource', 'enabled'])
          || typeof item.resource !== 'string' || !resourceCodes.has(item.resource)
          || typeof item.enabled !== 'boolean' || seen.has(item.resource)) return false
        seen.add(item.resource)
      }
      return true
    }
    case 'SEARCH_NEARBY_RESOURCES':
      return exactKeys(params, ['lng', 'lat', 'locationAddress'])
        && typeof params.lng === 'number' && Number.isFinite(params.lng)
        && params.lng >= -180 && params.lng <= 180
        && typeof params.lat === 'number' && Number.isFinite(params.lat)
        && params.lat >= -90 && params.lat <= 90
        && (params.locationAddress === undefined || typeof params.locationAddress === 'string')
    default:
      return false
  }
}

function result(requestId: string, message: string): ScreenActionReceipt {
  return { requestId, status: 'failed', message, data: null }
}

export async function submitScreenAction(intent: unknown): Promise<ScreenActionReceipt> {
  const requestId = crypto.randomUUID()
  if (!validScreenActionIntent(intent)) return result(requestId, '大屏操作参数不符合协议，未执行。')
  if (!handler) return result(requestId, '此操作需要在已接入的大屏页面执行。')
  const request: ScreenActionRequest = { requestId, action: intent.action, params: intent.params }
  let timeoutId: ReturnType<typeof setTimeout> | undefined
  try {
    const receipt = await Promise.race([
      Promise.resolve().then(() => handler?.(request)),
      new Promise<undefined>((resolve) => {
        timeoutId = setTimeout(() => resolve(undefined), 30000)
      }),
    ])
    if (!plainObject(receipt) || receipt.requestId !== requestId
      || typeof receipt.status !== 'string' || !statuses.has(receipt.status)
      || typeof receipt.message !== 'string' || !Object.hasOwn(receipt, 'data')
      || (receipt.data !== null && !plainObject(receipt.data))) {
      return result(requestId, '大屏操作结果暂未确认。')
    }
    return receipt as unknown as ScreenActionReceipt
  } catch {
    return result(requestId, '大屏操作暂时无法执行。')
  } finally {
    if (timeoutId !== undefined) clearTimeout(timeoutId)
  }
}
