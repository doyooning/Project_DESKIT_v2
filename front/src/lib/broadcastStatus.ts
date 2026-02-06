export type BroadcastStatus = 'READY' | 'ON_AIR' | 'ENDED' | 'STOPPED' | 'RESERVED' | 'CANCELED' | 'VOD'

export const READY_PREVIEW_MS = 3 * 60 * 1000
export const READY_GRACE_MS = 10 * 60 * 1000
export const BROADCAST_DURATION_MS = 30 * 60 * 1000

export const normalizeBroadcastStatus = (status?: string | null): BroadcastStatus => {
  switch ((status ?? '').toUpperCase()) {
    case 'READY':
      return 'READY'
    case 'ON_AIR':
    case 'LIVE':
    case '방송중':
      return 'ON_AIR'
    case 'ENDED':
    case 'END':
    case '종료':
      return 'ENDED'
    case 'STOPPED':
    case '송출중지':
      return 'STOPPED'
    case 'CANCELED':
    case '취소됨':
      return 'CANCELED'
    case 'VOD':
      return 'VOD'
    case 'RESERVED':
    case '예약됨':
    default:
      return 'RESERVED'
  }
}

export const getScheduledEndMs = (startAtMs?: number, endAtMs?: number): number | undefined => {
  if (typeof endAtMs === 'number') return endAtMs
  if (typeof startAtMs === 'number') return startAtMs + BROADCAST_DURATION_MS
  return undefined
}

export const computeLifecycleStatus = (input: {
  status?: string | null
  startAtMs?: number
  endAtMs?: number
  now?: number
}): BroadcastStatus => {
  const now = input.now ?? Date.now()
  const startAtMs = input.startAtMs
  const endAtMs = getScheduledEndMs(startAtMs, input.endAtMs)
  const base = normalizeBroadcastStatus(input.status)

  if (base === 'VOD') return 'VOD'
  if (base === 'CANCELED') return 'CANCELED'
  if (base === 'STOPPED') return 'STOPPED'
  if (base === 'ENDED') return 'ENDED'
  if (base === 'ON_AIR') {
    if (endAtMs && now > endAtMs) return 'ENDED'
    return 'ON_AIR'
  }
  if (base === 'READY') {
    if (startAtMs && now >= startAtMs + READY_GRACE_MS) return 'CANCELED'
    return 'READY'
  }
  if (!startAtMs) return base
  if (now >= startAtMs + READY_GRACE_MS) return 'CANCELED'
  if (now >= startAtMs - READY_PREVIEW_MS) return 'READY'
  return 'RESERVED'
}

export const hasReachedStartTime = (startAtMs?: number, now: number = Date.now()): boolean => {
  if (!startAtMs) return false
  return now >= startAtMs
}

export const getBroadcastStatusLabel = (status?: BroadcastStatus | string | null): string => {
  const normalized = normalizeBroadcastStatus(status)
  switch (normalized) {
    case 'READY':
      return '준비 중'
    case 'ON_AIR':
      return 'ON AIR'
    case 'ENDED':
      return '종료됨'
    case 'STOPPED':
      return '중지됨'
    case 'CANCELED':
      return '취소됨'
    case 'VOD':
      return 'VOD'
    case 'RESERVED':
    default:
      return '예약됨'
  }
}
